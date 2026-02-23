package com.codewiki.service;

import com.codewiki.client.LLMClient;
import com.codewiki.dto.ChatResponse;
import com.codewiki.model.*;
import com.codewiki.repository.ChatMessageRepository;
import com.codewiki.repository.FileExplanationRepository;
import com.codewiki.repository.WikiRepository;
import com.codewiki.repository.WikiSectionRepository;
import net.jqwik.api.*;
import net.jqwik.api.constraints.IntRange;
import net.jqwik.api.constraints.NotEmpty;
import net.jqwik.api.constraints.Size;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;

import java.util.*;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * Property-based tests for ChatService.
 * Tests universal properties that should hold for all valid inputs.
 */
@SpringBootTest
@ActiveProfiles("test")
class ChatServicePropertyTest {
    
    @Autowired
    private WikiRepository wikiRepository;
    
    @Autowired
    private WikiSectionRepository wikiSectionRepository;
    
    @Autowired
    private FileExplanationRepository fileExplanationRepository;
    
    @Autowired
    private ChatMessageRepository chatMessageRepository;
    
    @MockBean
    private LLMClient llmClient;
    
    @Autowired
    private ChatService chatService;
    
    @BeforeEach
    void setUp() {
        // Clean up database before each test
        chatMessageRepository.deleteAll();
        fileExplanationRepository.deleteAll();
        wikiSectionRepository.deleteAll();
        wikiRepository.deleteAll();
    }
    
    /**
     * Feature: codewiki-generator, Property 22: Chatbot RAG Context
     * 
     * **Validates: Requirements 10.2**
     * 
     * For any question submitted to the Chatbot, the response generation should use
     * both Wiki_Content and repository files as context (RAG implementation).
     */
    @Property(tries = 100)
    void chatbotUsesRAGContext(
            @ForAll @NotEmpty String question,
            @ForAll @Size(min = 1, max = 5) List<@NotEmpty String> sectionTitles,
            @ForAll @Size(min = 1, max = 3) List<@NotEmpty String> filePaths) {
        
        // Given: A wiki with sections and file explanations
        Wiki wiki = createTestWiki("https://github.com/test/repo");
        
        for (int i = 0; i < sectionTitles.size(); i++) {
            WikiSection section = new WikiSection();
            section.setWiki(wiki);
            section.setSectionType(SectionType.OVERVIEW);
            section.setTitle(sectionTitles.get(i));
            section.setContent("Content for " + sectionTitles.get(i));
            section.setOrderIndex(i);
            wiki.getSections().add(section);
        }
        
        for (String filePath : filePaths) {
            FileExplanation file = new FileExplanation();
            file.setWiki(wiki);
            file.setFilePath(filePath);
            file.setLanguage("Java");
            file.setExplanation("Explanation for " + filePath);
            wiki.getFileExplanations().add(file);
        }
        
        wikiRepository.save(wiki);
        
        // Mock LLM to return a response that we can verify
        when(llmClient.generateWithRetry(anyString(), anyInt()))
                .thenAnswer(invocation -> {
                    String prompt = invocation.getArgument(0);
                    // Verify prompt contains context from wiki sections
                    assertThat(prompt).contains("Repository Documentation");
                    return "This is a test answer based on the provided context.";
                });
        
        // When: User asks a question
        ChatResponse response = chatService.askQuestion(wiki.getId(), question);
        
        // Then: Response should be generated using RAG context
        assertThat(response).isNotNull();
        assertThat(response.getAnswer()).isNotEmpty();
        
        // Verify conversation was saved
        List<ChatMessage> history = chatService.getConversationHistory(wiki.getId());
        assertThat(history).hasSize(2); // User question + assistant answer
        assertThat(history.get(0).getRole()).isEqualTo(MessageRole.USER);
        assertThat(history.get(0).getContent()).isEqualTo(question);
        assertThat(history.get(1).getRole()).isEqualTo(MessageRole.ASSISTANT);
    }
    
