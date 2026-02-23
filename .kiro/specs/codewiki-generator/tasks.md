# Implementation Plan: CodeWiki Generator

## Overview

This implementation plan breaks down the CodeWiki Generator into incremental, testable steps. The approach prioritizes getting core functionality working first (repository ingestion → wiki generation → display), then adds search, chatbot, and polish features. Each task builds on previous work, with checkpoints to validate progress.

The implementation uses Java with Spring Boot for the backend, React with Tailwind CSS for the frontend, H2 for the database, and HuggingFace API for LLM integration.

## Tasks

- [x] 1. Set up project structure and dependencies
  - Create Spring Boot project with Maven/Gradle
  - Add dependencies: Spring Web, Spring Data JPA, H2, JGit, HuggingFace client, jqwik
  - Configure H2 database with persistent file storage
  - Set up React frontend with Tailwind CSS
  - Configure build to embed React in Spring Boot JAR
  - Create basic package structure: controller, service, repository, model, client
  - _Requirements: 14.1, 14.2, 14.3_

- [x] 2. Implement repository validation and cloning
  - [x] 2.1 Create RepositoryService with URL validation
    - Implement validateRepositoryUrl() to check GitHub URL format
    - Validate URL pattern: `https://github.com/{owner}/{repo}`
    - Return ValidationResult with success/failure and error messages
    - _Requirements: 1.2, 1.3_
  
  - [x] 2.2 Write property test for URL validation
    - **Property 1: URL Validation Correctness**
    - **Validates: Requirements 1.2, 1.3**
  
  - [x] 2.3 Implement repository size checking
    - Create getRepositorySize() using GitHub API
    - Check size before cloning operation
    - Reject repositories exceeding 10MB with appropriate error
    - _Requirements: 2.1, 2.2, 2.3_
  
  - [x] 2.4 Write property tests for size validation
    - **Property 3: Size Check Before Clone**
    - **Property 4: Oversized Repository Rejection**
    - **Property 5: Undersized Repository Approval**
    - **Validates: Requirements 2.1, 2.2, 2.3**
  
  - [x] 2.5 Implement repository cloning with JGit
    - Create cloneRepository() to clone to local storage
    - Handle cloning failures with logging and user notification
    - Clean up partial clones on failure
    - _Requirements: 3.1, 3.2_
  
  - [x] 2.6 Write unit tests for cloning edge cases
    - Test network timeout simulation
    - Test invalid repository handling
    - Test cleanup on failure
    - _Requirements: 3.1, 3.2_
  
  - [x] 2.7 Implement code file detection
    - Create detectCodeFiles() to scan for code files by extension
    - Reject repositories with no code files
    - Return list of CodeFile objects with path and language
    - _Requirements: 4.1, 4.2, 4.3_
  
  - [x] 2.8 Write property test for workflow sequence
    - **Property 8: Repository Processing Workflow**
    - **Validates: Requirements 3.3, 4.1, 4.3**

- [ ] 3. Checkpoint - Validate repository processing
  - Ensure all tests pass, ask the user if questions arise.

- [x] 4. Create database entities and repositories
  - [x] 4.1 Create Wiki entity with JPA annotations
    - Define fields: id, repositoryUrl, repositoryName, lastCommitHash, timestamps, stale flag, status
    - Add unique constraint on repositoryUrl
    - Define relationships to WikiSection, FileExplanation, ChatMessage
    - _Requirements: 7.1, 7.2_
  
  - [x] 4.2 Create WikiSection entity
    - Define fields: id, wikiId, sectionType, title, content, orderIndex
    - Use @Lob for content field
    - Define ManyToOne relationship to Wiki
    - _Requirements: 5.1, 5.2, 5.3, 5.4_
  
  - [x] 4.3 Create FileExplanation entity
    - Define fields: id, wikiId, filePath, language, explanation, codeSnippet
    - Use @Lob for explanation and codeSnippet
    - Define ManyToOne relationship to Wiki
    - _Requirements: 5.3, 15.2_
  
  - [x] 4.4 Create ChatMessage entity
    - Define fields: id, wikiId, role, content, createdAt
    - Define ManyToOne relationship to Wiki
    - _Requirements: 10.5_
  
  - [x] 4.5 Create GenerationStatus entity
    - Define fields: id, wikiId, phase, status, errorMessage, updatedAt
    - _Requirements: 18.1, 18.2_
  
  - [x] 4.6 Create Spring Data JPA repositories
    - Create WikiRepository with findByRepositoryUrl()
    - Create WikiSectionRepository
    - Create FileExplanationRepository
    - Create ChatMessageRepository with findByWikiIdOrderByCreatedAt()
    - Create GenerationStatusRepository
    - _Requirements: 7.1, 7.2_

