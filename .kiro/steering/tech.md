# Technology Stack

## Backend

- Java 17
- Spring Boot 3.2.0
- Spring Data JPA with H2 Database (file-based)
- Spring WebFlux for async HTTP client
- JGit 6.8.0 for Git operations
- Lombok for reducing boilerplate
- Jackson for JSON processing

## Frontend

- React 18
- React Router 6
- Tailwind CSS 3
- Axios for API calls
- React Markdown with syntax highlighting

## Testing

- JUnit 5 for unit tests
- jqwik 1.8.2 for property-based testing
- Spring Boot Test for integration tests

## Build System

Maven 3.6+ with frontend-maven-plugin for integrated React builds.

## Common Commands

```bash
# Build entire project (backend + frontend)
mvn clean package

# Run application locally
mvn spring-boot:run

# Run all tests
mvn test

# Run specific test class
mvn test -Dtest=ClassName

# Run property-based tests only
mvn test -Dtest=*PropertyTest

# Frontend development (in frontend/ directory)
npm install
npm start  # Dev server on port 3000

# Docker deployment
docker build -t codewiki-generator .
docker run -p 8080:8080 -e HUGGINGFACE_API_KEY=key codewiki-generator
```

## Configuration

Environment variables and application.properties control:
- HuggingFace API key (required): `HUGGINGFACE_API_KEY`
- Server port: `server.port` (default 8080)
- Database location: `spring.datasource.url`
- Repository size limit: `repository.max-size-mb`
- Rate limits: `rate-limit.concurrent-generations`, `rate-limit.llm-requests-per-minute`

## External Dependencies

- HuggingFace API for LLM inference
- GitHub for repository cloning (public repos only)
