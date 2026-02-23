package com.codewiki.controller;

import com.codewiki.dto.*;
import com.codewiki.model.*;
import com.codewiki.repository.GenerationStatusRepository;
import com.codewiki.repository.WikiRepository;
import com.codewiki.service.RateLimiterService;
import com.codewiki.service.RepositoryService;
import com.codewiki.service.WikiGeneratorService;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Async;
import org.springframework.web.bind.annotation.*;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * REST controller for wiki management operations.
 * Handles wiki submission, retrieval, regeneration, and status polling.
 */
@RestController
@RequestMapping("/api/wikis")
public class WikiController {
    
    private static final Logger logger = LoggerFactory.getLogger(WikiController.class);
    
    @Autowired
    WikiRepository wikiRepository;
    
    @Autowired
    GenerationStatusRepository generationStatusRepository;
    
    @Autowired
    RepositoryService repositoryService;
    
    @Autowired
    WikiGeneratorService wikiGeneratorService;
    
    @Autowired
    RateLimiterService rateLimiterService;
    
    /**
     * Submit a repository URL to generate a wiki.
     * POST /api/wikis
     * 
     * @param request the wiki submission request containing repository URL
     * @return WikiSubmissionResponse with wikiId and status
     */
    @PostMapping
    public ResponseEntity<?> submitWiki(@RequestBody WikiSubmissionRequest request) {
        logger.info("Received wiki submission request for URL: {}", request.getRepositoryUrl());
        
        // Validate repository URL
        ValidationResult validationResult = repositoryService.validateRepositoryUrl(request.getRepositoryUrl());
        if (!validationResult.isSuccess()) {
            logger.warn("Repository URL validation failed: {}", validationResult.getErrorMessage());
            return ResponseEntity.badRequest().body(validationResult.getErrorMessage());
        }
        
        // Check if wiki already exists (caching)
        Optional<Wiki> existingWiki = wikiRepository.findByRepositoryUrl(request.getRepositoryUrl());
        if (existingWiki.isPresent()) {
            Wiki wiki = existingWiki.get();
            logger.info("Found existing wiki for URL: {}, wikiId: {}", request.getRepositoryUrl(), wiki.getId());
            return ResponseEntity.ok(new WikiSubmissionResponse(wiki.getId(), wiki.getStatus().name()));
        }
        
        // Try to acquire generation slot
        if (!rateLimiterService.tryAcquireGenerationSlot()) {
            logger.warn("Generation rate limit reached, request queued");
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                    .body("Generation rate limit reached. Please try again later.");
        }
        
        try {
            // Validate repository size
            long repoSize = repositoryService.getRepositorySize(request.getRepositoryUrl());
            logger.info("Repository size: {} bytes", repoSize);
            
            // Create wiki entity with PENDING status
            Wiki wiki = new Wiki();
            wiki.setRepositoryUrl(request.getRepositoryUrl());
            wiki.setRepositoryName(extractRepositoryName(request.getRepositoryUrl()));
            wiki.setStatus(WikiStatus.PENDING);
            wiki = wikiRepository.save(wiki);
            
            // Create initial generation status
            GenerationStatus status = new GenerationStatus();
            status.setWikiId(wiki.getId());
            status.setPhase("Initializing");
            status.setStatus("PENDING");
            generationStatusRepository.save(status);
            
            // Start async generation
            String wikiId = wiki.getId();
            generateWikiAsync(wikiId, request.getRepositoryUrl());
            
            logger.info("Wiki generation initiated for wikiId: {}", wikiId);
            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(new WikiSubmissionResponse(wikiId, "PENDING"));
            
        } catch (GitAPIException e) {
            rateLimiterService.releaseGenerationSlot();
            logger.error("Failed to validate repository: {}", e.getMessage(), e);
            return ResponseEntity.badRequest().body("Repository validation failed: " + e.getMessage());
        } catch (Exception e) {
            rateLimiterService.releaseGenerationSlot();
            logger.error("Unexpected error during wiki submission: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("An unexpected error occurred: " + e.getMessage());
        }
    }
    
    /**
     * Retrieve a wiki by ID.
     * GET /api/wikis/{wikiId}
     * 
     * @param wikiId the wiki ID
     * @return WikiResponse with all sections and file explanations
     */
    @GetMapping("/{wikiId}")
    public ResponseEntity<?> getWiki(@PathVariable String wikiId) {
        logger.info("Retrieving wiki: {}", wikiId);
        
        Optional<Wiki> wikiOpt = wikiRepository.findById(wikiId);
        if (wikiOpt.isEmpty()) {
            logger.warn("Wiki not found: {}", wikiId);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Wiki not found");
        }
        
        Wiki wiki = wikiOpt.get();
        WikiResponse response = convertToWikiResponse(wiki);
        
        logger.info("Wiki retrieved successfully: {}", wikiId);
        return ResponseEntity.ok(response);
    }
    
    /**
     * Trigger wiki regeneration.
     * POST /api/wikis/{wikiId}/regenerate
     * 
     * @param wikiId the wiki ID
     * @return WikiSubmissionResponse with status
     */
    @PostMapping("/{wikiId}/regenerate")
    public ResponseEntity<?> regenerateWiki(@PathVariable String wikiId) {
        logger.info("Regeneration requested for wiki: {}", wikiId);
        
        Optional<Wiki> wikiOpt = wikiRepository.findById(wikiId);
        if (wikiOpt.isEmpty()) {
            logger.warn("Wiki not found for regeneration: {}", wikiId);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Wiki not found");
        }
        
        // Try to acquire generation slot
        if (!rateLimiterService.tryAcquireGenerationSlot()) {
            logger.warn("Generation rate limit reached, regeneration request rejected");
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                    .body("Generation rate limit reached. Please try again later.");
        }
        
        try {
            Wiki wiki = wikiOpt.get();
            
            // Update wiki status to IN_PROGRESS
            wiki.setStatus(WikiStatus.IN_PROGRESS);
            wiki.setStale(false);
            wikiRepository.save(wiki);
            
            // Create generation status
            GenerationStatus status = new GenerationStatus();
            status.setWikiId(wikiId);
            status.setPhase("Regenerating");
            status.setStatus("IN_PROGRESS");
            generationStatusRepository.save(status);
            
            // Start async regeneration
            generateWikiAsync(wikiId, wiki.getRepositoryUrl());
            
            logger.info("Wiki regeneration initiated for wikiId: {}", wikiId);
            return ResponseEntity.ok(new WikiSubmissionResponse(wikiId, "IN_PROGRESS"));
            
        } catch (Exception e) {
            rateLimiterService.releaseGenerationSlot();
            logger.error("Failed to initiate regeneration: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Failed to initiate regeneration: " + e.getMessage());
        }
    }
    
    /**
     * Poll generation status.
     * GET /api/wikis/{wikiId}/status
     * 
     * @param wikiId the wiki ID
     * @return GenerationStatusResponse with current status and phase
     */
    @GetMapping("/{wikiId}/status")
    public ResponseEntity<?> getGenerationStatus(@PathVariable String wikiId) {
        logger.debug("Status poll for wiki: {}", wikiId);
        
        Optional<Wiki> wikiOpt = wikiRepository.findById(wikiId);
        if (wikiOpt.isEmpty()) {
            logger.warn("Wiki not found for status check: {}", wikiId);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Wiki not found");
        }
        
        Wiki wiki = wikiOpt.get();
        
        // Try to find generation status
        List<GenerationStatus> statuses = generationStatusRepository.findAll().stream()
                .filter(s -> s.getWikiId().equals(wikiId))
                .collect(Collectors.toList());
        
        if (!statuses.isEmpty()) {
            GenerationStatus status = statuses.get(statuses.size() - 1); // Get latest
            GenerationStatusResponse response = new GenerationStatusResponse(
                    status.getStatus(),
                    status.getPhase(),
                    status.getErrorMessage()
            );
            return ResponseEntity.ok(response);
        }
        
        // Fallback to wiki status
        GenerationStatusResponse response = new GenerationStatusResponse(
                wiki.getStatus().name(),
                "Unknown",
                null
        );
        return ResponseEntity.ok(response);
    }
    
    /**
     * Async method to generate wiki.
     * Runs in a separate thread to avoid blocking the HTTP request.
     */
    @Async
    public void generateWikiAsync(String wikiId, String repositoryUrl) {
        Path repoPath = null;
        try {
            logger.info("Starting async wiki generation for wikiId: {}", wikiId);
            
            // Update status to IN_PROGRESS
            Optional<Wiki> wikiOpt = wikiRepository.findById(wikiId);
            if (wikiOpt.isEmpty()) {
                logger.error("Wiki not found during async generation: {}", wikiId);
                return;
            }
            
            Wiki wiki = wikiOpt.get();
            wiki.setStatus(WikiStatus.IN_PROGRESS);
            wikiRepository.save(wiki);
            
            updateGenerationStatus(wikiId, "IN_PROGRESS", "Cloning repository", null);
            
            // Clone repository
            repoPath = repositoryService.cloneRepository(repositoryUrl);
            logger.info("Repository cloned to: {}", repoPath);
            
            updateGenerationStatus(wikiId, "IN_PROGRESS", "Detecting code files", null);
            
            // Detect code files
            List<CodeFile> codeFiles = repositoryService.detectCodeFiles(repoPath);
            if (codeFiles.isEmpty()) {
                throw new IllegalStateException("No code files detected in repository");
            }
            logger.info("Detected {} code files", codeFiles.size());
            
            updateGenerationStatus(wikiId, "IN_PROGRESS", "Generating wiki content", null);
            
            // Generate wiki
            Wiki generatedWiki = wikiGeneratorService.generateWiki(repositoryUrl, repoPath);
            
            // Clear old sections and file explanations (for regeneration)
            wiki.getSections().clear();
            wiki.getFileExplanations().clear();
            
            // Update wiki with generated content
            wiki.setSections(generatedWiki.getSections());
            wiki.setFileExplanations(generatedWiki.getFileExplanations());
            wiki.setLastCommitHash(generatedWiki.getLastCommitHash());
            wiki.setStatus(WikiStatus.COMPLETED);
            wiki.setStale(false); // Clear stale flag after regeneration
            wikiRepository.save(wiki);
            
            updateGenerationStatus(wikiId, "COMPLETED", "Generation complete", null);
            
            logger.info("Wiki generation completed successfully for wikiId: {}", wikiId);
            
        } catch (Exception e) {
            logger.error("Wiki generation failed for wikiId: {}", wikiId, e);
            
            // Update wiki status to FAILED
            Optional<Wiki> wikiOpt = wikiRepository.findById(wikiId);
            if (wikiOpt.isPresent()) {
                Wiki wiki = wikiOpt.get();
                wiki.setStatus(WikiStatus.FAILED);
                wikiRepository.save(wiki);
            }
            
            updateGenerationStatus(wikiId, "FAILED", "Generation failed", e.getMessage());
            
        } finally {
            // Cleanup repository
            if (repoPath != null) {
                try {
                    repositoryService.cleanupRepository(repoPath);
                    logger.info("Repository cleaned up: {}", repoPath);
                } catch (Exception e) {
                    logger.warn("Failed to cleanup repository: {}", repoPath, e);
                }
            }
            
            // Release generation slot
            rateLimiterService.releaseGenerationSlot();
        }
    }
    
    /**
     * Helper method to update generation status.
     */
    private void updateGenerationStatus(String wikiId, String status, String phase, String errorMessage) {
        try {
            GenerationStatus genStatus = new GenerationStatus();
            genStatus.setWikiId(wikiId);
            genStatus.setStatus(status);
            genStatus.setPhase(phase);
            genStatus.setErrorMessage(errorMessage);
            generationStatusRepository.save(genStatus);
        } catch (Exception e) {
            logger.error("Failed to update generation status for wikiId: {}", wikiId, e);
        }
    }
    
    /**
     * Helper method to extract repository name from URL.
     */
    private String extractRepositoryName(String url) {
        // Extract owner/repo from https://github.com/owner/repo
        String[] parts = url.split("/");
        if (parts.length >= 5) {
            return parts[3] + "/" + parts[4].replace(".git", "");
        }
        return url;
    }
    
    /**
     * Helper method to convert Wiki entity to WikiResponse DTO.
     */
    private WikiResponse convertToWikiResponse(Wiki wiki) {
        WikiResponse response = new WikiResponse();
        response.setId(wiki.getId());
        response.setRepositoryUrl(wiki.getRepositoryUrl());
        response.setRepositoryName(wiki.getRepositoryName());
        response.setLastCommitHash(wiki.getLastCommitHash());
        response.setCreatedAt(wiki.getCreatedAt());
        response.setUpdatedAt(wiki.getUpdatedAt());
        response.setStale(wiki.isStale());
        response.setStatus(wiki.getStatus().name());
        
        // Convert sections
        List<WikiSectionDTO> sectionDTOs = wiki.getSections().stream()
                .map(this::convertToWikiSectionDTO)
                .collect(Collectors.toList());
        response.setSections(sectionDTOs);
        
        // Convert file explanations
        List<FileExplanationDTO> fileExpDTOs = wiki.getFileExplanations().stream()
                .map(this::convertToFileExplanationDTO)
                .collect(Collectors.toList());
        response.setFileExplanations(fileExpDTOs);
        
        return response;
    }
    
    /**
     * Helper method to convert WikiSection to WikiSectionDTO.
     */
    private WikiSectionDTO convertToWikiSectionDTO(WikiSection section) {
        WikiSectionDTO dto = new WikiSectionDTO();
        dto.setId(section.getId());
        dto.setSectionType(section.getSectionType().name());
        dto.setTitle(section.getTitle());
        dto.setContent(section.getContent());
        dto.setOrderIndex(section.getOrderIndex());
        return dto;
    }
    
    /**
     * Helper method to convert FileExplanation to FileExplanationDTO.
     */
    private FileExplanationDTO convertToFileExplanationDTO(FileExplanation fileExp) {
        FileExplanationDTO dto = new FileExplanationDTO();
        dto.setId(fileExp.getId());
        dto.setFilePath(fileExp.getFilePath());
        dto.setLanguage(fileExp.getLanguage());
        dto.setExplanation(fileExp.getExplanation());
        dto.setCodeSnippet(fileExp.getCodeSnippet());
        return dto;
    }
}
