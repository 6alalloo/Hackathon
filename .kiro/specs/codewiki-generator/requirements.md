# Requirements Document

## Introduction

The CodeWiki Generator is a hackathon project that automatically generates comprehensive, wiki-style documentation for public GitHub repositories using AI. The system accepts GitHub repository URLs, clones the repository, analyzes the codebase, and generates structured documentation that helps developers quickly understand unfamiliar projects. The generated wikis are persisted for reuse, searchable across all repositories, and include an interactive chatbot for answering repository-specific questions.

## Glossary

- **CodeWiki_System**: The complete application including backend, frontend, database, and LLM integration
- **Repository_Validator**: Component that validates GitHub repository URLs and size constraints
- **Repository_Cloner**: Component that clones GitHub repositories to local storage
- **Wiki_Generator**: Component that generates documentation from repository code using LLM
- **Wiki_Database**: H2 embedded database storing generated wiki content
- **Search_Engine**: Component that searches across all generated wikis
- **Chatbot**: Interactive AI assistant that answers questions about repositories
- **LLM_Client**: Client for HuggingFace Inference API
- **Repository_Monitor**: Component that detects updates to GitHub repositories
- **Rate_Limiter**: Component that prevents system overload
- **Wiki_Content**: Generated documentation including overview, architecture, and file explanations
- **Repository_URL**: Valid public GitHub repository URL
- **Size_Limit**: Maximum repository size of 10MB
- **Wiki_Section**: Individual part of wiki (overview, component, file explanation)
- **Hyperlink**: Clickable link in chatbot responses pointing to wiki sections

## Requirements

### Requirement 1: Repository URL Submission

**User Story:** As a developer, I want to submit a public GitHub repository URL through the UI, so that I can generate documentation for that repository.

#### Acceptance Criteria

1. THE CodeWiki_System SHALL provide a web interface for submitting Repository_URLs
2. WHEN a Repository_URL is submitted, THE Repository_Validator SHALL verify the URL points to a valid public GitHub repository
3. IF the Repository_URL is invalid, THEN THE CodeWiki_System SHALL display an error message describing the validation failure
4. WHEN a valid Repository_URL is submitted, THE CodeWiki_System SHALL initiate the wiki generation process

### Requirement 2: Repository Size Validation

**User Story:** As a system administrator, I want to enforce repository size limits, so that the system remains performant and manageable.

#### Acceptance Criteria

1. WHEN a Repository_URL is validated, THE Repository_Validator SHALL check the repository size before cloning
2. IF the repository size exceeds the Size_Limit, THEN THE CodeWiki_System SHALL reject the request with an error message indicating the size constraint
3. WHEN the repository size is within the Size_Limit, THE Repository_Validator SHALL approve the repository for cloning

### Requirement 3: Repository Cloning

**User Story:** As a developer, I want the system to clone my submitted repository, so that it can analyze the codebase.

#### Acceptance Criteria

1. WHEN a repository passes validation, THE Repository_Cloner SHALL clone the repository to local storage
2. IF the cloning operation fails, THEN THE CodeWiki_System SHALL log the error and notify the user of the failure
3. WHEN cloning completes successfully, THE CodeWiki_System SHALL proceed to wiki generation

### Requirement 4: Code File Detection

**User Story:** As a developer, I want the system to verify that repositories contain code files, so that I don't waste time on empty or non-code repositories.

#### Acceptance Criteria

1. WHEN a repository is cloned, THE Repository_Validator SHALL scan for code files
2. IF no code files are detected, THEN THE CodeWiki_System SHALL reject the repository with an error message
3. WHEN code files are detected, THE CodeWiki_System SHALL proceed with wiki generation

### Requirement 5: Wiki Content Generation

**User Story:** As a developer, I want the system to generate comprehensive documentation, so that I can quickly understand the repository structure and purpose.

#### Acceptance Criteria

1. WHEN a repository contains code files, THE Wiki_Generator SHALL generate a high-level overview of the repository
2. THE Wiki_Generator SHALL generate a component and architecture breakdown
3. THE Wiki_Generator SHALL generate file-by-file explanations organized by directory structure
4. THE Wiki_Generator SHALL generate explanations of how components interact with each other
5. THE Wiki_Generator SHALL support multiple programming languages in a single repository

