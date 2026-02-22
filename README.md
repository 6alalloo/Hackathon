# CodeWiki Generator

AI-powered wiki generator for GitHub repositories using HuggingFace LLMs.

## Overview

The CodeWiki Generator automatically generates comprehensive, wiki-style documentation for public GitHub repositories. It analyzes codebases and creates structured documentation including overviews, architecture breakdowns, and file-by-file explanations.

## Features

- Repository validation and cloning
- AI-powered documentation generation using HuggingFace API
- Full-text search across all generated wikis
- Interactive RAG-based chatbot for repository Q&A
- Repository update monitoring
- Rate limiting and concurrent request management
- Single Docker container deployment

## Technology Stack

**Backend:**
- Java 17
- Spring Boot 3.2.0
- Spring Data JPA
- H2 Database (file-based)
- JGit for Git operations
- WebFlux for HuggingFace API integration

**Frontend:**
- React 18
- Tailwind CSS
- React Router
- Axios for API calls

**Testing:**
- JUnit 5
- jqwik for property-based testing

## Prerequisites

- Java 17 or higher
- Maven 3.6 or higher
- Node.js 20.x (for frontend development)
- HuggingFace API key

## Building the Project

```bash
# Build both backend and frontend
mvn clean package

# The frontend will be automatically built and embedded in the Spring Boot JAR
```

## Running Locally

```bash
# Set HuggingFace API key
export HUGGINGFACE_API_KEY=your_api_key_here

# Run the application
mvn spring-boot:run

# Or run the JAR
java -jar target/codewiki-generator-1.0.0.jar
```

The application will be available at `http://localhost:8080`

## Running with Docker

```bash
# Build Docker image
docker build -t codewiki-generator .

# Run container
docker run -p 8080:8080 \
  -e HUGGINGFACE_API_KEY=your_api_key_here \
  -v codewiki-data:/app/data \
  codewiki-generator
```

## Development

### Backend Development

```bash
# Run Spring Boot application
mvn spring-boot:run
```

### Frontend Development

```bash
# Navigate to frontend directory
cd frontend

# Install dependencies
npm install

# Start development server
npm start
```

The React dev server will run on `http://localhost:3000` and proxy API requests to the Spring Boot backend.

## Configuration

Key configuration properties in `application.properties`:

- `server.port`: Application port (default: 8080)
- `spring.datasource.url`: H2 database file location
- `huggingface.api.key`: HuggingFace API key
- `repository.max-size-mb`: Maximum repository size (default: 10MB)
- `rate-limit.concurrent-generations`: Max concurrent wiki generations (default: 10)
- `rate-limit.llm-requests-per-minute`: Max LLM API requests per minute (default: 100)

## Project Structure

```
codewiki-generator/
├── src/main/java/com/codewiki/
│   ├── controller/       # REST API controllers
│   ├── service/          # Business logic services
│   ├── repository/       # JPA repositories
│   ├── model/            # JPA entities
│   ├── client/           # External API clients
│   └── CodeWikiApplication.java
├── src/main/resources/
│   └── application.properties
├── frontend/
│   ├── src/
│   │   ├── components/   # React components
│   │   ├── App.js
│   │   └── index.js
│   ├── public/
│   └── package.json
└── pom.xml
```

## Testing

```bash
# Run all tests
mvn test

# Run specific test class
mvn test -Dtest=RepositoryServiceTest

# Run property-based tests
mvn test -Dtest=*PropertyTest
```

## License

MIT License
