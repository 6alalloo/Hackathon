package com.codewiki.service;

import com.codewiki.dto.ErrorCode;
import com.codewiki.exception.CloningException;
import com.codewiki.exception.ValidationException;
import com.codewiki.model.CodeFile;
import com.codewiki.model.ValidationResult;
import com.codewiki.util.LoggingContext;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
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

    @Autowired
    public RepositoryServiceImpl(@Qualifier("githubWebClient") WebClient webClient) {
        this.webClient = webClient;
    }

    // Fallback constructor for isolated unit tests that instantiate the service directly.
    public RepositoryServiceImpl() {
        this(WebClient.builder().baseUrl("https://api.github.com").build());
    }
    
    @Override
    public ValidationResult validateRepositoryUrl(String url) {
        logger.debug("Validating repository URL: {}", url);
        
        // Check for null or empty URL
        if (url == null || url.strip().isEmpty()) {
            return ValidationResult.failure("Repository URL cannot be null or empty");
        }
        
        // Trim whitespace
        url = url.strip();
        
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
        LoggingContext.setRepositoryUrl(url);
        
        try {
            // Extract owner and repo from URL
            Matcher matcher = GITHUB_URL_PATTERN.matcher(url);
            if (!matcher.matches()) {
                throw new ValidationException(
                    ErrorCode.INVALID_REPOSITORY_URL,
                    "Invalid GitHub URL format"
                );
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
                throw new CloningException(
                    ErrorCode.REPOSITORY_NOT_FOUND,
                    "Repository not found or inaccessible",
                    List.of("Verify the repository URL is correct", "Ensure the repository is public")
                );
            }
            
            // GitHub API returns size in KB, convert to bytes
            Object sizeObj = response.get("size");
            long sizeInKb = sizeObj instanceof Number ? ((Number) sizeObj).longValue() : 0;
            long sizeInBytes = sizeInKb * 1024;
            
            logger.debug("Repository size: {} KB ({} bytes)", sizeInKb, sizeInBytes);
            
            // Check if repository exceeds maximum size
            long maxRepositorySizeBytes = maxRepositorySizeBytes();
            if (sizeInBytes > maxRepositorySizeBytes) {
                logger.warn("Repository size {} bytes exceeds maximum allowed size {} bytes", 
                    sizeInBytes, maxRepositorySizeBytes);
                throw new ValidationException(
                    ErrorCode.REPOSITORY_TOO_LARGE,
                    String.format("Repository size (%.2f MB) exceeds maximum allowed size (%d MB)", 
                        sizeInBytes / 1024.0 / 1024.0, maxSizeMb),
                    List.of("Try a smaller repository", "Contact support if you need to process larger repositories")
                );
            }
            
            return sizeInBytes;
            
        } catch (ValidationException | CloningException e) {
            throw e;
        } catch (Exception e) {
            logger.error("Error checking repository size for {}: {}", url, e.getMessage());
            throw new CloningException(
                ErrorCode.CLONING_FAILED,
                "Failed to check repository size: " + e.getMessage(),
                List.of("Check your internet connection", "Verify the repository URL is correct")
            );
        }
    }
    
    @Override
    public Path cloneRepository(String url) throws GitAPIException {
        logger.debug("Cloning repository: {}", url);
        LoggingContext.setRepositoryUrl(url);
        
        // Retry logic: attempt once, then retry after 2 seconds if it fails
        int maxAttempts = 2;
        long retryDelayMs = 2000;
        
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            Path clonePath = null;
            try {
                // Extract repository name from URL
                Matcher matcher = GITHUB_URL_PATTERN.matcher(url);
                if (!matcher.matches()) {
                    throw new ValidationException(
                        ErrorCode.INVALID_REPOSITORY_URL,
                        "Invalid GitHub URL format"
                    );
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
                
                logger.debug("Cloning to directory: {} (attempt {}/{})", clonePath, attempt, maxAttempts);
                
                // Clone the repository using JGit
                Git git = Git.cloneRepository()
                    .setURI(url)
                    .setDirectory(clonePath.toFile())
                    .call();
                
                git.close();
                
                logger.info("Repository cloned successfully to: {}", clonePath);
                return clonePath;
                
            } catch (ValidationException e) {
                // Don't retry validation errors
                throw e;
            } catch (Exception e) {
                logger.warn("Error cloning repository {} (attempt {}/{}): {}", 
                    url, attempt, maxAttempts, e.getMessage());
                
                // Clean up partial clone on failure
                if (clonePath != null && Files.exists(clonePath)) {
                    logger.debug("Cleaning up partial clone at: {}", clonePath);
                    try {
                        cleanupRepository(clonePath);
                    } catch (Exception cleanupEx) {
                        logger.warn("Failed to cleanup partial clone: {}", cleanupEx.getMessage());
                    }
                }
                
                // If this was the last attempt, throw exception
                if (attempt == maxAttempts) {
                    logger.error("Failed to clone repository after {} attempts: {}", maxAttempts, url);
                    throw new CloningException(
                        ErrorCode.CLONING_FAILED,
                        "Failed to clone repository after " + maxAttempts + " attempts: " + e.getMessage(),
                        List.of(
                            "Check your internet connection",
                            "Verify the repository URL is correct and the repository is public",
                            "Try again later"
                        )
                    );
                }
                
                // Wait before retrying
                try {
                    logger.debug("Waiting {} ms before retry", retryDelayMs);
                    Thread.sleep(retryDelayMs);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new CloningException(
                        ErrorCode.CLONING_FAILED,
                        "Cloning interrupted",
                        List.of("Try again")
                    );
                }
            }
        }
        
        // Should never reach here
        throw new CloningException(
            ErrorCode.CLONING_FAILED,
            "Failed to clone repository",
            List.of("Try again later")
        );
    }
    
    @Override
    public List<CodeFile> detectCodeFiles(Path repoPath) {
        logger.debug("Detecting code files in: {}", repoPath);
        
        if (repoPath == null || !Files.exists(repoPath)) {
            logger.warn("Repository path does not exist: {}", repoPath);
            throw new ValidationException(
                ErrorCode.NO_CODE_FILES,
                "Repository path does not exist"
            );
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
                        String relativePath = repoPath.relativize(path).toString()
                                .replace(File.separatorChar, '/');
                        return new CodeFile(relativePath, language);
                    }
                    return null;
                })
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
            
            logger.debug("Detected {} code files", codeFiles.size());
            
            if (codeFiles.isEmpty()) {
                logger.warn("No code files detected in repository: {}", repoPath);
                throw new ValidationException(
                    ErrorCode.NO_CODE_FILES,
                    "No code files detected in repository",
                    List.of(
                        "Ensure the repository contains source code files",
                        "Supported languages: Java, Python, JavaScript, TypeScript, and more"
                    )
                );
            }
            
        } catch (ValidationException e) {
            throw e;
        } catch (IOException e) {
            logger.error("Error detecting code files in {}: {}", repoPath, e.getMessage());
            throw new ValidationException(
                ErrorCode.NO_CODE_FILES,
                "Failed to scan repository for code files: " + e.getMessage()
            );
        }
        
        return codeFiles;
    }

    private long maxRepositorySizeBytes() {
        return maxSizeMb * 1024L * 1024L;
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
            logger.info("Repository cleanup successful: {}", repoPath);
        } catch (IOException e) {
            logger.error("Error cleaning up repository at {}: {}", repoPath, e.getMessage(), e);
            // Don't throw exception - cleanup failures shouldn't break the system
            // Log the error and continue operating
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