- [x] 5. Implement LLM client with retry logic
  - [x] 5.1 Create HuggingFaceLLMClient
    - Implement generateCompletion() with HTTP client
    - Configure for Qwen2.5-Coder-32B-Instruct model
    - Set temperature=0.3, max_tokens=2048
    - Include repository context in prompts
    - _Requirements: 6.1, 6.2_
  
  - [x] 5.2 Implement retry logic with exponential backoff
    - Create generateWithRetry() with 3 attempts
    - Use delays: 1s, 2s, 4s between retries
    - Log each retry attempt at WARN level
    - Log complete failure at ERROR level with user notification
    - _Requirements: 6.3, 6.4_
  
  - [x] 5.3 Write property tests for LLM retry behavior
    - **Property 13: LLM Retry with Exponential Backoff**
    - **Property 14: LLM Complete Failure Handling**
    - **Validates: Requirements 6.3, 6.4**
  
  - [x] 5.4 Write unit tests for LLM client
    - Test successful API call
    - Test single retry scenario
    - Test complete failure after 3 retries
    - Mock HuggingFace API responses
    - _Requirements: 6.1, 6.2, 6.3, 6.4_

- [-] 6. Implement wiki generation service
  - [x] 6.1 Create WikiGeneratorService with context building
    - Implement buildRepositoryContext() to extract metadata
    - Extract file structure, primary languages, entry points
    - Prepare context for LLM prompts
    - _Requirements: 5.1, 5.2, 5.3, 5.4_
  
  - [x] 6.2 Implement overview generation
    - Create generateOverview() using single LLM call
    - Include system prompt: "You are a technical documentation expert"
    - Generate high-level purpose and key features
    - _Requirements: 5.1_
  
  - [x] 6.3 Implement architecture generation
    - Create generateArchitecture() using single LLM call
    - Generate component breakdown and design patterns
    - _Requirements: 5.2_
  
  - [x] 6.4 Implement file explanation generation with chunking
    - Create generateFileExplanations() with batching logic
    - Chunk files into groups (max 8000 tokens per request)
    - Group related files from same directory
    - Generate explanations for each chunk
    - _Requirements: 5.3, 5.5_
  
  - [x] 6.5 Implement component interaction generation
    - Create generateComponentInteractions() using single LLM call
    - Perform cross-reference analysis
    - _Requirements: 5.4_
  
  - [x] 6.6 Implement wiki assembly and persistence
    - Create assembleWiki() to combine all sections
    - Save Wiki entity with all sections and file explanations
    - Associate with repository URL and commit hash
    - _Requirements: 7.1, 7.2_
  
  - [ ] 6.7 Write property tests for wiki generation
    - **Property 10: Wiki Structure Completeness**
    - **Property 11: Multi-Language Support**
    - **Property 15: Wiki Persistence**
    - **Validates: Requirements 5.1, 5.2, 5.3, 5.4, 5.5, 7.1, 7.2, 15.1, 15.2, 15.3**
  
  - [ ] 6.8 Write unit tests for wiki generation edge cases
    - Test single-file repository
    - Test large repository with 100+ files
    - Test repository with only README
    - Test mixed language repository (Python + JavaScript)
    - _Requirements: 5.1, 5.2, 5.3, 5.4, 5.5_

- [ ] 7. Checkpoint - Validate wiki generation
  - Ensure all tests pass, ask the user if questions arise.

