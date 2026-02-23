package com.codewiki.service;

import com.codewiki.client.LLMClient;
import com.codewiki.dto.ChatResponse;
import com.codewiki.model.*;
import com.codewiki.repository.ChatMessageRepository;
import com.codewiki.repository.FileExplanationRepository;
import com.codewiki.repository.WikiRepository;
import com.codewiki.repository.WikiSectionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Implementation of ChatService with RAG (Retrieval-Augmented Generation).
 * Provides intelligent question answering by retrieving relevant context from wiki content.
 */
@Service
public class ChatServiceImpl implements ChatService {
    
    private static final Logger logger = LoggerFactory.getLogger(ChatServiceImpl.class);
    
    private static final int MAX_CONTEXT_TOKENS = 6000;
    private static final int TOP_SECTIONS_COUNT = 3;
    private static final int CONVERSATION_HISTORY_TURNS = 3;
    private static final int APPROX_CHARS_PER_TOKEN = 4;
    
    private final WikiRepository wikiRepository;
    private final WikiSectionRepository wikiSectionRepository;
    private final FileExplanationRepository fileExplanationRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final LLMClient llmClient;
    
    public ChatServiceImpl(
            WikiRepository wikiRepository,
            WikiSectionRepository wikiSectionRepository,
            FileExplanationRepository fileExplanationRepository,
            ChatMessageRepository chatMessageRepository,
            LLMClient llmClient) {
        this.wikiRepository = wikiRepository;
        this.wikiSectionRepository = wikiSectionRepository;
        this.fileExplanationRepository = fileExplanationRepository;
        this.chatMessageRepository = chatMessageRepository;
        this.llmClient = llmClient;
    }
    
    @Override
    @Transactional
    public ChatResponse askQuestion(String wikiId, String question) {
        logger.info("Processing question for wiki {}: {}", wikiId, question);
        
        // Validate wiki exists
        Wiki wiki = wikiRepository.findById(wikiId)
                .orElseThrow(() -> new IllegalArgumentException("Wiki not found: " + wikiId));
        
        // Retrieve conversation history
        List<ChatMessage> history = getRecentHistory(wikiId, CONVERSATION_HISTORY_TURNS * 2);
        
        // Retrieve relevant context
        List<WikiSection> relevantSections = retrieveRelevantSections(wikiId, question);
        String context = buildContext(wiki, relevantSections, question);
        
        // Generate response
        String answer = generateResponse(wikiId, question, context, history);
        
        // Inject hyperlinks
        String answerWithLinks = injectHyperlinks(answer, wikiId, wiki.getSections());
        
        // Save conversation
        saveMessage(wiki, MessageRole.USER, question);
        saveMessage(wiki, MessageRole.ASSISTANT, answerWithLinks);
        
        // Extract references
        List<String> references = relevantSections.stream()
                .map(WikiSection::getTitle)
                .collect(Collectors.toList());
        
        return new ChatResponse(answerWithLinks, references);
    }
    
    @Override
    public List<WikiSection> retrieveRelevantSections(String wikiId, String question) {
        logger.debug("Retrieving relevant sections for question: {}", question);
        
        // Extract keywords from question
        Set<String> keywords = extractKeywords(question);
        logger.debug("Extracted keywords: {}", keywords);
        
        // Get all sections for the wiki
        Wiki wiki = wikiRepository.findById(wikiId)
                .orElseThrow(() -> new IllegalArgumentException("Wiki not found: " + wikiId));
        
        List<WikiSection> allSections = wiki.getSections();
        
        // Score and rank sections by relevance
        List<ScoredSection> scoredSections = allSections.stream()
                .map(section -> new ScoredSection(section, calculateRelevanceScore(section, keywords)))
                .filter(scored -> scored.score > 0)
                .sorted(Comparator.comparingDouble(ScoredSection::score).reversed())
                .toList();
        
        // Return top N sections
        return scoredSections.stream()
                .limit(TOP_SECTIONS_COUNT)
                .map(ScoredSection::section)
                .collect(Collectors.toList());
    }
    
    @Override
    public String generateResponse(String wikiId, String question, String context, List<ChatMessage> history) {
        logger.debug("Generating response with context length: {} chars", context.length());
        
        // Build prompt with system instructions, context, history, and question
        String prompt = buildRAGPrompt(question, context, history);
        
        // Generate response using LLM with retry
        String response = llmClient.generateWithRetry(prompt, 3);
        
        return response;
    }
    
    @Override
    public List<ChatMessage> getConversationHistory(String wikiId) {
        return chatMessageRepository.findByWikiIdOrderByCreatedAt(wikiId);
    }
    
    // Helper methods
    
    private List<ChatMessage> getRecentHistory(String wikiId, int maxMessages) {
        List<ChatMessage> allHistory = chatMessageRepository.findByWikiIdOrderByCreatedAt(wikiId);
        
        // Return last N messages
        int startIndex = Math.max(0, allHistory.size() - maxMessages);
        return allHistory.subList(startIndex, allHistory.size());
    }
    
    private Set<String> extractKeywords(String question) {
        // Simple keyword extraction: lowercase, remove common words, split on whitespace
        Set<String> stopWords = Set.of(
                "a", "an", "the", "is", "are", "was", "were", "be", "been", "being",
                "have", "has", "had", "do", "does", "did", "will", "would", "should",
                "could", "can", "may", "might", "must", "what", "when", "where", "who",
                "which", "why", "how", "this", "that", "these", "those", "i", "you",
                "he", "she", "it", "we", "they", "me", "him", "her", "us", "them",
                "my", "your", "his", "its", "our", "their", "in", "on", "at", "to",
                "for", "of", "with", "from", "by", "about", "as", "into", "through",
                "during", "before", "after", "above", "below", "between", "under"
        );
        
        return Arrays.stream(question.toLowerCase().split("\\W+"))
                .filter(word -> word.length() > 2)
                .filter(word -> !stopWords.contains(word))
                .collect(Collectors.toSet());
    }
    
