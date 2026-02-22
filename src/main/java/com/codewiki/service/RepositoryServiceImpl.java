package com.codewiki.service;

import com.codewiki.model.CodeFile;
import com.codewiki.model.ValidationResult;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Implementation of RepositoryService for GitHub repository operations.
 */
@Service
public class RepositoryServiceImpl implements RepositoryService {
    
    private static final Logger logger = LoggerFactory.getLogger(RepositoryServiceImpl.class);
    
    // Pattern for valid GitHub repository URLs: https://github.com/{owner}/{repo}
    private static final Pattern GITHUB_URL_PATTERN = 
        Pattern.compile("^https://github\\.com/([a-zA-Z0-9_-]+)/([a-zA-Z0-9_.-]+?)(?:\\.git)?$");
    
    // Maximum repository size in bytes (10MB)
    private static final long MAX_REPOSITORY_SIZE_BYTES = 10485760L;
    
    // Map of file extensions to programming languages
    private static final Map<String, String> EXTENSION_TO_LANGUAGE = Map.ofEntries(
        Map.entry(".java", "Java"),
        Map.entry(".py", "Python"),
        Map.entry(".js", "JavaScript"),
        Map.entry(".ts", "TypeScript"),
        Map.entry(".jsx", "JavaScript"),
        Map.entry(".tsx", "TypeScript"),
        Map.entry(".go", "Go"),
        Map.entry(".rs", "Rust"),
        Map.entry(".cpp", "C++"),
        Map.entry(".c", "C"),
        Map.entry(".h", "C/C++"),
        Map.entry(".cs", "C#"),
        Map.entry(".rb", "Ruby"),
        Map.entry(".php", "PHP"),
        Map.entry(".swift", "Swift"),
        Map.entry(".kt", "Kotlin"),
        Map.entry(".scala", "Scala"),
        Map.entry(".sh", "Shell"),
        Map.entry(".sql", "SQL"),
        Map.entry(".html", "HTML"),
        Map.entry(".css", "CSS"),
        Map.entry(".json", "JSON"),
        Map.entry(".xml", "XML"),
        Map.entry(".yaml", "YAML"),
        Map.entry(".yml", "YAML"),
        Map.entry(".md", "Markdown")
    );
    
    @Value("${repository.clone.base-path}")
    private String cloneBasePath;
    
    @Value("${repository.max-size-mb}")
    private int maxSizeMb;
    
    private final WebClient webClient;
    
    public RepositoryServiceImpl() {
        this.webClient = WebClient.builder()
            .baseUrl("https://api.github.com")
            .build();
    }
    
    @Override
    public ValidationResult validateRepositoryUrl(String url) {
        logger.debug("Validating repository URL: {}", url);
        
        // Check for null or empty URL
        if (url == null || url.trim().isEmpty()) {
            return ValidationResult.failure("Repository URL cannot be null or empty");
        }
        
        // Trim whitespace
        url = url.trim();
        
        // Check URL pattern
        if (!GITHUB_URL_PATTERN.matcher(url).matches()) {
            return ValidationResult.failure(
                "Invalid GitHub repository URL format. Expected: https://github.com/{owner}/{repo}"
            );
        }
        
        logger.debug("Repository URL validation successful: {}", url);
        return ValidationResult.success();
    }
    
    @Override
    public long getRepositorySize(String url) throws GitAPIException {
        logger.debug("Checking repository size for: {}", url);
        
        try {
            // Extract owner and repo from URL
            Matcher matcher = GITHUB_URL_PATTERN.matcher(url);
            if (!matcher.matches()) {
                throw new GitAPIException("Invalid GitHub URL format") {};
            }
            
            String owner = matcher.group(1);
            String repo = matcher.group(2);
            
            // Call GitHub API to get repository information
            String apiPath = String.format("/repos/%s/%s", owner, repo);
            logger.debug("Calling GitHub API: {}", apiPath);
            
            Map<String, Object> response = webClient.get()
                .uri(apiPath)
                .retrieve()
                .bodyToMono(Map.class)
                .block();
            
            if (response == null || !response.containsKey("size")) {
                logger.error("Invalid response from GitHub API for repository: {}", url);
                throw new GitAPIException("Failed to retrieve repository size") {};
            }
            
            // GitHub API returns size in KB, convert to bytes
            Object sizeObj = response.get("size");
            long sizeInKb = sizeObj instanceof Number ? ((Number) sizeObj).longValue() : 0;
            long sizeInBytes = sizeInKb * 1024;
            
            logger.debug("Repository size: {} KB ({} bytes)", sizeInKb, sizeInBytes);
            
            // Check if repository exceeds maximum size
            if (sizeInBytes > MAX_REPOSITORY_SIZE_BYTES) {
                logger.warn("Repository size {} bytes exceeds maximum allowed size {} bytes", 
                    sizeInBytes, MAX_REPOSITORY_SIZE_BYTES);
                throw new GitAPIException(
                    String.format("Repository size (%.2f MB) exceeds maximum allowed size (%d MB)", 
                        sizeInBytes / 1024.0 / 1024.0, maxSizeMb)
                ) {};
            }
            
            return sizeInBytes;
            
        } catch (Exception e) {
            logger.error("Error checking repository size for {}: {}", url, e.getMessage());
            if (e instanceof GitAPIException) {
                throw (GitAPIException) e;
            }
            throw new GitAPIException("Failed to check repository size: " + e.getMessage()) {};
        }
    }
    
