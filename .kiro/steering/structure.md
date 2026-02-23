# Project Structure

## Architecture Pattern

Standard Spring Boot layered architecture with clear separation of concerns:

```
Controller → Service → Repository → Database
                ↓
            External APIs (HuggingFace, GitHub)
```

## Directory Layout

```
codewiki-generator/
├── src/main/java/com/codewiki/
│   ├── CodeWikiApplication.java    # Spring Boot entry point
│   ├── config/                     # Spring configuration beans
│   ├── controller/                 # REST API endpoints
│   ├── service/                    # Business logic layer
│   ├── repository/                 # JPA repositories (data access)
│   ├── model/                      # JPA entities and domain models
│   └── client/                     # External API clients (HuggingFace)
├── src/main/resources/
│   └── application.properties      # Configuration
├── src/test/java/com/codewiki/     # Mirrors main structure
├── frontend/
│   ├── src/
│   │   ├── components/             # React components
│   │   ├── App.js                  # Main React app
│   │   └── index.js                # React entry point
│   ├── public/
│   └── package.json
├── data/                           # H2 database files (runtime)
├── repos/                          # Cloned repositories (runtime)
└── pom.xml                         # Maven build configuration
```

## Package Organization

- `controller/`: REST endpoints, request/response handling
- `service/`: Business logic, orchestration, external API calls
- `repository/`: Spring Data JPA interfaces for database access
- `model/`: JPA entities with relationships, enums, value objects
- `client/`: HTTP clients for external services
- `config/`: Spring beans, configuration classes

## Key Models

- `Wiki`: Main entity for generated documentation
- `WikiSection`: Individual sections (overview, architecture, etc.)
- `FileExplanation`: File-level documentation
- `ChatMessage`: RAG chatbot conversation history
- `GenerationStatus`: Async generation tracking
- `ValidationResult`: Repository validation outcomes

## Naming Conventions

- Entities: Singular nouns (Wiki, ChatMessage)
- Repositories: EntityNameRepository
- Services: EntityNameService with EntityNameServiceImpl
- Controllers: EntityNameController
- Use descriptive method names following Spring conventions