- [x] 8. Implement rate limiting
  - [x] 8.1 Create RateLimiterService with semaphores
    - Implement concurrent generation limiting (10 permits)
    - Use Semaphore for tryAcquireGenerationSlot() and releaseGenerationSlot()
    - _Requirements: 12.1_
  
  - [x] 8.2 Implement LLM request rate limiting
    - Create token bucket for LLM requests (100 requests/minute)
    - Implement tryAcquireLLMSlot() and releaseLLMSlot()
    - Add delay logic when limit reached
    - _Requirements: 12.4, 12.5_
  
  - [x] 8.3 Implement request queueing
    - Create LinkedBlockingQueue for excess requests
    - Track queue position by request ID
    - Implement getQueuePosition()
    - _Requirements: 12.2, 12.3_
  
  - [x] 8.4 Write property tests for rate limiting
    - **Property 31: Concurrent Generation Limit**
    - **Property 32: Queue Position Feedback**
    - **Property 33: LLM API Rate Limiting**
    - **Validates: Requirements 12.1, 12.2, 12.3, 12.4, 12.5**
  
  - [x] 8.5 Write unit tests for rate limiter edge cases
    - Test exactly 10 concurrent requests
    - Test 11th concurrent request (should queue)
    - Test exactly 100 LLM requests in 1 minute
    - Test 101st LLM request (should delay)
    - _Requirements: 12.1, 12.2, 12.4, 12.5_

- [x] 9. Implement REST API controllers
  - [x] 9.1 Create WikiController for wiki management
    - POST /api/wikis - Submit repository URL, initiate generation
    - GET /api/wikis/{wikiId} - Retrieve wiki with all sections
    - POST /api/wikis/{wikiId}/regenerate - Trigger regeneration
    - GET /api/wikis/{wikiId}/status - Poll generation status
    - _Requirements: 1.1, 1.4, 7.3, 7.4, 18.1, 18.2, 18.3, 18.4, 18.5_
  
  - [x] 9.2 Implement wiki retrieval with caching
    - Check if wiki exists in database by repository URL
    - Return cached wiki without regeneration
    - Handle stale wiki notifications
    - _Requirements: 7.3, 7.4, 11.3, 11.4_
  
  - [x] 9.3 Write property tests for wiki API
    - **Property 2: Valid URL Initiates Generation**
    - **Property 16: Wiki Cache Retrieval**
    - **Property 38: Generation Status Tracking**
    - **Property 39: Generation Completion Status**
    - **Property 40: Generation Failure Status**
    - **Property 41: Status Persistence**
    - **Validates: Requirements 1.4, 7.3, 7.4, 18.1, 18.2, 18.3, 18.4, 18.5**
  
  - [x] 9.4 Write integration tests for wiki endpoints
    - Test end-to-end: Submit URL → Generate → Retrieve
    - Test cached retrieval: Submit → Retrieve again (no regeneration)
    - Mock GitHub API and HuggingFace API
    - _Requirements: 1.1, 1.4, 7.3, 7.4_

- [x] 10. Implement error handling and logging
  - [x] 10.1 Create error response DTOs
    - Define ErrorResponse with code, message, category, timestamp, suggestions
    - Create error codes for all error types
    - _Requirements: 13.3, 13.4_
  
  - [x] 10.2 Implement global exception handler
    - Create @ControllerAdvice for centralized error handling
    - Handle ValidationException, CloningException, LLMException, DatabaseException
    - Return appropriate HTTP status codes
    - _Requirements: 13.1, 13.2, 13.3_
  
  - [x] 10.3 Configure structured logging
    - Set up JSON logging format
    - Include request ID, wiki ID, repository URL in log context
    - Configure log levels: DEBUG, INFO, WARN, ERROR
    - Sanitize sensitive information (API keys)
    - _Requirements: 13.1, 13.2_
  
  - [x] 10.4 Implement error recovery logic
    - Add retry for cloning errors (once after 2s delay)
    - Clean up resources on failures
    - Ensure system continues after recoverable errors
    - _Requirements: 13.5_
  
  - [x] 10.5 Write property tests for error handling
    - **Property 7: Clone Failure Handling**
    - **Property 9: Empty Repository Rejection**
    - **Property 34: Error Logging Completeness**
    - **Property 35: User-Friendly Error Messages**
    - **Property 36: System Resilience**
    - **Validates: Requirements 3.2, 4.2, 13.1, 13.2, 13.3, 13.4, 13.5**

- [x] 11. Checkpoint - Validate core backend functionality
  - Ensure all tests pass, ask the user if questions arise.

