# CodeWiki Generator - Project State

Last updated: February 23, 2026

## Purpose
This document consolidates:
- product requirements from `.kiro/specs/codewiki-generator/requirements.md`
- technical design from `.kiro/specs/codewiki-generator/design.md`
- implementation plan from `.kiro/specs/codewiki-generator/tasks.md`
- actual implementation status from the current codebase

It is intended to be the single, reality-checked project snapshot.

## Canonical Specs
- Requirements: `.kiro/specs/codewiki-generator/requirements.md`
- Technical Design: `.kiro/specs/codewiki-generator/design.md`
- Implementation Plan: `.kiro/specs/codewiki-generator/tasks.md`

## Product Definition
CodeWiki Generator is a monolithic Spring Boot + React app that should:
- accept public GitHub repository URLs
- validate, size-check, clone, and analyze codebases
- generate wiki-style documentation using HuggingFace LLMs
- persist generated wikis for caching and reuse
- support cross-wiki search and repo-specific chatbot Q&A (RAG)
- monitor repositories for updates and support regeneration
- run in a single Docker deployment with H2 persistent storage

## Glossary (From Requirements)
- `CodeWiki_System`: full app (backend, frontend, DB, LLM integration)
- `Repository_Validator`: URL + size + code-file validation
- `Repository_Cloner`: clone to local storage
- `Wiki_Generator`: LLM-driven wiki generation
- `Wiki_Database`: H2 persistence for wiki content
- `Search_Engine`: cross-wiki search
- `Chatbot`: repository-aware assistant
- `LLM_Client`: HuggingFace API client
- `Repository_Monitor`: detects upstream repo changes
- `Rate_Limiter`: generation/API throttling
- `Wiki_Content`: overview/architecture/file explanations
- `Repository_URL`: valid public GitHub URL
- `Size_Limit`: 10MB max repository size
- `Wiki_Section`: section unit inside a wiki
- `Hyperlink`: chat response links to wiki sections

## Requirements Catalog (R1-R18)
Status in this section reflects **actual current implementation**, not just task checkboxes.

### R1: Repository URL Submission
- Spec: UI URL submission, validate URL, show validation error, initiate generation.
- Current: Backend submission API is implemented at `POST /api/wikis` with validation and async orchestration kickoff; frontend submission flow is still pending.
- Status: `Partial`

### R2: Repository Size Validation
- Spec: check size pre-clone, reject >10MB, allow <=10MB.
- Current: backend service calls GitHub API and rejects oversized repos.
- Status: `Partial` (logic exists, no end-to-end flow exposed via API/UI)

### R3: Repository Cloning
- Spec: clone after validation; log + notify on failure; proceed to generation on success.
- Current: clone logic + cleanup exists in service and is orchestrated in async generation flow.
- Status: `Partial` (backend implemented; frontend flow pending)

### R4: Code File Detection
- Spec: scan cloned repo; reject no-code repos; continue if code found.
- Current: file detection by extension is implemented and used in generation orchestration.
- Status: `Partial`

### R5: Wiki Content Generation
- Spec: generate overview, architecture, file explanations, interactions, multi-language support.
- Current: `WikiGeneratorService` and implementation exist with overview/architecture/file explanation/interactions generation flow.
- Status: `Partial`

### R6: LLM Integration
- Spec: HuggingFace model integration with retries/backoff and failure handling.
- Current: `LLMClient` and `HuggingFaceLLMClient` exist with retry/backoff behavior.
- Status: `Partial`

### R7: Wiki Persistence
- Spec: store generated wiki, map to repository URL, return cached content.
- Current: entities/repositories and persistence orchestration are implemented, including cache lookup by repository URL.
- Status: `Partial`

### R8: Wiki Display
- Spec: show structured wiki sections with navigation and syntax highlighting.
- Current: frontend is placeholder.
- Status: `Not implemented`

### R9: Cross-Wiki Search
- Spec: searchable across all wiki content with relevance ranking and section navigation.
- Current: backend search controller/service are implemented; frontend search UI is pending.
- Status: `Partial`

### R10: Interactive Chatbot
- Spec: chatbot in wiki view, RAG on wiki/files, section hyperlinks, conversation context.
- Current: backend chat controller/service are implemented with context retrieval and history persistence; frontend chat UI is pending.
- Status: `Partial`

