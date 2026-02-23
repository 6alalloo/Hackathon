package com.codewiki.controller;

import com.codewiki.dto.FileExplanationDTO;
import com.codewiki.dto.GenerationStatusResponse;
import com.codewiki.dto.WikiResponse;
import com.codewiki.dto.WikiSectionDTO;
import com.codewiki.dto.WikiSubmissionRequest;
import com.codewiki.dto.WikiSubmissionResponse;
import com.codewiki.model.GenerationPhase;
import com.codewiki.model.GenerationState;
import com.codewiki.model.GenerationStatus;
import com.codewiki.model.ValidationResult;
import com.codewiki.model.Wiki;
import com.codewiki.model.WikiStatus;
import com.codewiki.repository.GenerationStatusRepository;
import com.codewiki.repository.WikiRepository;
import com.codewiki.service.RateLimiterService;
import com.codewiki.service.RepositoryService;
import com.codewiki.service.WikiGenerationOrchestratorService;
import jakarta.validation.Valid;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

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

    private final WikiRepository wikiRepository;
    private final GenerationStatusRepository generationStatusRepository;
    private final RepositoryService repositoryService;
    private final WikiGenerationOrchestratorService wikiGenerationOrchestratorService;
    private final RateLimiterService rateLimiterService;

    public WikiController(
            WikiRepository wikiRepository,
            GenerationStatusRepository generationStatusRepository,
            RepositoryService repositoryService,
            WikiGenerationOrchestratorService wikiGenerationOrchestratorService,
            RateLimiterService rateLimiterService) {
        this.wikiRepository = wikiRepository;
        this.generationStatusRepository = generationStatusRepository;
        this.repositoryService = repositoryService;
        this.wikiGenerationOrchestratorService = wikiGenerationOrchestratorService;
        this.rateLimiterService = rateLimiterService;
    }

    /**
     * Submit a repository URL to generate a wiki.
     */
    @PostMapping
    public ResponseEntity<?> submitWiki(@Valid @RequestBody WikiSubmissionRequest request) {
        logger.info("Received wiki submission request for URL: {}", request.getRepositoryUrl());

        ValidationResult validationResult = repositoryService.validateRepositoryUrl(request.getRepositoryUrl());
        if (!validationResult.isSuccess()) {
            logger.warn("Repository URL validation failed: {}", validationResult.getErrorMessage());
            return ResponseEntity.badRequest().body(validationResult.getErrorMessage());
        }

        Optional<Wiki> existingWiki = wikiRepository.findByRepositoryUrl(request.getRepositoryUrl());
        if (existingWiki.isPresent()) {
            Wiki wiki = existingWiki.get();
            logger.info("Found existing wiki for URL: {}, wikiId: {}", request.getRepositoryUrl(), wiki.getId());
            return ResponseEntity.ok(new WikiSubmissionResponse(wiki.getId(), wiki.getStatus().name()));
        }

        if (!rateLimiterService.tryAcquireGenerationSlot()) {
            logger.warn("Generation rate limit reached, request rejected");
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                    .body("Generation rate limit reached. Please try again later.");
        }

        try {
            long repoSize = repositoryService.getRepositorySize(request.getRepositoryUrl());
            logger.info("Repository size: {} bytes", repoSize);

            Wiki wiki = new Wiki();
            wiki.setRepositoryUrl(request.getRepositoryUrl());
            wiki.setRepositoryName(extractRepositoryName(request.getRepositoryUrl()));
            wiki.setStatus(WikiStatus.PENDING);
            wiki = wikiRepository.save(wiki);

            GenerationStatus status = new GenerationStatus();
            status.setWikiId(wiki.getId());
            status.setPhase(GenerationPhase.INITIALIZING);
            status.setStatus(GenerationState.PENDING);
            generationStatusRepository.save(status);

            wikiGenerationOrchestratorService.generateWikiAsync(wiki.getId(), request.getRepositoryUrl());

            logger.info("Wiki generation initiated for wikiId: {}", wiki.getId());
            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(new WikiSubmissionResponse(wiki.getId(), WikiStatus.PENDING.name()));
        } catch (GitAPIException e) {
            rateLimiterService.releaseGenerationSlot();
            logger.error("Failed to validate repository size for URL: {}", request.getRepositoryUrl(), e);
            return ResponseEntity.badRequest().body("Repository validation failed: " + e.getMessage());
        } catch (Exception e) {
            rateLimiterService.releaseGenerationSlot();
            logger.error("Unexpected error during wiki submission", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("An unexpected error occurred: " + e.getMessage());
        }
    }

    /**
     * Retrieve a wiki by ID.
     */
    @GetMapping("/{wikiId}")
    public ResponseEntity<?> getWiki(@PathVariable String wikiId) {
        logger.info("Retrieving wiki: {}", wikiId);

        Optional<Wiki> wikiOpt = wikiRepository.findById(wikiId);
        if (wikiOpt.isEmpty()) {
            logger.warn("Wiki not found: {}", wikiId);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Wiki not found");
        }

        WikiResponse response = convertToWikiResponse(wikiOpt.get());
        return ResponseEntity.ok(response);
    }

    /**
     * Trigger wiki regeneration.
     */
    @PostMapping("/{wikiId}/regenerate")
    public ResponseEntity<?> regenerateWiki(@PathVariable String wikiId) {
        logger.info("Regeneration requested for wiki: {}", wikiId);

        Optional<Wiki> wikiOpt = wikiRepository.findById(wikiId);
        if (wikiOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Wiki not found");
        }

        if (!rateLimiterService.tryAcquireGenerationSlot()) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                    .body("Generation rate limit reached. Please try again later.");
        }

        try {
            Wiki wiki = wikiOpt.get();
            wiki.setStatus(WikiStatus.IN_PROGRESS);
            wiki.setStale(false);
            wikiRepository.save(wiki);

            GenerationStatus status = new GenerationStatus();
            status.setWikiId(wikiId);
            status.setPhase(GenerationPhase.REGENERATING);
            status.setStatus(GenerationState.IN_PROGRESS);
            generationStatusRepository.save(status);

            wikiGenerationOrchestratorService.generateWikiAsync(wikiId, wiki.getRepositoryUrl());
            return ResponseEntity.ok(new WikiSubmissionResponse(wikiId, WikiStatus.IN_PROGRESS.name()));
        } catch (Exception e) {
            rateLimiterService.releaseGenerationSlot();
            logger.error("Failed to initiate regeneration for wikiId: {}", wikiId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Failed to initiate regeneration: " + e.getMessage());
        }
    }

    /**
     * Poll generation status.
     */
    @GetMapping("/{wikiId}/status")
    public ResponseEntity<?> getGenerationStatus(@PathVariable String wikiId) {
        Optional<Wiki> wikiOpt = wikiRepository.findById(wikiId);
        if (wikiOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Wiki not found");
        }

        Optional<GenerationStatus> latest = generationStatusRepository.findTopByWikiIdOrderByUpdatedAtDesc(wikiId);
        if (latest.isPresent()) {
            GenerationStatus status = latest.get();
            return ResponseEntity.ok(new GenerationStatusResponse(
                    status.getStatus(),
                    status.getPhase(),
                    status.getErrorMessage()
            ));
        }

        Wiki wiki = wikiOpt.get();
        return ResponseEntity.ok(new GenerationStatusResponse(
                mapWikiStatus(wiki.getStatus()),
                GenerationPhase.UNKNOWN,
                null
        ));
    }

    private GenerationState mapWikiStatus(WikiStatus wikiStatus) {
        return switch (wikiStatus) {
            case PENDING -> GenerationState.PENDING;
            case IN_PROGRESS -> GenerationState.IN_PROGRESS;
            case COMPLETED -> GenerationState.COMPLETED;
            case FAILED -> GenerationState.FAILED;
        };
    }

    private String extractRepositoryName(String url) {
        String[] parts = url.split("/");
        if (parts.length >= 5) {
            return parts[3] + "/" + parts[4].replace(".git", "");
        }
        return url;
    }

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

        List<WikiSectionDTO> sectionDTOs = wiki.getSections().stream()
                .map(this::convertToWikiSectionDTO)
                .collect(Collectors.toList());
        response.setSections(sectionDTOs);

        List<FileExplanationDTO> fileExpDTOs = wiki.getFileExplanations().stream()
                .map(this::convertToFileExplanationDTO)
                .collect(Collectors.toList());
        response.setFileExplanations(fileExpDTOs);

        return response;
    }

    private WikiSectionDTO convertToWikiSectionDTO(com.codewiki.model.WikiSection section) {
        WikiSectionDTO dto = new WikiSectionDTO();
        dto.setId(section.getId());
        dto.setSectionType(section.getSectionType().name());
        dto.setTitle(section.getTitle());
        dto.setContent(section.getContent());
        dto.setOrderIndex(section.getOrderIndex());
        return dto;
    }

    private FileExplanationDTO convertToFileExplanationDTO(com.codewiki.model.FileExplanation fileExp) {
        FileExplanationDTO dto = new FileExplanationDTO();
        dto.setId(fileExp.getId());
        dto.setFilePath(fileExp.getFilePath());
        dto.setLanguage(fileExp.getLanguage());
        dto.setExplanation(fileExp.getExplanation());
        dto.setCodeSnippet(fileExp.getCodeSnippet());
        return dto;
    }
}