- [x] 12. Implement search functionality
  - [x] 12.1 Create SearchService with H2 full-text search
    - Configure H2 Lucene integration for full-text indexing
    - Index wiki overview, architecture, and file explanations
    - _Requirements: 9.1, 9.2_
  
  - [x] 12.2 Implement search query execution
    - Create search() method with TF-IDF ranking
    - Boost title matches for better relevance
    - Extract context snippets (150 chars before/after match)
    - Return results with repository name, section reference, snippet
    - _Requirements: 9.2, 9.3, 9.4_
  
  - [x] 12.3 Implement wiki indexing
    - Create indexWiki() to index new wikis
    - Create reindexAll() for bulk reindexing
    - Trigger indexing after wiki generation completes
    - _Requirements: 9.2_
  
  - [x] 12.4 Create SearchController
    - GET /api/search?q={query} - Execute search across all wikis
    - Return SearchResult list with ranking
    - _Requirements: 9.1, 9.2, 9.3, 9.4_
  
  - [x] 12.5 Write property tests for search
    - **Property 18: Search Across All Wikis**
    - **Property 19: Search Result Ranking**
    - **Property 20: Search Result Completeness**
    - **Validates: Requirements 9.2, 9.3, 9.4**
  
  - [x] 12.6 Write unit tests for search edge cases
    - Test empty search query
    - Test query with special characters
    - Test query matching multiple wikis
    - Test query matching no wikis
    - _Requirements: 9.1, 9.2, 9.3_

- [x] 13. Implement chatbot with RAG
  - [x] 13.1 Create ChatService with retrieval logic
    - Implement retrieveRelevantSections() using keyword extraction
    - Search wiki sections using extracted keywords
    - Retrieve top 3 most relevant sections
    - Include related code files if mentioned
    - Limit context to 6000 tokens
    - _Requirements: 10.2_
  
  - [x] 13.2 Implement response generation with context
    - Create generateResponse() with RAG prompt
    - Inject retrieved wiki sections and code files
    - Include last 3 conversation turns for context
    - Instruct LLM to reference wiki sections
    - _Requirements: 10.2, 10.5_
  
  - [x] 13.3 Implement hyperlink injection
    - Parse LLM response for section references
    - Convert patterns like "as explained in [section]" to hyperlinks
    - Format: `[section name](/wiki/{wikiId}/section/{sectionId})`
    - Validate section references exist
    - _Requirements: 10.3, 10.4_
  
  - [x] 13.4 Implement conversation history persistence
    - Save user questions and assistant responses to ChatMessage table
    - Retrieve conversation history by wikiId
    - Order messages by timestamp
    - _Requirements: 10.5_
  
  - [x] 13.5 Create ChatController
    - POST /api/wikis/{wikiId}/chat - Submit question, return answer
    - GET /api/wikis/{wikiId}/chat/history - Retrieve conversation history
    - _Requirements: 10.1, 10.2, 10.3, 10.4, 10.5_
  
  - [x] 13.6 Write property tests for chatbot
    - **Property 22: Chatbot RAG Context**
    - **Property 23: Chatbot Hyperlink Injection**
    - **Property 24: Chatbot Hyperlink Navigation**
    - **Property 25: Chatbot Conversation Context**
    - **Property 26: Chatbot Multi-Language Support**
    - **Validates: Requirements 10.2, 10.3, 10.4, 10.5, 15.4**
  
  - [x] 13.7 Write unit tests for chatbot edge cases
    - Test first question in conversation (no history)
    - Test question referencing previous answer
    - Test question about non-existent code
    - Test question requiring multiple wiki sections
    - Test hyperlink injection edge cases
    - _Requirements: 10.2, 10.3, 10.4, 10.5_

- [ ] 14. Checkpoint - Validate search and chatbot
  - Ensure all tests pass, ask the user if questions arise.