    @Override
    public Path cloneRepository(String url) throws GitAPIException {
        logger.debug("Cloning repository: {}", url);
        
        Path clonePath = null;
        try {
            // Extract repository name from URL
            Matcher matcher = GITHUB_URL_PATTERN.matcher(url);
            if (!matcher.matches()) {
                throw new GitAPIException("Invalid GitHub URL format") {};
            }
            
            String repo = matcher.group(2);
            
            // Generate unique directory name using repository name and timestamp
            String uniqueDirName = String.format("%s_%d", repo, System.currentTimeMillis());
            clonePath = Paths.get(cloneBasePath, uniqueDirName);
            
            // Create base directory if it doesn't exist
            File baseDir = new File(cloneBasePath);
            if (!baseDir.exists()) {
                logger.debug("Creating base directory: {}", cloneBasePath);
                baseDir.mkdirs();
            }
            
            logger.debug("Cloning to directory: {}", clonePath);
            
            // Clone the repository using JGit
            Git git = Git.cloneRepository()
                .setURI(url)
                .setDirectory(clonePath.toFile())
                .call();
            
            git.close();
            
            logger.debug("Repository cloned successfully to: {}", clonePath);
            return clonePath;
            
        } catch (Exception e) {
            logger.error("Error cloning repository {}: {}", url, e.getMessage());
            
            // Clean up partial clone on failure
            if (clonePath != null && Files.exists(clonePath)) {
                logger.debug("Cleaning up partial clone at: {}", clonePath);
                try {
                    cleanupRepository(clonePath);
                } catch (Exception cleanupEx) {
                    logger.warn("Failed to cleanup partial clone: {}", cleanupEx.getMessage());
                }
            }
            
            if (e instanceof GitAPIException) {
                throw (GitAPIException) e;
            }
            throw new GitAPIException("Failed to clone repository: " + e.getMessage()) {};
        }
    }
    
    @Override
    public List<CodeFile> detectCodeFiles(Path repoPath) {
        logger.debug("Detecting code files in: {}", repoPath);
        
        if (repoPath == null || !Files.exists(repoPath)) {
            logger.warn("Repository path does not exist: {}", repoPath);
            return Collections.emptyList();
        }
        
        List<CodeFile> codeFiles = new ArrayList<>();
        
        try (Stream<Path> paths = Files.walk(repoPath)) {
            codeFiles = paths
                .filter(Files::isRegularFile)
                .filter(path -> {
                    // Skip .git directory
                    return !path.toString().contains(File.separator + ".git" + File.separator);
                })
                .map(path -> {
                    String fileName = path.getFileName().toString();
                    String extension = getFileExtension(fileName);
                    
                    if (extension != null && EXTENSION_TO_LANGUAGE.containsKey(extension)) {
                        String language = EXTENSION_TO_LANGUAGE.get(extension);
                        String relativePath = repoPath.relativize(path).toString();
                        return new CodeFile(relativePath, language);
                    }
                    return null;
                })
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
            
            logger.debug("Detected {} code files", codeFiles.size());
            
            if (codeFiles.isEmpty()) {
                logger.warn("No code files detected in repository: {}", repoPath);
            }
            
        } catch (IOException e) {
            logger.error("Error detecting code files in {}: {}", repoPath, e.getMessage());
            return Collections.emptyList();
        }
        
        return codeFiles;
    }
    
    /**
     * Extracts the file extension from a filename.
     * 
     * @param fileName the filename
     * @return the extension including the dot (e.g., ".java"), or null if no extension
     */
    private String getFileExtension(String fileName) {
        if (fileName == null || fileName.isEmpty()) {
            return null;
        }
        
        int lastDotIndex = fileName.lastIndexOf('.');
        if (lastDotIndex > 0 && lastDotIndex < fileName.length() - 1) {
            return fileName.substring(lastDotIndex).toLowerCase();
        }
        
        return null;
    }
    
    @Override
    public void cleanupRepository(Path repoPath) {
        logger.debug("Cleaning up repository at: {}", repoPath);
        
        if (repoPath == null || !Files.exists(repoPath)) {
            logger.debug("Repository path does not exist, nothing to cleanup: {}", repoPath);
            return;
        }
        
        try {
            deleteDirectory(repoPath.toFile());
            logger.debug("Repository cleanup successful: {}", repoPath);
        } catch (IOException e) {
            logger.error("Error cleaning up repository at {}: {}", repoPath, e.getMessage());
            throw new RuntimeException("Failed to cleanup repository: " + e.getMessage(), e);
        }
    }
    
    /**
     * Recursively deletes a directory and all its contents.
     * 
     * @param directory the directory to delete
     * @throws IOException if deletion fails
     */
    private void deleteDirectory(File directory) throws IOException {
        if (!directory.exists()) {
            return;
        }
        
        if (directory.isDirectory()) {
            File[] files = directory.listFiles();
            if (files != null) {
                for (File file : files) {
                    deleteDirectory(file);
                }
            }
        }
        
        if (!directory.delete()) {
            throw new IOException("Failed to delete: " + directory.getAbsolutePath());
        }
    }
}
