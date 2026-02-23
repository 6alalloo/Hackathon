# Java Backend Convention Audit Report

## Header
- Project Path: `C:\Users\6alal\Hackathon`
- Audit Date: February 23, 2026
- Scope: Java backend only (`src/main/java`, `src/test/java`, backend build/config files). Frontend feature completeness is out of scope, except backend coupling to frontend build.
- Methodology:
- Static backend structure and code convention inspection.
- Startup/config/boot pattern inspection.
- Persistence and API contract inspection.
- Test code convention and reliability inspection.
- Command validation:
- `./mvnw -q -DskipTests package` (pass).
- `./mvnw -q test` (fail; command output reported `Tests run: 141, Failures: 14, Errors: 26`; per-class surefire totals are `138/14/26`).

## Executive Summary
The backend has broad feature coverage and a generally recognizable Spring layering shape, but there is significant convention and architecture drift that creates high maintenance and reliability risk. The highest-risk areas are:
- Async boot/orchestration pattern (`@Async` usage in controller with self-invocation).
- Duplicate persistence ownership between controller and service.
- Search implementation reliability (H2 full-text function unavailable at runtime/tests).
- Test suite reliability conventions (jqwik lifecycle misuse causing null-initialized fixtures and false negatives).

Overall assessment: **At-risk for convention compliance and correctness under load/regression**, despite successful compile/package.

## Critical Findings

### C1. `@Async` self-invocation inside controller likely runs synchronously
- Impact:
- Request threads can be blocked unexpectedly.
- Generation lifecycle behavior can differ from intended async contract.
- Boot/startup/runtime pattern violates standard Spring async conventions.
- Evidence:
- `generateWikiAsync(...)` is in controller and annotated with `@Async`: `src/main/java/com/codewiki/controller/WikiController.java:245`.
- It is invoked from methods in the same class: `src/main/java/com/codewiki/controller/WikiController.java:103`, `src/main/java/com/codewiki/controller/WikiController.java:185`.
- Self-invocation bypasses proxy interception in Spring AOP async model.
- Recommendation:
- Move generation orchestration into a dedicated service bean (for example `WikiGenerationOrchestratorService`) and invoke async method through bean proxy.
- Keep controllers thin: request validation, delegation, response mapping only.

### C2. Duplicate persistence ownership for `Wiki` across layers
- Impact:
- Conflicting writes and status/state races.
- High risk of duplicate/constraint errors (`repositoryUrl` is unique).
- Violates clear boundary of persistence responsibility.
- Evidence:
- Controller creates/saves initial wiki: `src/main/java/com/codewiki/controller/WikiController.java:92`.
- Controller later calls `wikiGeneratorService.generateWiki(...)`: `src/main/java/com/codewiki/controller/WikiController.java:280`.
- Service creates new `Wiki` object and saves it: `src/main/java/com/codewiki/service/WikiGeneratorServiceImpl.java:184`, `src/main/java/com/codewiki/service/WikiGeneratorServiceImpl.java:226`.
- Unique constraint on repository URL: `src/main/java/com/codewiki/model/Wiki.java:16`.
- Recommendation:
- Use a single persistence owner for wiki aggregate lifecycle.
- `generateWiki` should update existing wiki by ID/context, not create a second aggregate for same repo URL.

### C3. Search depends on unavailable H2 full-text function at runtime/tests
- Impact:
- Search endpoints/services fail at runtime under common test/dev conditions.
- Introduces false confidence because errors are swallowed and converted into empty results.
- Evidence:
- Native SQL uses `FT_SEARCH_DATA`: `src/main/java/com/codewiki/service/SearchServiceImpl.java:95`, `src/main/java/com/codewiki/service/SearchServiceImpl.java:149`.
- Test evidence shows SQL grammar failure/function missing: `target/surefire-reports/com.codewiki.service.SearchServiceTest.txt:4`.
- Recommendation:
- Replace with supported strategy:
- Option A: H2-supported mechanism verified for current H2 version with deterministic init.
- Option B: DB-agnostic `LIKE`/token strategy for MVP.
- Option C: dedicated search component later.
- Do not swallow search backend exceptions silently; surface controlled error state.