- [x] 15. Implement repository monitoring
  - [x] 15.1 Create RepositoryMonitorService
    - Implement checkForUpdates() as scheduled task (every 24 hours)
    - Use @Scheduled annotation with cron expression
    - _Requirements: 11.1_
  
  - [x] 15.2 Implement update detection logic
    - Create isRepositoryUpdated() to fetch latest commit hash from GitHub
    - Compare remote hash with stored lastCommitHash
    - Mark wiki as stale if hashes differ
    - _Requirements: 11.1, 11.2_
  
  - [x] 15.3 Implement stale wiki handling
    - Create markWikiAsStale() to set stale flag
    - Modify wiki retrieval to check stale flag
    - Return stale notification in API response
    - _Requirements: 11.2, 11.3_
  
  - [x] 15.4 Implement wiki regeneration
    - Modify regenerate endpoint to replace existing wiki content
    - Delete old sections and file explanations
    - Generate new content with latest repository version
    - Update lastCommitHash and clear stale flag
    - _Requirements: 11.4, 11.5_
  
  - [x] 15.5 Write property tests for monitoring
    - **Property 27: Repository Update Detection**
    - **Property 28: Stale Wiki Marking**
    - **Property 29: Stale Wiki Notification**
    - **Property 30: Wiki Regeneration Replacement**
    - **Validates: Requirements 11.1, 11.2, 11.3, 11.4, 11.5**

- [ ] 16. Build React frontend
  - [ ] 16.1 Create repository submission form
    - Build form component with URL input and submit button
    - Add client-side URL validation
    - Display loading indicator during submission
    - Show error messages for validation failures
    - _Requirements: 1.1, 1.2, 1.3, 17.2_
  
  - [ ] 16.2 Create wiki display component
    - Build WikiView component with section navigation
    - Display overview, architecture, and file explanations
    - Organize file explanations by directory structure
    - Implement syntax highlighting for code snippets
    - Add navigation between sections
    - _Requirements: 8.1, 8.2, 8.3, 8.4_
  
  - [ ] 16.3 Create generation status tracker
    - Build StatusTracker component with polling logic
    - Display current phase and overall status
    - Show progress updates during generation
    - Handle status transitions: PENDING → IN_PROGRESS → COMPLETED/FAILED
    - Display error details on failure
    - _Requirements: 18.1, 18.2, 18.3, 18.4, 18.5, 17.2, 17.3_
  
  - [ ] 16.4 Create search interface
    - Build SearchBar component accessible from all pages
    - Display search results with repository name, section, snippet
    - Implement click navigation to wiki sections
    - _Requirements: 9.1, 9.4, 9.5_
  
  - [ ] 16.5 Create chatbot UI
    - Build ChatBot component with message history
    - Display user questions and assistant responses
    - Render hyperlinks in responses
    - Implement click navigation for hyperlinks
    - Add input field for new questions
    - Maintain conversation context
    - _Requirements: 10.1, 10.3, 10.4, 10.5_
  
  - [ ] 16.6 Implement responsive design with Tailwind
    - Apply Tailwind CSS classes for responsive layout
    - Ensure mobile-friendly design
    - Optimize for modern browsers (Chrome, Firefox, Safari, Edge)
    - Target page load within 2 seconds
    - _Requirements: 17.1, 17.4, 17.5_
  
  - [ ] 16.7 Implement stale wiki notification
    - Display notification banner when wiki is stale
    - Add "Regenerate" button to trigger update
    - Show regeneration progress
    - _Requirements: 11.3, 11.4_

- [ ] 17. Integrate frontend with backend
  - [ ] 17.1 Configure API client in React
    - Set up Axios or Fetch for API calls
    - Configure base URL for backend endpoints
    - Add error handling for network failures
    - _Requirements: 1.1, 7.3, 9.1, 10.1_
  
  - [ ] 17.2 Implement API integration for all features
    - Connect submission form to POST /api/wikis
    - Connect wiki display to GET /api/wikis/{wikiId}
    - Connect status tracker to GET /api/wikis/{wikiId}/status
    - Connect search to GET /api/search
    - Connect chatbot to POST /api/wikis/{wikiId}/chat
    - Connect regeneration to POST /api/wikis/{wikiId}/regenerate
    - _Requirements: 1.1, 7.3, 9.1, 10.1, 11.4, 18.1_
  
  - [ ] 17.3 Build React app and embed in Spring Boot
    - Configure Maven/Gradle to build React during compilation
    - Copy React build output to Spring Boot static resources
    - Configure Spring Boot to serve React app
    - Set up routing to handle React Router paths
    - _Requirements: 14.1, 14.2_