### R11: Repository Update Detection
- Spec: periodic monitoring, stale marking, stale notification, regenerate workflow.
- Current: monitoring service implementation exists with scheduled checks and stale marking.
- Status: `Partial`

### R12: Rate Limiting
- Spec: 10 concurrent generations, queue + position, 100 LLM req/min throttling.
- Current: rate limiter service implementation exists for concurrent generations and LLM request pacing.
- Status: `Partial`

### R13: Error Handling and Logging
- Spec: categorized errors, user-friendly responses, recovery suggestions, resilient operation.
- Current: global error model and exception handler are implemented; additional consistency cleanup is still needed.
- Status: `Partial`

### R14: Docker Deployment
- Spec: single container, embedded frontend build, H2 persistence, startup init, single port.
- Current: `Dockerfile` and `docker-compose.yml` exist; Maven builds React into Spring resources.
- Status: `Partial to Mostly implemented` (runtime behavior not fully validated by integration tests)

### R15: Multi-Language Code Support
- Spec: detect multiple languages, language-specific file explanations, multi-language chat.
- Current: language detection map in repository service exists.
- Status: `Partial` (detection only; generation/chat parts missing)

### R16: Public Access
- Spec: no auth/registration required; all generated wikis accessible.
- Current: no security/auth layer configured; no wiki endpoints exist yet.
- Status: `Partial`

### R17: UI Responsiveness
- Spec: responsive React+Tailwind UI with loading/progress and browser support/performance target.
- Current: scaffold only.
- Status: `Not implemented`

### R18: Generation Status Tracking
- Spec: in-progress phase/status, complete/fail state, persisted and retrievable later.
- Current: generation status entity/repository and status polling endpoint are implemented; consistency hardening is still in progress.
- Status: `Partial`

## Technical Design Summary

### Architecture (Target)
Layered monolith inside one container:
- Frontend: React + Tailwind
- API: REST controllers
- Services: wiki/repo/chat/search/monitor
- Integrations: JGit + HuggingFace API
- Data: H2 + filesystem clones

### Planned Core Services
- `RepositoryService`: URL validation, size check, clone, detect files, cleanup
- `WikiGeneratorService`: build context, generate overview/architecture/files/interactions, assemble wiki
- `LLMClient`: prompt calls + retry/backoff
- `SearchService`: index/search/rank snippets across wikis
- `ChatService`: RAG retrieval + answer generation + section links + history
- `RepositoryMonitorService`: periodic commit checks + stale marking
- `RateLimiter`: generation concurrency and LLM throughput controls

### Data Model (Target)
Primary entities:
- `Wiki`
- `WikiSection`
- `FileExplanation`
- `ChatMessage`
- `GenerationStatus`

Relationships:
- `Wiki` has many sections, file explanations, chat messages

### REST API (Target)
- `POST /api/wikis`
- `GET /api/wikis/{wikiId}`
- `POST /api/wikis/{wikiId}/regenerate`
- `GET /api/wikis/{wikiId}/status`
- `GET /api/search?q=...`
- `POST /api/wikis/{wikiId}/chat`
- `GET /api/wikis/{wikiId}/chat/history`

### Correctness Properties (44)
Design defines 44 properties grouped around:
- URL validation and repo processing workflow
- LLM retry/failure behaviors
- wiki structure and persistence/cache semantics
- search ranking/completeness/navigation
- chatbot RAG/hyperlink/context/multi-language behavior
- monitoring/staleness/regeneration semantics
- rate limiting and queue behavior
- error logging/resilience/public access
- status tracking and UI feedback/performance

Property IDs and names are documented in `.kiro/specs/codewiki-generator/design.md` (Property 1 through Property 44).

## Implementation Plan Snapshot (From tasks.md)

### Marked Complete in Plan
- Task 1: project setup and dependencies
- Task 2: repository validation and cloning subtasks
- Task 4: entities and repositories

### Marked Pending in Plan
- Task 3 checkpoint
- Tasks 5-23 (LLM, wiki generation, rate limiter, controllers, error framework, search, chatbot, monitor, frontend implementation, integration, performance, full validation)

## Actual Codebase Snapshot

### Backend
- Spring Boot app with scheduling enabled:
  - `src/main/java/com/codewiki/CodeWikiApplication.java`
- Config:
  - `src/main/resources/application.properties`
  - `src/main/java/com/codewiki/config/AppConfig.java`
