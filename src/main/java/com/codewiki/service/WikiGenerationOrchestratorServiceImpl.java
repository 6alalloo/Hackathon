package com.codewiki.service;

import com.codewiki.model.CodeFile;
import com.codewiki.model.FileExplanation;
import com.codewiki.model.GenerationPhase;
import com.codewiki.model.GenerationState;
import com.codewiki.model.GenerationStatus;
import com.codewiki.model.Wiki;
import com.codewiki.model.WikiSection;
import com.codewiki.model.WikiStatus;
import com.codewiki.repository.GenerationStatusRepository;
import com.codewiki.repository.WikiRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

@Service
public class WikiGenerationOrchestratorServiceImpl implements WikiGenerationOrchestratorService {

    private static final Logger logger = LoggerFactory.getLogger(WikiGenerationOrchestratorServiceImpl.class);

    private final WikiRepository wikiRepository;
    private final GenerationStatusRepository generationStatusRepository;
    private final RepositoryService repositoryService;
    private final WikiGeneratorService wikiGeneratorService;
    private final RateLimiterService rateLimiterService;

    public WikiGenerationOrchestratorServiceImpl(
            WikiRepository wikiRepository,
            GenerationStatusRepository generationStatusRepository,
            RepositoryService repositoryService,
            WikiGeneratorService wikiGeneratorService,
            RateLimiterService rateLimiterService) {
        this.wikiRepository = wikiRepository;
        this.generationStatusRepository = generationStatusRepository;
        this.repositoryService = repositoryService;
        this.wikiGeneratorService = wikiGeneratorService;
        this.rateLimiterService = rateLimiterService;
    }

    @Override
    @Async("taskExecutor")
    @Transactional
    public void generateWikiAsync(String wikiId, String repositoryUrl) {
        Path repoPath = null;
        try {
            logger.info("Starting async wiki generation for wikiId: {}", wikiId);

            Optional<Wiki> wikiOpt = wikiRepository.findById(wikiId);
            if (wikiOpt.isEmpty()) {
                logger.error("Wiki not found during async generation: {}", wikiId);
                return;
            }

            Wiki wiki = wikiOpt.get();
            wiki.setStatus(WikiStatus.IN_PROGRESS);
            wikiRepository.save(wiki);

            updateGenerationStatus(wikiId, GenerationState.IN_PROGRESS, GenerationPhase.CLONING_REPOSITORY, null);

            repoPath = repositoryService.cloneRepository(repositoryUrl);
            logger.info("Repository cloned to: {}", repoPath);

            updateGenerationStatus(wikiId, GenerationState.IN_PROGRESS, GenerationPhase.DETECTING_CODE_FILES, null);

            List<CodeFile> codeFiles = repositoryService.detectCodeFiles(repoPath);
            if (codeFiles.isEmpty()) {
                throw new IllegalStateException("No code files detected in repository");
            }
            logger.info("Detected {} code files", codeFiles.size());

            updateGenerationStatus(wikiId, GenerationState.IN_PROGRESS, GenerationPhase.GENERATING_WIKI_CONTENT, null);

            Wiki generatedWiki = wikiGeneratorService.generateWiki(repositoryUrl, repoPath);
            replaceWikiContent(wiki, generatedWiki);

            wiki.setStatus(WikiStatus.COMPLETED);
            wiki.setStale(false);
            wikiRepository.save(wiki);

            updateGenerationStatus(wikiId, GenerationState.COMPLETED, GenerationPhase.GENERATION_COMPLETE, null);

            logger.info("Wiki generation completed successfully for wikiId: {}", wikiId);
        } catch (Exception e) {
            logger.error("Wiki generation failed for wikiId: {}", wikiId, e);

            wikiRepository.findById(wikiId).ifPresent(wiki -> {
                wiki.setStatus(WikiStatus.FAILED);
                wikiRepository.save(wiki);
            });

            updateGenerationStatus(
                    wikiId,
                    GenerationState.FAILED,
                    GenerationPhase.GENERATION_FAILED,
                    e.getMessage()
            );
        } finally {
            if (repoPath != null) {
                try {
                    repositoryService.cleanupRepository(repoPath);
                    logger.info("Repository cleaned up: {}", repoPath);
                } catch (Exception e) {
                    logger.warn("Failed to cleanup repository: {}", repoPath, e);
                }
            }
            rateLimiterService.releaseGenerationSlot();
        }
    }

    private void replaceWikiContent(Wiki targetWiki, Wiki generatedWiki) {
        targetWiki.getSections().clear();
        targetWiki.getFileExplanations().clear();

        for (WikiSection section : generatedWiki.getSections()) {
            section.setWiki(targetWiki);
            targetWiki.getSections().add(section);
        }

        for (FileExplanation explanation : generatedWiki.getFileExplanations()) {
            explanation.setWiki(targetWiki);
            targetWiki.getFileExplanations().add(explanation);
        }

        targetWiki.setLastCommitHash(generatedWiki.getLastCommitHash());
    }

    private void updateGenerationStatus(
            String wikiId,
            GenerationState state,
            GenerationPhase phase,
            String errorMessage) {
        try {
            GenerationStatus genStatus = new GenerationStatus();
            genStatus.setWikiId(wikiId);
            genStatus.setStatus(state);
            genStatus.setPhase(phase);
            genStatus.setErrorMessage(errorMessage);
            generationStatusRepository.save(genStatus);
        } catch (Exception e) {
            logger.error("Failed to update generation status for wikiId: {}", wikiId, e);
        }
    }
}
