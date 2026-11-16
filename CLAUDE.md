# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

This is a legal case assistant system built with Java Spring Boot - an AI-powered legal case quality monitoring and management system that integrates with the Dify AI platform to provide intelligent case analysis capabilities.

## Development Commands

### Build and Run
```bash
# Build project
mvn clean compile

# Run application (default port 8080)
mvn spring-boot:run

# Package application
mvn clean package

# Run tests
mvn test
```

### Database Setup
- Database: MySQL
- Default connection: `jdbc:mysql://localhost:3306/case_assistant_db`
- Automatic database schema creation through `hibernate.ddl-auto: update`

### Running Specific Tests
```bash
# Run a specific test class
mvn -Dtest=TestClassName test

# Run a specific test method
mvn -Dtest=TestClassName#testMethod test
```

## Architecture Overview

### Core Technology Stack
- **Spring Boot 3.1.5** with Java 17
- **Spring Data JPA** with Hibernate for database operations
- **Spring WebFlux** for reactive/streaming capabilities
- **MySQL** as the main database
- **Knife4j/Swagger** providing API documentation at `/doc.html`
- **Lombok** for reducing boilerplate code
- **RestTemplate** and **WebClient** for HTTP client operations

### Package Structure
```
com.hubu.caseassistant/
├── config/           # Configuration classes (Dify API, CORS)
├── controller/       # REST controllers (CaseAnalysisController, ChatController)
├── dto/             # Data Transfer Objects
├── model/           # JPA entities (Case, ProsecutorProfile, Report)
├── service/         # Business logic
│   ├── client/      # External API clients (DifyApiClient, DifyChatApiClient)
│   └── impl/        # Service implementations
└── repository/      # JPA repositories
```

### Core Domain Models
- **Case**: Complex entity with 70+ fields representing legal cases and analysis results
- **ProsecutorProfile**: Tracks prosecutor performance metrics
- **Report**: Manages uploaded documents and their Dify file associations

### API Architecture
Two main API groups:
1. **Case Analysis API** (`/api/`) - File upload, analysis workflow, case management
2. **Chat API** (`/api/chat/`) - AI chat interface

### Dify AI Integration Pattern
The application follows this integration flow:
1. Upload files to Dify platform via `DifyApiClient`
2. Trigger AI workflow for case analysis
3. Parse structured JSON responses from Dify
4. Store analysis results in local database
5. Provide both synchronous and server-sent event (SSE) streaming responses

### Configuration
- Main config file: `src/main/resources/application.yml`
- Dify API settings bound to `DifyApiProperties` class
- Database and AI service configuration for different environments

### Key Technical Features
- **Server-Sent Events (SSE)** for real-time analysis progress streaming
- **Reactive programming** using WebFlux for non-blocking operations
- **Global exception handling** for consistent API error responses
- **CORS configuration** for frontend integration
- **UUID-based primary keys** for entities
- **File upload and management** with temporary file storage

## Development Notes

### External Dependencies
- Requires running Dify AI platform (default: `http://localhost/v1`)
- Requires MySQL database connection for data persistence
- Dify integration API keys configured in `application.yml`

### Testing
- Test structure exists but is currently empty
- Run tests with `mvn test` after implementing tests

### API Documentation
- Access Swagger UI at `http://localhost:8080/doc.html` when running
- All endpoints documented with OpenAPI 3 annotations

### Key Implementation Details

1. **File Processing Workflow**:
   - Files are uploaded via multipart form data to `/api/analysis/start`
   - Files are temporarily stored on disk before being uploaded to Dify
   - Dify workflow is triggered for analysis
   - Results are parsed and stored in the database

2. **Dify Integration**:
   - Two API clients exist: `DifyApiClient` for workflow operations and `DifyChatApiClient` for chat
   - Supports both blocking and streaming responses from Dify
   - File uploads use `RestTemplate` while streaming uses `WebClient`

3. **Data Model**:
   - `Case` entity contains comprehensive legal case information with 70+ fields
   - `ProsecutorProfile` tracks prosecutor performance metrics
   - `Report` manages document uploads and their Dify file associations

4. **Streaming Analysis**:
   - Uses Server-Sent Events (SSE) for real-time progress updates
   - Supports both cached results and new analysis
   - Provides detailed thinking process reporting