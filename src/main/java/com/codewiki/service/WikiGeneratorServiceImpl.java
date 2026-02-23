package com.codewiki.service;

import com.codewiki.client.LLMClient;
import com.codewiki.model.*;
import com.codewiki.repository.WikiRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Implementation of WikiGeneratorService.
 * Orchestrates multi-phase wiki generation using LLM.
 */
@Service
public class WikiGeneratorServiceImpl implements WikiGeneratorService {
    
    private static final Logger logger = LoggerFactory.getLogger(WikiGeneratorServiceImpl.class);
    private static final int MAX_TOKENS_PER_REQUEST = 8000;
    private static final int ESTIMATED_TOKENS_PER_CHAR = 4; // Conservative estimate
    
    private final LLMClient llmClient;
    private final RepositoryService repositoryService;
    private final WikiRepository wikiRepository;
    
    public WikiGeneratorServiceImpl(LLMClient llmClient, 
                                   RepositoryService repositoryService,
                                   WikiRepository wikiRepository) {
        this.llmClient = llmClient;
        this.repositoryService = repositoryService;
        this.wikiRepository = wikiRepository;
    }
    
    @Override
    @Transactional
    public Wiki generateWiki(String repositoryUrl, Path repoPath) {
        logger.info("Starting wiki generation for repository: {}", repositoryUrl);
        
        try {
            // Detect code files
            List<CodeFile> codeFiles = repositoryService.detectCodeFiles(repoPath);
            logger.info("Detected {} code files", codeFiles.size());
            
            // Build repository context
            RepositoryContext context = buildRepositoryContext(repoPath, codeFiles);
            logger.info("Built repository context with {} files", context.getTotalFiles());
            
            // Generate all sections
            String overview = generateOverview(context);
            logger.info("Generated overview section");
            
            String architecture = generateArchitecture(context);
            logger.info("Generated architecture section");
            
            List<FileExplanation> fileExplanations = generateFileExplanations(context);
            logger.info("Generated {} file explanations", fileExplanations.size());
            
            String interactions = generateComponentInteractions(context);
            logger.info("Generated component interactions section");
            
            // Assemble and save wiki
            Wiki wiki = assembleWiki(repositoryUrl, context.getCommitHash(), 
                                    overview, architecture, interactions, fileExplanations);
            
            logger.info("Wiki generation completed successfully for: {}", repositoryUrl);
            return wiki;
            
        } catch (Exception e) {
            logger.error("Wiki generation failed for repository: {}", repositoryUrl, e);
            throw new RuntimeException("Wiki generation failed: " + e.getMessage(), e);
        }
    }
    
    @Override
    public RepositoryContext buildRepositoryContext(Path repoPath, List<CodeFile> codeFiles) {
        RepositoryContext context = new RepositoryContext();
        
        // Set basic info
        context.setRepositoryName(repoPath.getFileName().toString());
        context.setCodeFiles(codeFiles);
        context.setTotalFiles(codeFiles.size());
        
        // Calculate language distribution
        Map<String, Integer> languageDistribution = codeFiles.stream()
                .collect(Collectors.groupingBy(
                        CodeFile::getLanguage,
                        Collectors.collectingAndThen(Collectors.counting(), Long::intValue)
                ));
        context.setLanguageDistribution(languageDistribution);
        
        // Identify entry points (common entry point file names)
        List<String> entryPoints = codeFiles.stream()
                .map(CodeFile::getFilePath)
                .filter(this::isEntryPoint)
                .collect(Collectors.toList());
        context.setEntryPoints(entryPoints);
        
        // Build file structure tree
        String fileStructure = buildFileStructureTree(codeFiles);
        context.setFileStructure(fileStructure);
        
        // Get commit hash
        try {
            String commitHash = getLatestCommitHash(repoPath);
            context.setCommitHash(commitHash);
        } catch (Exception e) {
            logger.warn("Failed to get commit hash", e);
            context.setCommitHash("unknown");
        }
        
        return context;
    }
    
    @Override
    public String generateOverview(RepositoryContext context) {
        logger.debug("Generating overview for repository: {}", context.getRepositoryName());
        
        String prompt = buildOverviewPrompt(context);
        return llmClient.generateWithRetry(prompt, 3);
    }
    