    /**
     * Feature: codewiki-generator, Property 23: Chatbot Hyperlink Injection
     * 
     * **Validates: Requirements 10.3**
     * 
     * For any Chatbot response that references wiki sections, the response should
     * include hyperlinks to those sections in the format
     * `[section name](/wiki/{wikiId}/section/{sectionId})`.
     */
    @Property(tries = 100)
    void chatbotInjectsHyperlinks(
            @ForAll @NotEmpty String sectionTitle,
            @ForAll @NotEmpty String question) {
        
        // Given: A wiki with a specific section
        Wiki wiki = createTestWiki("https://github.com/test/hyperlink");
        
        WikiSection section = new WikiSection();
        section.setWiki(wiki);
        section.setSectionType(SectionType.ARCHITECTURE);
        section.setTitle(sectionTitle);
        section.setContent("Architecture details");
        section.setOrderIndex(0);
        wiki.getSections().add(section);
        
        wikiRepository.save(wiki);
        
        // Mock LLM to return a response that references the section
        when(llmClient.generateWithRetry(anyString(), anyInt()))
                .thenReturn("As explained in the " + sectionTitle + ", the system works this way.");
        
        // When: User asks a question
        ChatResponse response = chatService.askQuestion(wiki.getId(), question);
        
        // Then: Response should contain hyperlink to the section
        assertThat(response.getAnswer()).contains("/wiki/" + wiki.getId() + "/section/" + section.getId());
        assertThat(response.getAnswer()).contains("[" + sectionTitle + "]");
    }
    
    /**
     * Feature: codewiki-generator, Property 24: Chatbot Hyperlink Navigation
     * 
     * **Validates: Requirements 10.4**
     * 
     * For any hyperlink in a chatbot response, clicking it should navigate to the
     * referenced Wiki_Section (verified by checking link format).
     */
    @Property(tries = 100)
    void chatbotHyperlinksAreValid(
            @ForAll @NotEmpty String sectionTitle,
            @ForAll @NotEmpty String question) {
        
        // Given: A wiki with sections
        Wiki wiki = createTestWiki("https://github.com/test/navigation");
        
        WikiSection section = new WikiSection();
        section.setWiki(wiki);
        section.setSectionType(SectionType.OVERVIEW);
        section.setTitle(sectionTitle);
        section.setContent("Overview content");
        section.setOrderIndex(0);
        wiki.getSections().add(section);
        
        wikiRepository.save(wiki);
        
        // Mock LLM to reference the section
        when(llmClient.generateWithRetry(anyString(), anyInt()))
                .thenReturn("See the " + sectionTitle + " for more details.");
        
        // When: User asks a question
        ChatResponse response = chatService.askQuestion(wiki.getId(), question);
        
        // Then: Hyperlinks should follow the correct format
        if (response.getAnswer().contains("/wiki/")) {
            // Extract the link and verify it points to a valid section
            assertThat(response.getAnswer()).matches(".*\\[.*\\]\\(/wiki/" + wiki.getId() + "/section/[a-f0-9-]+\\).*");
        }
    }
    
