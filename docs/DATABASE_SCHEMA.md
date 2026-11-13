# Database Schema Specification

## 1. Database technology
- PostgreSQL
- Spring Data JPA
- Hibernate ORM

## 2. Core entity model

### 2.1 User
Represents a user of the application.

| Field | Type | Constraints | Notes |
|---|---|---|---|
| id | UUID | PK | Primary key |
| username | VARCHAR(100) | NOT NULL, UNIQUE | Normalized account handle |
| email | VARCHAR(255) | NOT NULL, UNIQUE | Normalized sign-in email |
| passwordHash | VARCHAR(255) | NOT NULL | Hashed password |
| firstName | VARCHAR(100) | NOT NULL | Display name |
| lastName | VARCHAR(100) | NOT NULL | Display name |
| createdAt | TIMESTAMP | NOT NULL | Audit timestamp |
| updatedAt | TIMESTAMP | NOT NULL | Audit timestamp |

Authentication rules:
- Store only BCrypt password hashes; never persist or return the submitted password.
- Normalize email addresses and usernames to lowercase before checking uniqueness and saving.
- Email and username uniqueness are enforced in the database and checked before account creation.

### 2.2 Project
Represents a software project workspace.

| Field | Type | Constraints | Notes |
|---|---|---|---|
| id | UUID | PK | Primary key |
| name | VARCHAR(255) | NOT NULL | Project name |
| description | TEXT | NOT NULL | Project description |
| objectives | TEXT | NULL | Goals and outcomes |
| domain | VARCHAR(100) | NOT NULL | Domain classification |
| preferredStack | VARCHAR(255) | NOT NULL | Tech preferences |
| status | VARCHAR(50) | NOT NULL | e.g., DRAFT, ACTIVE, COMPLETED |
| userId | UUID | FK -> user.id | Owner of project |
| createdAt | TIMESTAMP | NOT NULL | Audit timestamp |
| updatedAt | TIMESTAMP | NOT NULL | Audit timestamp |

### 2.3 Requirement
Represents a functional or non-functional requirement.

| Field | Type | Constraints | Notes |
|---|---|---|---|
| id | UUID | PK | Primary key |
| projectId | UUID | FK -> project.id | Parent project |
| code | VARCHAR(50) | NOT NULL | e.g., REQ-001 |
| title | VARCHAR(255) | NOT NULL | Requirement title |
| description | TEXT | NOT NULL | Full requirement text |
| acceptanceCriteria | TEXT | NULL for legacy rows | New API writes require non-empty criteria; stored newline-separated |
| type | VARCHAR(50) | NOT NULL | FUNCTIONAL or NON_FUNCTIONAL |
| priority | VARCHAR(50) | NOT NULL | LOW/MEDIUM/HIGH |
| source | VARCHAR(100) | NULL | `MOCK_DEMO`, `OPENAI`, or manual source |
| status | VARCHAR(50) | NOT NULL | `PENDING`, `APPROVED`, or `REJECTED` |
| createdAt | TIMESTAMP | NOT NULL | Audit timestamp |
| updatedAt | TIMESTAMP | NOT NULL | Audit timestamp |

The application adds `acceptanceCriteria` through Hibernate's additive schema update; it does not recreate or clear existing data. The UUID primary key and non-null project foreign key preserve requirement identity and project association. New requirements start in `PENDING`; editing a reviewed requirement resets it to `PENDING`.

### 2.4 UserStory
Represents a user-centered story tied to requirements.

| Field | Type | Constraints | Notes |
|---|---|---|---|
| id | UUID | PK | Primary key |
| projectId | UUID | FK -> project.id | Parent project |
| requirementId | UUID | FK -> requirement.id | Supporting requirement |
| storyId | VARCHAR(50) | NOT NULL | e.g., US-001 |
| title | VARCHAR(255) | NOT NULL | User story title |
| description | TEXT | NOT NULL | Story narrative |
| acceptanceCriteria | TEXT | NOT NULL | Acceptance criteria |
| priority | VARCHAR(50) | NOT NULL | Priority |
| createdAt | TIMESTAMP | NOT NULL | Audit timestamp |
| updatedAt | TIMESTAMP | NOT NULL | Audit timestamp |

### 2.5 ArchitectureDocument
Represents a versioned architecture output for a project. New documents use `content`, `architectureStyle`, `generationProvider`, `version`, and `diagrams`; the original architecture columns remain for compatibility with existing records and are populated as summaries/projections for new documents.

| Field | Type | Constraints | Notes |
|---|---|---|---|
| id | UUID | PK | Primary key |
| projectId | UUID | FK -> project.id | Parent project |
| title | VARCHAR(255) | NOT NULL | Architecture title |
| summary | TEXT | NOT NULL | Retained legacy column; overview projection for new documents |
| components | TEXT | NOT NULL | Retained legacy column; content projection for new documents |
| databaseDesign | TEXT | NOT NULL | Retained legacy column; database section projection |
| apiEndpoints | TEXT | NOT NULL | Retained legacy column; API section projection |
| content | TEXT | NULL | Full editable architecture document; null for legacy rows |
| architectureStyle | VARCHAR(120) | NULL | Recommended style |
| generationProvider | VARCHAR(40) | NULL | `MOCK_DEMO`, `OPENAI`, or `LEGACY` in API responses |
| diagrams | TEXT | NULL | JSON map of four Mermaid diagram sources |
| version | INTEGER | NULL | Per-project version; null on older records |
| status | VARCHAR(50) | NOT NULL | `DRAFT`, `APPROVED`, or `REJECTED` |
| createdAt | TIMESTAMP | NOT NULL | Audit timestamp |
| updatedAt | TIMESTAMP | NOT NULL | Audit timestamp |