    @Override
    public String generateArchitecture(RepositoryContext context) {
        logger.debug("Generating architecture for repository: {}", context.getRepositoryName());
        
        String prompt = buildArchitecturePrompt(context);
        return llmClient.generateWithRetry(prompt, 3);
    }
    
    @Override
    public List<FileExplanation> generateFileExplanations(RepositoryContext context) {
        logger.debug("Generating file explanations for {} files", context.getTotalFiles());
        
        List<FileExplanation> explanations = new ArrayList<>();
        List<CodeFile> codeFiles = context.getCodeFiles();
        
        // Group files by directory for better context
        Map<String, List<CodeFile>> filesByDirectory = groupFilesByDirectory(codeFiles);
        
        // Process each directory group
        for (Map.Entry<String, List<CodeFile>> entry : filesByDirectory.entrySet()) {
            String directory = entry.getKey();
            List<CodeFile> dirFiles = entry.getValue();
            
            // Chunk files within directory if needed
            List<List<CodeFile>> chunks = chunkFiles(dirFiles, MAX_TOKENS_PER_REQUEST);
            
            for (List<CodeFile> chunk : chunks) {
                logger.debug("Processing chunk of {} files in directory: {}", chunk.size(), directory);
                
                String prompt = buildFileExplanationPrompt(context, chunk);
                String response = llmClient.generateWithRetry(prompt, 3);
                
                // Parse response and create FileExplanation objects
                List<FileExplanation> chunkExplanations = parseFileExplanations(chunk, response);
                explanations.addAll(chunkExplanations);
            }
        }
        
        return explanations;
    }
    
    @Override
    public String generateComponentInteractions(RepositoryContext context) {
        logger.debug("Generating component interactions for repository: {}", context.getRepositoryName());
        
        String prompt = buildComponentInteractionsPrompt(context);
        return llmClient.generateWithRetry(prompt, 3);
    }
    
    @Override
    @Transactional
    public Wiki assembleWiki(String repositoryUrl, String commitHash, String overview,
                            String architecture, String interactions, List<FileExplanation> fileExplanations) {
        logger.debug("Assembling wiki for repository: {}", repositoryUrl);
        
        // Create wiki entity
        Wiki wiki = new Wiki();
        wiki.setRepositoryUrl(repositoryUrl);
        wiki.setRepositoryName(extractRepositoryName(repositoryUrl));
        wiki.setLastCommitHash(commitHash);
        wiki.setStatus(WikiStatus.COMPLETED);
        
        // Create wiki sections
        List<WikiSection> sections = new ArrayList<>();
        
        WikiSection overviewSection = new WikiSection();
        overviewSection.setWiki(wiki);
        overviewSection.setSectionType(SectionType.OVERVIEW);
        overviewSection.setTitle("Overview");
        overviewSection.setContent(overview);
        overviewSection.setOrderIndex(0);
        sections.add(overviewSection);
        
        WikiSection architectureSection = new WikiSection();
        architectureSection.setWiki(wiki);
        architectureSection.setSectionType(SectionType.ARCHITECTURE);
        architectureSection.setTitle("Architecture");
        architectureSection.setContent(architecture);
        architectureSection.setOrderIndex(1);
        sections.add(architectureSection);
        
        WikiSection interactionsSection = new WikiSection();
        interactionsSection.setWiki(wiki);
        interactionsSection.setSectionType(SectionType.INTERACTIONS);
        interactionsSection.setTitle("Component Interactions");
        interactionsSection.setContent(interactions);
        interactionsSection.setOrderIndex(2);
        sections.add(interactionsSection);
        
        wiki.setSections(sections);
        
        // Associate file explanations with wiki
        for (FileExplanation explanation : fileExplanations) {
            explanation.setWiki(wiki);
        }
        wiki.setFileExplanations(fileExplanations);
        
        // Save wiki (cascades to sections and file explanations)
        return wikiRepository.save(wiki);
    }
    
    // Helper methods
    
    private boolean isEntryPoint(String filePath) {
        String fileName = filePath.toLowerCase();
        return fileName.contains("main.") || 
               fileName.contains("index.") || 
               fileName.contains("app.") ||
               fileName.contains("application.") ||
               fileName.endsWith("main.java") ||
               fileName.endsWith("main.py") ||
               fileName.endsWith("main.js") ||
               fileName.endsWith("index.js") ||
               fileName.endsWith("index.html");
    }
    