### Requirement 6: LLM Integration

**User Story:** As a system operator, I want the system to use HuggingFace Inference API for content generation, so that we can leverage powerful language models without hosting infrastructure.

#### Acceptance Criteria

1. THE LLM_Client SHALL connect to HuggingFace Inference API using Qwen2.5-Coder-32B-Instruct or DeepSeek-Coder-V2 models
2. WHEN the Wiki_Generator requests content generation, THE LLM_Client SHALL send repository code and context to the API
3. IF the LLM API request fails, THEN THE CodeWiki_System SHALL retry up to 3 times with exponential backoff
4. IF all retry attempts fail, THEN THE CodeWiki_System SHALL log the error and notify the user of the generation failure

### Requirement 7: Wiki Persistence

**User Story:** As a developer, I want generated wikis to be saved, so that I can access them later without regenerating.

#### Acceptance Criteria

1. WHEN wiki generation completes successfully, THE CodeWiki_System SHALL store the Wiki_Content in the Wiki_Database
2. THE Wiki_Database SHALL associate Wiki_Content with the Repository_URL
3. WHEN a user requests a wiki for a previously processed repository, THE CodeWiki_System SHALL retrieve the Wiki_Content from the Wiki_Database
4. THE CodeWiki_System SHALL display cached Wiki_Content without regenerating

### Requirement 8: Wiki Display

**User Story:** As a developer, I want to view generated wikis in an organized format, so that I can easily navigate and understand the documentation.

#### Acceptance Criteria

1. THE CodeWiki_System SHALL display Wiki_Content with the following sections: overview, architecture breakdown, and file explanations
2. THE CodeWiki_System SHALL organize file explanations by directory structure
3. THE CodeWiki_System SHALL provide navigation between Wiki_Sections
4. THE CodeWiki_System SHALL render code snippets with syntax highlighting

### Requirement 9: Cross-Wiki Search

**User Story:** As a developer, I want to search across all generated wikis, so that I can find relevant information across multiple repositories.

#### Acceptance Criteria

1. THE CodeWiki_System SHALL provide a search interface accessible from any page
2. WHEN a user submits a search query, THE Search_Engine SHALL search across all Wiki_Content in the Wiki_Database
3. THE Search_Engine SHALL return results ranked by relevance
4. THE CodeWiki_System SHALL display search results with repository name, matching Wiki_Section, and context snippet
5. WHEN a user clicks a search result, THE CodeWiki_System SHALL navigate to the relevant Wiki_Section

### Requirement 10: Interactive Chatbot

**User Story:** As a developer, I want to ask questions about a repository, so that I can get specific answers without reading the entire wiki.

#### Acceptance Criteria

1. WHEN viewing a wiki, THE CodeWiki_System SHALL display a Chatbot interface
2. WHEN a user submits a question, THE Chatbot SHALL use both Wiki_Content and repository files to generate an answer
3. THE Chatbot SHALL include Hyperlinks to relevant Wiki_Sections in responses
4. WHEN a user clicks a Hyperlink, THE CodeWiki_System SHALL navigate to the referenced Wiki_Section
5. THE Chatbot SHALL maintain conversation context for follow-up questions

### Requirement 11: Repository Update Detection

**User Story:** As a developer, I want the system to detect when repositories are updated, so that the wiki stays current with the latest code.

#### Acceptance Criteria

1. THE Repository_Monitor SHALL periodically check GitHub for updates to previously processed repositories
2. WHEN a repository update is detected, THE Repository_Monitor SHALL mark the Wiki_Content as stale
3. WHEN a user requests a wiki marked as stale, THE CodeWiki_System SHALL display a notification about available updates
4. THE CodeWiki_System SHALL provide an option to regenerate the wiki with the latest repository version
5. WHEN regeneration is requested, THE Wiki_Generator SHALL replace the existing Wiki_Content with newly generated content