The `(project_id, version)` pair is unique for generated versions. Each generation creates a new version rather than overwriting an earlier approved/rejected document. `architecture_document_requirements` is an element-collection table storing the source requirement UUIDs used for that document; ownership and approval are checked again at generation time.

### 2.6 DevelopmentTask
Represents a work item from the development plan.

| Field | Type | Constraints | Notes |
|---|---|---|---|
| id | UUID | PK | Primary key |
| projectId | UUID | FK -> project.id | Parent project |
| requirementId | UUID | FK -> requirement.id | Related requirement |
| taskId | VARCHAR(50) | NOT NULL | e.g., TASK-001 |
| title | VARCHAR(255) | NOT NULL | Task title |
| description | TEXT | NOT NULL | Task description |
| priority | VARCHAR(50) | NOT NULL | High/Medium/Low |
| status | VARCHAR(50) | NOT NULL | TODO/IN_PROGRESS/DONE |
| dependencyIds | TEXT | NULL | Related tasks |
| milestone | VARCHAR(100) | NULL | Delivery milestone |
| createdAt | TIMESTAMP | NOT NULL | Audit timestamp |
| updatedAt | TIMESTAMP | NOT NULL | Audit timestamp |

### 2.7 TestCase
Represents a QA scenario.

| Field | Type | Constraints | Notes |
|---|---|---|---|
| id | UUID | PK | Primary key |
| projectId | UUID | FK -> project.id | Parent project |
| requirementId | UUID | FK -> requirement.id | Related requirement |
| testId | VARCHAR(50) | NOT NULL | e.g., TC-001 |
| title | VARCHAR(255) | NOT NULL | Test case title |
| description | TEXT | NOT NULL | Test description |
| preconditions | TEXT | NOT NULL | Preconditions |
| steps | TEXT | NOT NULL | Execution steps |
| expectedResult | TEXT | NOT NULL | Expected outcome |
| type | VARCHAR(50) | NOT NULL | FUNCTIONAL, NEGATIVE, EDGECASE |
| createdAt | TIMESTAMP | NOT NULL | Audit timestamp |
| updatedAt | TIMESTAMP | NOT NULL | Audit timestamp |

### 2.8 RequirementTraceability
Stores cross-linking between requirements and downstream artifacts.

| Field | Type | Constraints | Notes |
|---|---|---|---|
| id | UUID | PK | Primary key |
| projectId | UUID | FK -> project.id | Parent project |
| requirementId | UUID | FK -> requirement.id | Related requirement |
| userStoryId | UUID | FK -> user_story.id | Optional story link |
| taskId | UUID | FK -> development_task.id | Optional task link |
| testCaseId | UUID | FK -> test_case.id | Optional test link |
| coverageStatus | VARCHAR(50) | NOT NULL | COVERED/UNCOVERED |
| createdAt | TIMESTAMP | NOT NULL | Audit timestamp |
| updatedAt | TIMESTAMP | NOT NULL | Audit timestamp |

### 2.9 AgentExecutionLog
Stores runtime agent execution metadata and failures.

| Field | Type | Constraints | Notes |
|---|---|---|---|
| id | UUID | PK | Primary key |
| projectId | UUID | FK -> project.id | Parent project |
| agentName | VARCHAR(150) | NOT NULL | RequirementAnalystAgent etc. |
| status | VARCHAR(50) | NOT NULL | STARTED, SUCCESS, FAILED, RETRY |
| inputSummary | TEXT | NOT NULL | High-level prompt metadata |
| outputSummary | TEXT | NULL | Result summary |
| errorMessage | TEXT | NULL | Failure message |
| retryCount | INTEGER | NOT NULL DEFAULT 0 | Retry counter |
| startedAt | TIMESTAMP | NOT NULL | Start timestamp |
| completedAt | TIMESTAMP | NULL | Finished timestamp |

## 3. Key relationships
- User has many projects
- Project has many requirements, user stories, tasks, test cases, architecture documents, logs
- Requirement can relate to many user stories, tasks, tests, and traceability rows
- UserStory links to one requirement and belongs to one project
- DevelopmentTask links to one project and one requirement
- TestCase links to one project and one requirement
- RequirementTraceability connects all downstream artifacts to a requirement

## 4. Constraints and rules
- Project name and description must be non-empty
- Each requirement should have a unique requirement code within a project
- Each task and test case should have a unique identifier within the project
- Traceability rows must not indicate a requirement as covered when no downstream artifact exists
- All timestamps should be managed automatically by JPA auditing

## 5. JPA mapping recommendations
Use:
- `@Entity` for all domain models
- `@Table` with explicit naming strategy where needed
- `@ManyToOne`, `@OneToMany`, `@OneToOne` based on strict cardinality
- `@CreationTimestamp` and `@UpdateTimestamp` for audit fields

## 6. Implementation status
Users, projects, requirements, and architecture documents are persisted by the current backend. Development tasks, test cases, user stories, traceability, and agent logs remain design-level entities unless otherwise implemented in a later phase. Servlet sessions are currently stored by the application server in memory and are not represented in this relational schema; a shared session store is required before horizontally scaling application instances.