    private String buildFileStructureTree(List<CodeFile> codeFiles) {
        StringBuilder tree = new StringBuilder();
        Map<String, List<String>> dirStructure = new TreeMap<>();
        
        for (CodeFile file : codeFiles) {
            String path = file.getFilePath();
            int lastSlash = path.lastIndexOf('/');
            String dir = lastSlash > 0 ? path.substring(0, lastSlash) : ".";
            String fileName = lastSlash > 0 ? path.substring(lastSlash + 1) : path;
            
            dirStructure.computeIfAbsent(dir, k -> new ArrayList<>()).add(fileName);
        }
        
        for (Map.Entry<String, List<String>> entry : dirStructure.entrySet()) {
            tree.append(entry.getKey()).append("/\n");
            for (String file : entry.getValue()) {
                tree.append("  - ").append(file).append("\n");
            }
        }
        
        return tree.toString();
    }
    
    private String getLatestCommitHash(Path repoPath) throws IOException {
        Path gitDir = repoPath.resolve(".git");
        if (!Files.exists(gitDir)) {
            return "unknown";
        }
        
        Path headFile = gitDir.resolve("HEAD");
        if (!Files.exists(headFile)) {
            return "unknown";
        }
        
        String headContent = Files.readString(headFile).trim();
        if (headContent.startsWith("ref:")) {
            String refPath = headContent.substring(5).trim();
            Path refFile = gitDir.resolve(refPath);
            if (Files.exists(refFile)) {
                return Files.readString(refFile).trim();
            }
        }
        
        return headContent;
    }
    
    private Map<String, List<CodeFile>> groupFilesByDirectory(List<CodeFile> codeFiles) {
        Map<String, List<CodeFile>> grouped = new LinkedHashMap<>();
        
        for (CodeFile file : codeFiles) {
            String path = file.getFilePath();
            int lastSlash = path.lastIndexOf('/');
            String dir = lastSlash > 0 ? path.substring(0, lastSlash) : ".";
            
            grouped.computeIfAbsent(dir, k -> new ArrayList<>()).add(file);
        }
        
        return grouped;
    }
    
    private List<List<CodeFile>> chunkFiles(List<CodeFile> files, int maxTokens) {
        List<List<CodeFile>> chunks = new ArrayList<>();
        List<CodeFile> currentChunk = new ArrayList<>();
        int currentTokens = 0;
        
        for (CodeFile file : files) {
            // Estimate tokens for this file (path + language + some overhead)
            int estimatedTokens = (file.getFilePath().length() + file.getLanguage().length()) * ESTIMATED_TOKENS_PER_CHAR;
            
            if (currentTokens + estimatedTokens > maxTokens && !currentChunk.isEmpty()) {
                chunks.add(new ArrayList<>(currentChunk));
                currentChunk.clear();
                currentTokens = 0;
            }
            
            currentChunk.add(file);
            currentTokens += estimatedTokens;
        }
        
        if (!currentChunk.isEmpty()) {
            chunks.add(currentChunk);
        }
        
        return chunks.isEmpty() ? List.of(files) : chunks;
    }
    
    private String extractRepositoryName(String repositoryUrl) {
        // Extract owner/repo from https://github.com/owner/repo
        String[] parts = repositoryUrl.split("/");
        if (parts.length >= 2) {
            return parts[parts.length - 2] + "/" + parts[parts.length - 1].replace(".git", "");
        }
        return repositoryUrl;
    }
    
    private String buildOverviewPrompt(RepositoryContext context) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("You are a technical documentation expert analyzing code repositories.\n\n");
        prompt.append("Repository: ").append(context.getRepositoryName()).append("\n");
        prompt.append("Total Files: ").append(context.getTotalFiles()).append("\n");
        prompt.append("Languages: ").append(context.getLanguageDistribution().keySet()).append("\n\n");
        prompt.append("File Structure:\n").append(context.getFileStructure()).append("\n\n");
        
        if (!context.getEntryPoints().isEmpty()) {
            prompt.append("Entry Points: ").append(context.getEntryPoints()).append("\n\n");
        }
        
        prompt.append("Task: Generate a high-level overview of this repository.\n");
        prompt.append("Include:\n");
        prompt.append("- Purpose and main functionality\n");
        prompt.append("- Key features\n");
        prompt.append("- Technology stack\n");
        prompt.append("- Target use case\n\n");
        prompt.append("Write in clear, concise markdown format.\n");
        