### Requirement 12: Rate Limiting

**User Story:** As a system administrator, I want to limit request rates, so that the system and LLM API are not overloaded.

#### Acceptance Criteria

1. THE Rate_Limiter SHALL enforce a maximum of 10 concurrent wiki generation requests
2. WHEN the concurrent request limit is reached, THE CodeWiki_System SHALL queue additional requests
3. THE CodeWiki_System SHALL display the queue position to users waiting for generation
4. THE Rate_Limiter SHALL enforce a maximum of 100 LLM API requests per minute
5. IF the LLM API rate limit is reached, THEN THE CodeWiki_System SHALL delay requests until capacity is available

### Requirement 13: Error Handling and Logging

**User Story:** As a system administrator, I want comprehensive error handling and logging, so that I can diagnose and resolve issues quickly.

#### Acceptance Criteria

1. WHEN any error occurs, THE CodeWiki_System SHALL log the error with timestamp, context, and stack trace
2. THE CodeWiki_System SHALL categorize errors as: validation errors, cloning errors, LLM errors, database errors, or system errors
3. WHEN a user-facing error occurs, THE CodeWiki_System SHALL display a user-friendly error message
4. THE CodeWiki_System SHALL provide error recovery suggestions where applicable
5. THE CodeWiki_System SHALL continue operating after handling recoverable errors

### Requirement 14: Docker Deployment

**User Story:** As a system administrator, I want to deploy the application using Docker, so that deployment is consistent and portable.

#### Acceptance Criteria

1. THE CodeWiki_System SHALL package the Java Spring Boot backend and React frontend in a single Docker container
2. THE CodeWiki_System SHALL embed the compiled React build within the Spring Boot application
3. THE CodeWiki_System SHALL use H2 embedded database with persistent storage mounted as a Docker volume
4. WHEN the Docker container starts, THE CodeWiki_System SHALL initialize the Wiki_Database if it does not exist
5. THE CodeWiki_System SHALL expose a single HTTP port for all web traffic

### Requirement 15: Multi-Language Code Support

**User Story:** As a developer, I want the system to handle repositories with multiple programming languages, so that I can document polyglot projects.

#### Acceptance Criteria

1. THE Wiki_Generator SHALL detect all programming languages present in a repository
2. THE Wiki_Generator SHALL generate documentation that identifies the language for each file
3. THE Wiki_Generator SHALL provide language-specific insights in file explanations
4. THE Chatbot SHALL answer questions about code in any supported programming language

### Requirement 16: Public Access

**User Story:** As a user, I want to access the application without authentication, so that I can quickly generate documentation without account setup.

#### Acceptance Criteria

1. THE CodeWiki_System SHALL allow access to all features without requiring user authentication
2. THE CodeWiki_System SHALL allow access to all features without requiring user registration
3. THE CodeWiki_System SHALL display all generated wikis to all users

### Requirement 17: User Interface Responsiveness

**User Story:** As a developer, I want a functional and responsive UI, so that I can efficiently interact with the system.

#### Acceptance Criteria

1. THE CodeWiki_System SHALL provide a responsive web interface using React and Tailwind CSS
2. THE CodeWiki_System SHALL display loading indicators during wiki generation
3. THE CodeWiki_System SHALL display progress updates during long-running operations
4. THE CodeWiki_System SHALL render pages within 2 seconds on standard broadband connections
5. THE CodeWiki_System SHALL support modern web browsers including Chrome, Firefox, Safari, and Edge

### Requirement 18: Generation Status Tracking

**User Story:** As a developer, I want to see the status of wiki generation, so that I know when my documentation is ready.

#### Acceptance Criteria

1. WHEN wiki generation starts, THE CodeWiki_System SHALL display the generation status as "In Progress"
2. THE CodeWiki_System SHALL update the status display with current generation phase
3. WHEN generation completes successfully, THE CodeWiki_System SHALL display the status as "Complete" and show the wiki
4. IF generation fails, THEN THE CodeWiki_System SHALL display the status as "Failed" with error details
5. THE CodeWiki_System SHALL allow users to navigate away and return to check status later