    private double calculateRelevanceScore(WikiSection section, Set<String> keywords) {
        String content = (section.getTitle() + " " + section.getContent()).toLowerCase();
        
        double score = 0.0;
        for (String keyword : keywords) {
            // Count occurrences of keyword
            int count = countOccurrences(content, keyword);
            
            // Title matches get higher weight
            if (section.getTitle().toLowerCase().contains(keyword)) {
                score += 3.0;
            }
            
            // Content matches
            score += count * 1.0;
        }
        
        return score;
    }
    
    private int countOccurrences(String text, String keyword) {
        int count = 0;
        int index = 0;
        while ((index = text.indexOf(keyword, index)) != -1) {
            count++;
            index += keyword.length();
        }
        return count;
    }
    
    private String buildContext(Wiki wiki, List<WikiSection> sections, String question) {
        StringBuilder context = new StringBuilder();
        int currentTokens = 0;
        int maxChars = MAX_CONTEXT_TOKENS * APPROX_CHARS_PER_TOKEN;
        
        // Add wiki overview
        context.append("Repository: ").append(wiki.getRepositoryName()).append("\n\n");
        
        // Add relevant sections
        for (WikiSection section : sections) {
            String sectionText = "## " + section.getTitle() + "\n" + section.getContent() + "\n\n";
            
            if (context.length() + sectionText.length() > maxChars) {
                break;
            }
            
            context.append(sectionText);
        }
        
        // Check if question mentions specific files
        List<FileExplanation> relevantFiles = findRelevantFiles(wiki, question);
        for (FileExplanation file : relevantFiles) {
            String fileText = "### File: " + file.getFilePath() + "\n" +
                    "Language: " + file.getLanguage() + "\n" +
                    file.getExplanation() + "\n\n";
            
            if (context.length() + fileText.length() > maxChars) {
                break;
            }
            
            context.append(fileText);
        }
        
        return context.toString();
    }
    
    private List<FileExplanation> findRelevantFiles(Wiki wiki, String question) {
        String questionLower = question.toLowerCase();
        
        return wiki.getFileExplanations().stream()
                .filter(file -> {
                    String fileName = file.getFilePath().toLowerCase();
                    // Check if file name or path is mentioned in question
                    return questionLower.contains(fileName) ||
                           fileName.contains(questionLower.replaceAll("\\s+", ""));
                })
                .limit(2) // Limit to 2 files to preserve token budget
                .collect(Collectors.toList());
    }
    
    private String buildRAGPrompt(String question, String context, List<ChatMessage> history) {
        StringBuilder prompt = new StringBuilder();
        
        // System instructions
        prompt.append("You are a helpful AI assistant that answers questions about code repositories. ");
        prompt.append("Use the provided wiki sections and code files to answer the user's question. ");
        prompt.append("When referencing information from specific sections, mention the section name. ");
        prompt.append("Be concise and accurate.\n\n");
        
        // Context
        prompt.append("=== Repository Documentation ===\n");
        prompt.append(context);
        prompt.append("\n=== End of Documentation ===\n\n");
        
        // Conversation history
        if (!history.isEmpty()) {
            prompt.append("=== Conversation History ===\n");
            for (ChatMessage msg : history) {
                prompt.append(msg.getRole()).append(": ").append(msg.getContent()).append("\n");
            }
            prompt.append("=== End of History ===\n\n");
        }
        
        // Current question
        prompt.append("User Question: ").append(question).append("\n\n");
        prompt.append("Assistant Answer:");
        
        return prompt.toString();
    }
    
    private String injectHyperlinks(String answer, String wikiId, List<WikiSection> allSections) {
        String result = answer;
        
        // Pattern to match section references: "in the [section name]", "as explained in [section]", etc.
        Pattern pattern = Pattern.compile(
                "(?:in the|as explained in|see the|refer to the|check the|described in the)\\s+([A-Z][\\w\\s]+?)(?:\\s+section)?(?=[\\s.,;!?]|$)",
                Pattern.CASE_INSENSITIVE
        );
        
        Matcher matcher = pattern.matcher(answer);
        StringBuffer sb = new StringBuffer();
        
        while (matcher.find()) {
            String sectionReference = matcher.group(1).trim();
            
            // Find matching section
            Optional<WikiSection> matchingSection = allSections.stream()
                    .filter(section -> section.getTitle().equalsIgnoreCase(sectionReference) ||
                                     section.getTitle().toLowerCase().contains(sectionReference.toLowerCase()))
                    .findFirst();
            
            if (matchingSection.isPresent()) {
                WikiSection section = matchingSection.get();
                String link = String.format("[%s](/wiki/%s/section/%s)", 
                        section.getTitle(), wikiId, section.getId());
                
                // Replace the section reference with hyperlink
                String replacement = matcher.group(0).replace(sectionReference, link);
                matcher.appendReplacement(sb, Matcher.quoteReplacement(replacement));
            }
        }
        
        matcher.appendTail(sb);
        return sb.toString();
    }
    
    private void saveMessage(Wiki wiki, MessageRole role, String content) {
        ChatMessage message = new ChatMessage();
        message.setWiki(wiki);
        message.setRole(role);
        message.setContent(content);
        chatMessageRepository.save(message);
    }
    
    // Helper class for scoring sections
    private record ScoredSection(WikiSection section, double score) {}
}