        return prompt.toString();
    }
    
    private String buildArchitecturePrompt(RepositoryContext context) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("You are a technical documentation expert analyzing code repositories.\n\n");
        prompt.append("Repository: ").append(context.getRepositoryName()).append("\n");
        prompt.append("Languages: ").append(context.getLanguageDistribution()).append("\n\n");
        prompt.append("File Structure:\n").append(context.getFileStructure()).append("\n\n");
        
        prompt.append("Task: Generate an architecture breakdown of this repository.\n");
        prompt.append("Include:\n");
        prompt.append("- Component breakdown (identify main modules/packages)\n");
        prompt.append("- Design patterns used\n");
        prompt.append("- Architectural style (MVC, layered, microservices, etc.)\n");
        prompt.append("- Key dependencies and relationships\n\n");
        prompt.append("Write in clear, concise markdown format.\n");
        
        return prompt.toString();
    }
    
    private String buildFileExplanationPrompt(RepositoryContext context, List<CodeFile> files) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("You are a technical documentation expert analyzing code files.\n\n");
        prompt.append("Repository: ").append(context.getRepositoryName()).append("\n\n");
        
        prompt.append("Files to explain:\n");
        for (CodeFile file : files) {
            prompt.append("- ").append(file.getFilePath()).append(" (").append(file.getLanguage()).append(")\n");
        }
        prompt.append("\n");
        
        prompt.append("Task: For each file, provide a brief explanation.\n");
        prompt.append("Format your response as:\n");
        prompt.append("FILE: <filepath>\n");
        prompt.append("EXPLANATION: <explanation>\n");
        prompt.append("---\n\n");
        prompt.append("Keep explanations concise (2-3 sentences per file).\n");
        
        return prompt.toString();
    }
    
    private String buildComponentInteractionsPrompt(RepositoryContext context) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("You are a technical documentation expert analyzing code repositories.\n\n");
        prompt.append("Repository: ").append(context.getRepositoryName()).append("\n");
        prompt.append("Languages: ").append(context.getLanguageDistribution()).append("\n\n");
        prompt.append("File Structure:\n").append(context.getFileStructure()).append("\n\n");
        
        prompt.append("Task: Analyze how components interact with each other.\n");
        prompt.append("Include:\n");
        prompt.append("- Data flow between components\n");
        prompt.append("- API/interface boundaries\n");
        prompt.append("- External dependencies\n");
        prompt.append("- Communication patterns\n\n");
        prompt.append("Write in clear, concise markdown format.\n");
        
        return prompt.toString();
    }
    
    private List<FileExplanation> parseFileExplanations(List<CodeFile> files, String response) {
        List<FileExplanation> explanations = new ArrayList<>();
        
        // Simple parsing: split by "---" or "FILE:"
        String[] parts = response.split("---");
        
        for (String part : parts) {
            part = part.trim();
            if (part.isEmpty()) continue;
            
            // Try to extract FILE: and EXPLANATION:
            int fileIndex = part.indexOf("FILE:");
            int explIndex = part.indexOf("EXPLANATION:");
            
            if (fileIndex >= 0 && explIndex >= 0) {
                String filePath = part.substring(fileIndex + 5, explIndex).trim();
                String explanation = part.substring(explIndex + 12).trim();
                
                // Find matching CodeFile
                CodeFile matchingFile = files.stream()
                        .filter(f -> f.getFilePath().equals(filePath) || filePath.contains(f.getFilePath()))
                        .findFirst()
                        .orElse(null);
                
                if (matchingFile != null) {
                    FileExplanation fileExpl = new FileExplanation();
                    fileExpl.setFilePath(matchingFile.getFilePath());
                    fileExpl.setLanguage(matchingFile.getLanguage());
                    fileExpl.setExplanation(explanation);
                    explanations.add(fileExpl);
                }
            }
        }
        
        // Fallback: create explanations for files without matches
        for (CodeFile file : files) {
            boolean hasExplanation = explanations.stream()
                    .anyMatch(e -> e.getFilePath().equals(file.getFilePath()));
            
            if (!hasExplanation) {
                FileExplanation fileExpl = new FileExplanation();
                fileExpl.setFilePath(file.getFilePath());
                fileExpl.setLanguage(file.getLanguage());
                fileExpl.setExplanation("File explanation not available.");
                explanations.add(fileExpl);
            }
        }
        
        return explanations;
    }
}
