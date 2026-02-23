package com.codewiki.service;

import com.codewiki.model.CodeFile;
import com.codewiki.model.FileExplanation;
import com.codewiki.model.RepositoryContext;
import com.codewiki.model.Wiki;

import java.nio.file.Path;
import java.util.List;

/**
 * Service interface for wiki generation operations.
 * Orchestrates multi-phase wiki generation using LLM.
 */
public interface WikiGeneratorService {
    
    /**
     * Generates a complete wiki for a repository.
     * Orchestrates all generation phases: overview, architecture, file explanations, interactions.
     * 
     * @param repositoryUrl the GitHub repository URL
     * @param repoPath the path to the cloned repository
     * @return the generated Wiki entity with all sections
     */
    Wiki generateWiki(String repositoryUrl, Path repoPath);
    
    /**
     * Builds repository context for LLM prompts.
     * Extracts file structure, primary languages, entry points.
     * 
     * @param repoPath the path to the cloned repository
     * @param codeFiles the list of detected code files
     * @return RepositoryContext containing metadata
     */
    RepositoryContext buildRepositoryContext(Path repoPath, List<CodeFile> codeFiles);
    
    /**
     * Generates high-level overview of the repository.
     * Uses single LLM call to generate purpose and key features.
     * 
     * @param context the repository context
     * @return the generated overview content
     */
    String generateOverview(RepositoryContext context);
    
    /**
     * Generates architecture breakdown of the repository.
     * Uses single LLM call to generate component breakdown and design patterns.
     * 
     * @param context the repository context
     * @return the generated architecture content
     */
    String generateArchitecture(RepositoryContext context);
    
    /**
     * Generates file-by-file explanations with chunking.
     * Chunks files into groups (max 8000 tokens per request).
     * Groups related files from same directory.
     * 
     * @param context the repository context
     * @return list of FileExplanation objects
     */
    List<FileExplanation> generateFileExplanations(RepositoryContext context);
    
    /**
     * Generates component interaction analysis.
     * Uses single LLM call to perform cross-reference analysis.
     * 
     * @param context the repository context
     * @return the generated component interactions content
     */
    String generateComponentInteractions(RepositoryContext context);
    
    /**
     * Assembles wiki from all generated sections.
     * Saves Wiki entity with all sections and file explanations.
     * 
     * @param repositoryUrl the GitHub repository URL
     * @param commitHash the commit hash
     * @param overview the overview content
     * @param architecture the architecture content
     * @param interactions the component interactions content
     * @param fileExplanations the file explanations
     * @return the assembled and persisted Wiki entity
     */
    Wiki assembleWiki(String repositoryUrl, String commitHash, String overview, 
                     String architecture, String interactions, List<FileExplanation> fileExplanations);
}