### C4. jqwik lifecycle misuse causes null fixture setup in property tests
- Impact:
- Large portion of failing tests are framework setup artifacts, not product defects.
- Regression signal is noisy and unreliable.
- Evidence:
- `@Property` methods with `@BeforeEach` setup patterns:
- `src/test/java/com/codewiki/client/HuggingFaceLLMClientPropertyTest.java:28`
- `src/test/java/com/codewiki/exception/ErrorHandlingPropertyTest.java:44`
- `src/test/java/com/codewiki/service/ChatServicePropertyTest.java:54`
- `src/test/java/com/codewiki/service/SearchServicePropertyTest.java:39`
- Reported NPE symptoms in surefire outputs (for example null `mockServer`, null autowired fields).
- Recommendation:
- Use jqwik lifecycle annotations (`@BeforeTry`, `@AfterTry`, `@BeforeProperty`) where applicable.
- Separate pure property tests from Spring integration tests.
- Keep property tests deterministic and infrastructure-light.

## High Findings

### H1. Controller contains orchestration/business logic and direct repository access
- Impact:
- Layering drift; harder testing and maintenance.
- More duplicated exception handling and state transitions.
- Evidence:
- Field injection of repositories/services: `src/main/java/com/codewiki/controller/WikiController.java:34`.
- Heavy orchestration in endpoint methods: `src/main/java/com/codewiki/controller/WikiController.java:57`.
- Recommendation:
- Move orchestration into service layer; use constructor injection in controllers.

### H2. Generation status lookup uses full-table scan + unsorted "last item"
- Impact:
- Incorrect status can be returned under non-deterministic DB ordering.
- Scales poorly as status rows grow.
- Evidence:
- Scan with `findAll().stream()`: `src/main/java/com/codewiki/controller/WikiController.java:218`.
- Picks `statuses.get(statuses.size() - 1)`: `src/main/java/com/codewiki/controller/WikiController.java:223`.
- Recommendation:
- Add repository query: `findTopByWikiIdOrderByUpdatedAtDesc(...)` (or equivalent) and index by `wikiId, updatedAt`.

### H3. `repository.max-size-mb` config not used as enforcement source of truth
- Impact:
- Config drift; operational tuning does not work as expected.
- Evidence:
- Hardcoded constant `MAX_REPOSITORY_SIZE_BYTES = 10485760L`: `src/main/java/com/codewiki/service/RepositoryServiceImpl.java:41`.
- Config-injected `maxSizeMb` exists: `src/main/java/com/codewiki/service/RepositoryServiceImpl.java:76`.
- Enforcement compares to constant, not derived config: `src/main/java/com/codewiki/service/RepositoryServiceImpl.java:155`.
- Recommendation:
- Derive bytes from `maxSizeMb` once and enforce from config.

### H4. Scheduler cron property is declared but ignored
- Impact:
- Runtime behavior cannot be configured by properties; violates externalized config convention.
- Evidence:
- Property exists: `src/main/resources/application.properties:43`.
- Hardcoded cron used in annotation: `src/main/java/com/codewiki/service/RepositoryMonitorServiceImpl.java:46`.
- Recommendation:
- Use `@Scheduled(cron = "${repository.monitor.cron}")`.

### H5. Chat history endpoint returns JPA entity directly
- Impact:
- Lazy-loading/serialization risk.
- API leaks persistence model.
- Evidence:
- Returns `ResponseEntity<List<ChatMessage>>`: `src/main/java/com/codewiki/controller/ChatController.java:73`.
- Entity has lazy association: `src/main/java/com/codewiki/model/ChatMessage.java:14`.
- DTO exists but unused: `src/main/java/com/codewiki/dto/ChatMessageDTO.java:10`.
- Recommendation:
- Return DTOs at controller boundary, never entities.

