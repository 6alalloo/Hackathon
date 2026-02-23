# Product Overview

CodeWiki Generator is an AI-powered documentation tool that automatically generates comprehensive wiki-style documentation for public GitHub repositories.

## Core Capabilities

- Repository validation and cloning via JGit
- AI-powered documentation generation using HuggingFace LLMs (Qwen/Qwen2.5-Coder-32B-Instruct)
- Full-text search across generated wikis
- Interactive RAG-based chatbot for repository Q&A
- Repository update monitoring with scheduled checks
- Rate limiting and concurrent request management
- Single Docker container deployment

## Key Constraints

- Maximum repository size: 10MB (configurable)
- Concurrent wiki generations: 10 (configurable)
- LLM API rate limit: 100 requests/minute (configurable)
- File-based H2 database for persistence
- Public GitHub repositories only

## User Workflow

1. User submits GitHub repository URL
2. System validates and clones repository
3. AI analyzes codebase and generates structured documentation
4. User can search wikis and interact with chatbot for Q&A
5. System monitors repositories for updates