    /**
     * Feature: codewiki-generator, Property 25: Chatbot Conversation Context
     * 
     * **Validates: Requirements 10.5**
     * 
     * For any follow-up question in a conversation, the Chatbot should have access
     * to previous messages in the conversation history (at least the last 3 turns).
     */
    @Property(tries = 100)
    void chatbotMaintainsConversationContext(
            @ForAll @Size(min = 2, max = 5) List<@NotEmpty String> questions) {
        
        // Given: A wiki
        Wiki wiki = createTestWiki("https://github.com/test/context");
        
        WikiSection section = new WikiSection();
        section.setWiki(wiki);
        section.setSectionType(SectionType.OVERVIEW);
        section.setTitle("Overview");
        section.setContent("System overview");
        section.setOrderIndex(0);
        wiki.getSections().add(section);
        
        wikiRepository.save(wiki);
        
        // Mock LLM to return responses
        when(llmClient.generateWithRetry(anyString(), anyInt()))
                .thenAnswer(invocation -> {
                    String prompt = invocation.getArgument(0);
                    // For follow-up questions, verify conversation history is included
                    if (prompt.contains("Conversation History")) {
                        assertThat(prompt).contains("USER:");
                        assertThat(prompt).contains("ASSISTANT:");
                    }
                    return "Answer to the question.";
                });
        
        // When: User asks multiple questions in sequence
        for (String question : questions) {
            chatService.askQuestion(wiki.getId(), question);
        }
        
        // Then: Conversation history should contain all messages
        List<ChatMessage> history = chatService.getConversationHistory(wiki.getId());
        assertThat(history).hasSize(questions.size() * 2); // Each question + answer
        
        // Verify messages are ordered by timestamp
        for (int i = 0; i < history.size() - 1; i++) {
            assertThat(history.get(i).getCreatedAt())
                    .isBeforeOrEqualTo(history.get(i + 1).getCreatedAt());
        }
        
        // Verify alternating roles
        for (int i = 0; i < history.size(); i++) {
            MessageRole expectedRole = (i % 2 == 0) ? MessageRole.USER : MessageRole.ASSISTANT;
            assertThat(history.get(i).getRole()).isEqualTo(expectedRole);
        }
    }
    
    /**
     * Feature: codewiki-generator, Property 26: Chatbot Multi-Language Support
     * 
     * **Validates: Requirements 15.4**
     * 
     * For any question about code in a supported programming language, the Chatbot
     * should be able to generate relevant answers regardless of the language.
     */
    @Property(tries = 100)
    void chatbotSupportsMultipleLanguages(
            @ForAll("programmingLanguages") String language,
            @ForAll @NotEmpty String question) {
        
        // Given: A wiki with files in different languages
        Wiki wiki = createTestWiki("https://github.com/test/multilang");
        
        WikiSection section = new WikiSection();
        section.setWiki(wiki);
        section.setSectionType(SectionType.OVERVIEW);
        section.setTitle("Overview");
        section.setContent("Multi-language project");
        section.setOrderIndex(0);
        wiki.getSections().add(section);
        
        FileExplanation file = new FileExplanation();
        file.setWiki(wiki);
        file.setFilePath("example." + getFileExtension(language));
        file.setLanguage(language);
        file.setExplanation("Code in " + language);
        wiki.getFileExplanations().add(file);
        
        wikiRepository.save(wiki);
        
        // Mock LLM to return language-aware response
        when(llmClient.generateWithRetry(anyString(), anyInt()))
                .thenReturn("This " + language + " code does the following...");
        
        // When: User asks about the code
        ChatResponse response = chatService.askQuestion(wiki.getId(), question);
        
        // Then: Response should be generated successfully
        assertThat(response).isNotNull();
        assertThat(response.getAnswer()).isNotEmpty();
    }
    
    // Helper methods and providers
    
    private Wiki createTestWiki(String repoUrl) {
        Wiki wiki = new Wiki();
        wiki.setRepositoryUrl(repoUrl);
        wiki.setRepositoryName(extractRepoName(repoUrl));
        wiki.setStatus(WikiStatus.COMPLETED);
        return wiki;
    }
    
    private String extractRepoName(String url) {
        String[] parts = url.split("/");
        return parts[parts.length - 1];
    }
    
    @Provide
    Arbitrary<String> programmingLanguages() {
        return Arbitraries.of("Java", "Python", "JavaScript", "TypeScript", 
                "Go", "Rust", "C++", "C#", "Ruby", "PHP");
    }
    
    private String getFileExtension(String language) {
        return switch (language) {
            case "Java" -> "java";
            case "Python" -> "py";
            case "JavaScript" -> "js";
            case "TypeScript" -> "ts";
            case "Go" -> "go";
            case "Rust" -> "rs";
            case "C++" -> "cpp";
            case "C#" -> "cs";
            case "Ruby" -> "rb";
            case "PHP" -> "php";
            default -> "txt";
        };
    }
}