### H6. Lazy collection access outside safe transaction boundary in chat retrieval path
- Impact:
- Runtime/test instability (`LazyInitializationException` observed).
- Evidence:
- `retrieveRelevantSections` is not annotated transactional: `src/main/java/com/codewiki/service/ChatServiceImpl.java:88`.
- Accesses `wiki.getSections()` lazy collection: `src/main/java/com/codewiki/service/ChatServiceImpl.java:99`.
- Observed failure: `target/surefire-reports/com.codewiki.service.ChatServiceTest.txt:4`.
- Recommendation:
- Query sections explicitly via repository or enforce read-only transaction boundaries on retrieval methods.

### H7. Cross-platform path handling is inconsistent
- Impact:
- Windows/Linux portability defects in structure parsing/grouping.
- Evidence:
- Path relativization may produce `\` on Windows: `src/main/java/com/codewiki/service/RepositoryServiceImpl.java:308`.
- Later logic assumes `/` delimiters with `lastIndexOf('/')`: `src/main/java/com/codewiki/service/WikiGeneratorServiceImpl.java:250`, `src/main/java/com/codewiki/service/WikiGeneratorServiceImpl.java:295`.
- Recommendation:
- Normalize separators (`replace('\\', '/')`) at ingestion boundary.

## Medium Findings

### M1. `WebClient` bean is defined but multiple core classes create their own clients
- Impact:
- Inconsistent configuration and reduced testability.
- Evidence:
- Shared bean exists: `src/main/java/com/codewiki/config/AppConfig.java:20`.
- Local construction in services/clients:
- `src/main/java/com/codewiki/service/RepositoryServiceImpl.java:82`
- `src/main/java/com/codewiki/service/RepositoryMonitorServiceImpl.java:36`
- `src/main/java/com/codewiki/client/HuggingFaceLLMClient.java:56`
- Recommendation:
- Centralize client configuration through injected beans/builders.

### M2. Global exception handling exists but controllers still catch broadly
- Impact:
- Inconsistent error payloads and duplicated handling logic.
- Evidence:
- Global handler present: `src/main/java/com/codewiki/exception/GlobalExceptionHandler.java:22`.
- Broad catches in controllers:
- `src/main/java/com/codewiki/controller/ChatController.java:54`
- `src/main/java/com/codewiki/controller/WikiController.java:109`
- Recommendation:
- Let domain exceptions propagate to global handler; keep controller catches minimal/specific.

### M3. Request DTO validation annotations and `@Valid` usage are missing
- Impact:
- Manual/duplicated validation logic and inconsistent responses.
- Evidence:
- Request DTOs have no bean validation constraints: `src/main/java/com/codewiki/dto/WikiSubmissionRequest.java:6`, `src/main/java/com/codewiki/dto/ChatRequest.java:6`.
- Controller request bodies not validated with `@Valid`: `src/main/java/com/codewiki/controller/WikiController.java:57`, `src/main/java/com/codewiki/controller/ChatController.java:42`.
- Recommendation:
- Add jakarta validation annotations and controller `@Valid`.

### M4. Mixed state modeling conventions (enums vs raw strings)
- Impact:
- Increased invalid-state risk and weaker compile-time guarantees.
- Evidence:
- Enum-based wiki status: `src/main/java/com/codewiki/model/WikiStatus.java:3`.
- String-based generation status/phase fields: `src/main/java/com/codewiki/model/GenerationStatus.java:18`, `src/main/java/com/codewiki/model/GenerationStatus.java:21`.
- Recommendation:
- Convert generation status/phase to enums.

### M5. Default application settings are development-leaning for shared runtime profile
- Impact:
- Operational and security posture drift in production-like environments.
- Evidence:
- Schema auto-update: `src/main/resources/application.properties:12`.
- H2 console enabled: `src/main/resources/application.properties:17`.
- DEBUG logging for app package: `src/main/resources/application.properties:22`.
- Recommendation:
- Split profile-specific config (`application-dev`, `application-prod`, `application-test`), harden production defaults.

### M6. Backend build is tightly coupled to frontend build even when frontend is incomplete
- Impact:
- Slower backend iteration and higher CI fragility.
- Evidence:
- Frontend plugin tasks wired into Maven lifecycle:
- `pom.xml:153`, `pom.xml:173`, `pom.xml:183`
- Recommendation:
- Gate frontend build behind profile/property until frontend is ready.

### M7. Documentation drift in `PROJECT.md`
- Impact:
- Misleading project status and planning.
- Evidence:
- Document claims missing components: `PROJECT.md:71`, `PROJECT.md:91`, `PROJECT.md:96`.
- Implementations exist:
- `src/main/java/com/codewiki/service/WikiGeneratorServiceImpl.java:22`
- `src/main/java/com/codewiki/service/SearchServiceImpl.java:24`
- `src/main/java/com/codewiki/service/ChatServiceImpl.java:25`
- Recommendation:
- Align `PROJECT.md` to code reality and mark actual maturity/gaps.

### M8. jqwik state file noise is not ignored
- Impact:
- Working tree noise and accidental commits.
- Evidence:
- `.jqwik-database` modified in git status.
- `.gitignore` does not include `.jqwik-database`.
- Recommendation:
- Add `.jqwik-database` to `.gitignore`.

## Small Findings

### S1. Wildcard imports are common in main backend code
- Impact:
- Style/readability drift and less explicit dependencies.
- Evidence:
- `src/main/java/com/codewiki/controller/WikiController.java:3`
- `src/main/java/com/codewiki/controller/WikiController.java:17`
- `src/main/java/com/codewiki/config/LoggingFilter.java:3`
- Recommendation:
- Use explicit imports in production source.

### S2. Duplicate URL sanitization/context key logic
- Impact:
- Logic duplication and drift risk.
- Evidence:
- Sanitization in filter: `src/main/java/com/codewiki/config/LoggingFilter.java:64`.
- Similar logic in utility: `src/main/java/com/codewiki/util/LoggingContext.java:70`.
- Recommendation:
- Consolidate into one shared utility/path.

### S3. Test portability issue: hardcoded Linux temp path in Windows-oriented repository
- Impact:
- Cross-platform test fragility.
- Evidence:
- `Paths.get("/tmp/test-repo")`: `src/test/java/com/codewiki/controller/WikiControllerIntegrationTest.java:88`.
- Recommendation:
- Use `Files.createTempDirectory(...)` for platform-agnostic paths.

## Recommended Remediation Order

### Phase 1 (Stability-Critical)
- Fix async orchestration architecture (`@Async` in dedicated service bean).
- Resolve duplicate wiki persistence ownership.
- Replace/fix search strategy so queries work reliably on current H2 runtime.
- Unblock test reliability by correcting jqwik lifecycle misuse.

### Phase 2 (Convention and API Integrity)
- Refactor controller layer to delegate orchestration.
- Add deterministic repository query for latest generation status.
- Convert generation status/phase to enums.
- Move chat history response to DTO boundary.
- Add request validation annotations and `@Valid`.

### Phase 3 (Hardening and Hygiene)
- Centralize `WebClient` configuration/injection.
- Externalize scheduler cron usage and split profile configs.
- Normalize path separators for portability.
- Remove wildcard imports.
- Eliminate duplicated sanitization logic.
- Align project documentation and add `.jqwik-database` to ignore list.

## Test Cases and Scenarios to Include in Follow-up Validation
1. Build health:
- `./mvnw -q -DskipTests package` should pass consistently.
2. Regression gate:
- `./mvnw -q test` should converge to zero setup/lifecycle errors.
3. Async behavior:
- `POST /api/wikis` returns immediately while generation runs off request thread.
4. Persistence consistency:
- One `Wiki` row per repository URL across submit/regenerate flow.
5. Search functionality:
- Seeded wiki sections/file explanations are queryable without SQL grammar/function errors.
6. Serialization safety:
- `/api/wikis/{wikiId}/chat/history` returns DTO payload without lazy-loading exceptions.

## Assumptions and Defaults
- Convention baseline: standard Spring Boot layered architecture with constructor injection, DTO API boundaries, externalized config, and deterministic repository queries.
- Scope excludes frontend implementation except backend/frontend coupling in build lifecycle.
- Severity model: `Critical`, `High`, `Medium`, `Small`.
- Report style is prescriptive: each finding includes impact, evidence, and recommended fix direction.

## Appendix

### A. Maven Command Output Summary
- Command: `./mvnw -q -DskipTests package`
- Result: **PASS**
- Command: `./mvnw -q test`
- Result: **FAIL**
- Reported by command: `Tests run: 141, Failures: 14, Errors: 26`
- Per-class surefire `.txt` aggregate: `Tests run: 138, Failures: 14, Errors: 26` (likely minor reporting delta from suite aggregation).

### B. Test Class-by-Class Failure Table

| Test Class | Run | Failures | Errors | Status | Primary Symptom |
|---|---:|---:|---:|---|---|
| `com.codewiki.build.LombokAnnotationProcessingPropertyTest` | 1 | 0 | 0 | Pass | N/A |
| `com.codewiki.build.MavenConfigurationPreservationPropertyTest` | 4 | 0 | 0 | Pass | N/A |
| `com.codewiki.client.HuggingFaceLLMClientPropertyTest` | 5 | 0 | 5 | Fail | `NullPointerException` (`mockServer` null) |
| `com.codewiki.client.HuggingFaceLLMClientTest` | 12 | 0 | 0 | Pass | N/A |
| `com.codewiki.CodeWikiApplicationTests` | 1 | 0 | 0 | Pass | N/A |
| `com.codewiki.controller.WikiControllerIntegrationTest` | 7 | 0 | 0 | Pass | N/A |
| `com.codewiki.controller.WikiControllerPropertyTest` | 6 | 0 | 0 | Pass | N/A |
| `com.codewiki.exception.ErrorHandlingPropertyTest` | 5 | 0 | 5 | Fail | `NullPointerException` (`exceptionHandler` null) |
| `com.codewiki.service.ChatServicePropertyTest` | 5 | 0 | 5 | Fail | `NullPointerException` (autowired repos null) |
| `com.codewiki.service.ChatServiceTest` | 8 | 1 | 1 | Fail | `LazyInitializationException` and hyperlink assertion mismatch |
| `com.codewiki.service.RateLimiterServiceImplTest` | 13 | 0 | 0 | Pass | N/A |
| `com.codewiki.service.RateLimiterServicePropertyTest` | 9 | 0 | 0 | Pass | N/A |
| `com.codewiki.service.RepositoryMonitorServicePropertyTest` | 7 | 0 | 5 | Fail | `NullPointerException` (fixture/service null) |
| `com.codewiki.service.RepositoryMonitorServiceTest` | 14 | 5 | 0 | Fail | Mockito `Wanted but not invoked` |
| `com.codewiki.service.RepositoryServiceImplTest` | 20 | 0 | 0 | Pass | N/A |
| `com.codewiki.service.RepositoryServicePropertyTest` | 4 | 2 | 0 | Fail | URL whitespace/null message assertion mismatches |
| `com.codewiki.service.SearchServicePropertyTest` | 5 | 0 | 5 | Fail | `NullPointerException` (autowired service/repo null) |
| `com.codewiki.service.SearchServiceTest` | 12 | 6 | 0 | Fail | Empty results due to `FT_SEARCH_DATA` SQL error |

### C. Fast Wins Checklist
- [ ] Add `.jqwik-database` to `.gitignore`.
- [ ] Replace chat history entity response with `ChatMessageDTO`.
- [ ] Introduce `GenerationStatusRepository` query for latest status by `wikiId`.
- [ ] Externalize scheduled cron usage via `${repository.monitor.cron}`.
- [ ] Derive repository max size from `repository.max-size-mb` config.
- [ ] Remove wildcard imports in main source.
- [ ] Normalize file path separators before structure grouping.
- [ ] Split dev/prod config defaults (`ddl-auto`, H2 console, log level).
- [ ] Update `PROJECT.md` to match current implementation reality.