- Services currently implemented include:
  - `RepositoryService` / `RepositoryServiceImpl`
  - `WikiGeneratorService` / `WikiGeneratorServiceImpl`
  - `SearchService` / `SearchServiceImpl`
  - `ChatService` / `ChatServiceImpl`
  - `RepositoryMonitorService` / `RepositoryMonitorServiceImpl`
  - `RateLimiterService` / `RateLimiterServiceImpl`
  - `LLMClient` / `HuggingFaceLLMClient`
- Controllers currently implemented include:
  - `HealthController` (`GET /api/status`)
  - `WikiController` (submission/retrieval/regeneration/status endpoints)
  - `SearchController`
  - `ChatController`

### Persistence Layer
Entities implemented:
- `src/main/java/com/codewiki/model/Wiki.java`
- `src/main/java/com/codewiki/model/WikiSection.java`
- `src/main/java/com/codewiki/model/FileExplanation.java`
- `src/main/java/com/codewiki/model/ChatMessage.java`
- `src/main/java/com/codewiki/model/GenerationStatus.java`
- `src/main/java/com/codewiki/model/WikiStatus.java`
- `src/main/java/com/codewiki/model/SectionType.java`
- `src/main/java/com/codewiki/model/MessageRole.java`

Repositories implemented:
- `src/main/java/com/codewiki/repository/WikiRepository.java`
- `src/main/java/com/codewiki/repository/WikiSectionRepository.java`
- `src/main/java/com/codewiki/repository/FileExplanationRepository.java`
- `src/main/java/com/codewiki/repository/ChatMessageRepository.java`
- `src/main/java/com/codewiki/repository/GenerationStatusRepository.java`

### Frontend
- React app scaffolding:
  - `frontend/src/App.js`
  - `frontend/src/index.js`
  - `frontend/src/index.css`
- Current UI state: placeholder shell only, no functional product flows.

### DevOps/Packaging
- Docker assets exist:
  - `Dockerfile`
  - `docker-compose.yml`
- Maven builds frontend and copies static assets into Spring Boot artifact (`pom.xml`).

### Tests
- Test coverage spans unit tests, integration tests, and property-based tests across:
  - controllers
  - services
  - client integration logic
  - exception handling
  - build/configuration invariants

## Implemented vs Not Implemented

### Implemented
- Project skeleton and dependencies
- H2 + JPA entity/repository schema layer
- Core repository operations (validate/size/clone/detect/cleanup)
- Wiki submission/retrieval/regeneration/status APIs
- LLM client and wiki generation pipeline
- Search service and chat service backend APIs
- Repository monitoring and rate limiting services
- Build + Docker scaffolding
- Broad automated test suite (with current reliability issues under active remediation)

### Not Implemented Yet
- Wiki generation service and orchestration pipeline
- HuggingFace LLM client with retry/backoff and error model
- Wiki management API endpoints
- Status-tracking API and generation workflow
- Search service/controller/indexing/relevance ranking
- Chatbot RAG service/controller/history/hyperlinks
- Repository monitoring/staleness/regeneration flow
- Rate limiter and queueing system
- Centralized error response schema and exception handling
- Functional frontend components and backend integration
- Full property-based test coverage for planned properties
- End-to-end and performance test suites for planned workflows

## Reality Check: Plan vs Code Mismatches
- `tasks.md` marks several Task 2 testing subtasks complete (size/clone/workflow properties), but current tests are primarily URL-validation focused.
- `tasks.md` marks Docker configuration as pending, but `Dockerfile` and `docker-compose.yml` already exist in repository.
- `README.md` describes full feature set (search/chat/monitor/rate-limit), but those features are not yet implemented in code.

## Current Risks and Gaps
- No end-to-end workflow from URL submission to generated wiki exists yet.
- No public API for core product behavior beyond health check.
- No LLM integration means no documentation generation capability today.
- No search/chat/monitoring means key differentiating product features are absent.
- Test coverage does not yet verify most design properties.
- Some configuration is present but unused without corresponding services/controllers.

## Suggested Source-of-Truth Usage
- Use this file (`PROJECT.md`) for current reality.
- Use `requirements.md` for product scope.
- Use `design.md` for architecture and properties.
- Use `tasks.md` for implementation sequencing, but validate checkbox state against code before planning each sprint.