- [ ] 18. Checkpoint - Validate full-stack integration
  - Ensure all tests pass, ask the user if questions arise.

- [ ] 19. Configure Docker deployment
  - [ ] 19.1 Create Dockerfile
    - Use multi-stage build: Node for React, Maven/Gradle for Spring Boot
    - Build React app in first stage
    - Build Spring Boot JAR with embedded React in second stage
    - Use appropriate base image (OpenJDK)
    - _Requirements: 14.1, 14.2_
  
  - [ ] 19.2 Configure H2 database persistence
    - Set H2 to file-based mode (not in-memory)
    - Configure database file path
    - Create Docker volume for database persistence
    - Initialize database schema on first startup
    - _Requirements: 14.3, 14.4_
  
  - [ ] 19.3 Configure application properties
    - Set server port (default 8080)
    - Configure H2 database connection
    - Set HuggingFace API key from environment variable
    - Configure logging levels
    - _Requirements: 14.5_
  
  - [ ] 19.4 Create docker-compose.yml
    - Define service for CodeWiki application
    - Map port 8080 to host
    - Mount volume for H2 database
    - Set environment variables for API keys
    - _Requirements: 14.3, 14.5_
  
  - [ ] 19.5 Write integration test for Docker deployment
    - Test container startup
    - Test database initialization
    - Test API accessibility
    - Test volume persistence
    - _Requirements: 14.1, 14.2, 14.3, 14.4, 14.5_

- [ ] 20. Implement public access configuration
  - [ ] 20.1 Configure Spring Security for public access
    - Disable authentication requirements for all endpoints
    - Allow anonymous access to all features
    - Remove any authorization checks
    - _Requirements: 16.1, 16.2_
  
  - [ ] 20.2 Configure CORS for frontend
    - Allow cross-origin requests from React dev server
    - Configure appropriate CORS headers
    - _Requirements: 16.1_
  
  - [ ] 20.3 Write property test for public access
    - **Property 37: Public Access Without Authentication**
    - **Validates: Requirements 16.1, 16.2, 16.3**

- [ ] 21. Performance optimization and testing
  - [ ] 21.1 Write performance tests
    - Test 10 concurrent wiki generations (at limit)
    - Test 20 concurrent wiki generations (queueing)
    - Test 100 LLM requests per minute (at limit)
    - Test 150 LLM requests per minute (throttling)
    - Test maximum size repository (10MB)
    - Test repository with 500+ files
    - Test search across 100+ wikis
    - _Requirements: 12.1, 12.2, 12.4, 12.5, 17.4_
  
  - [ ] 21.2 Write property tests for UI performance
    - **Property 42: Loading Indicators During Generation**
    - **Property 43: Progress Updates for Long Operations**
    - **Property 44: Page Load Performance**
    - **Validates: Requirements 17.2, 17.3, 17.4**

- [ ] 22. Final integration testing
  - [ ] 22.1 Write end-to-end integration tests
    - Test workflow: Submit URL → Validate → Clone → Generate → Display
    - Test workflow: Submit URL → Find cached → Display (no regeneration)
    - Test workflow: Search → Click result → Navigate to section
    - Test workflow: View wiki → Ask question → Click hyperlink → Navigate
    - Test workflow: Detect update → Mark stale → Regenerate → Display new content
    - Mock GitHub API and HuggingFace API with WireMock
    - _Requirements: All requirements_
  
  - [ ] 22.2 Write property test for search navigation
    - **Property 21: Search Result Navigation**
    - **Validates: Requirements 9.5**

- [ ] 23. Final checkpoint - Complete system validation
  - Ensure all tests pass, ask the user if questions arise.

## Notes

- Tasks marked with `*` are optional testing tasks and can be skipped for faster MVP delivery
- Each task references specific requirements for traceability
- Property tests validate universal correctness properties from the design document
- Unit tests validate specific examples and edge cases
- Integration tests validate end-to-end workflows
- Checkpoints ensure incremental validation and provide opportunities for user feedback
- The implementation prioritizes core functionality first: repository processing → wiki generation → display
- Search, chatbot, and monitoring features are added after core functionality is working
- Docker deployment is configured near the end to package the complete application
