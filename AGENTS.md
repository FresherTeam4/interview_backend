# AGENTS.md

## Purpose and scope

- These instructions apply to the entire repository unless a more specific nested `AGENTS.md` or `AGENTS.override.md` says otherwise.
- This repository is the backend for an interview platform. It handles accounts, authentication, CV upload and storage, AI-assisted CV parsing, and candidate profiles.
- Make the smallest complete change that satisfies the request. Preserve existing behavior unless the task explicitly requires a behavior or contract change.

## Technology baseline

- Java 17 without preview features.
- Spring Boot 4.1.x, Spring MVC, Spring Security, Spring Data JPA, and Jakarta Validation.
- Maven Wrapper is the supported build entry point; do not require a globally installed Maven.
- MySQL is the application database. Liquibase owns all schema changes.
- AWS SDK v2 speaks S3 to MinIO for CV object storage.
- Spring AI integrates with Gemini for CV parsing.
- JUnit 5, Mockito, AssertJ, and Spring test support are used for tests.
- The existing base package is `com.baseProject.myBaseProject`. Do not rename it or perform a broad package cleanup as part of an unrelated change.

## Repository map

- `controller`, `dto`, and `mapper`: HTTP endpoints, external contracts, and entity/domain-to-DTO mapping.
- `service` and `service/impl`: service contracts, application workflows, and business rules.
- `repository` and `entity`: Spring Data persistence access and JPA entities.
- `security` and `exception`: authentication/authorization and the shared API error contract.
- `cv` and `storage`: CV validation/parsing adapters and storage ports/MinIO implementation.
- `config`: Spring configuration and typed properties.
- `src/main/resources/db/changelog`: ordered Liquibase migrations.
- `src/main/resources/ai`: versioned CV prompts and response schemas.
- `src/test/java`: tests mirroring the production package structure.
- `docs`: API and integration documentation.

## Working workflow

1. Read the affected production code, tests, configuration, and documentation before editing. Trace callers and persisted/API contracts when behavior may cross layers.
2. Check the current Git diff and preserve unrelated user changes. Do not revert, rewrite, or reformat files outside the requested scope.
3. Prefer extending an existing abstraction, exception, mapper, configuration record, or test pattern over introducing a parallel design.
4. Implement behavior and tests together. Bug fixes require a regression test when testable.
5. Run focused tests during development, then the appropriate broader verification before
   reporting completion.
6. Report changed behavior, verification actually run, and any remaining risk or blocked check.

### Interview Engine module review override

- Effective from Interview Engine `M03`, do not create new unit, controller, or integration test
  files for a module unless the user explicitly asks for automated tests.
- This scoped agreement overrides the general test-authoring and per-change test-execution
  requirements in this file for planned Interview Engine modules `M03`–`M16`; it does not delete,
  disable, or weaken tests that already exist from `M01`/`M02` or other features.
- Default verification is compilation plus relevant configuration, Liquibase/Hibernate, startup,
  and diff checks in proportion to the change. Do not run a large test suite by default merely as a
  module review ritual; report any verification that was actually run.
- At the end of every module, explain the implementation and runtime flow. For every API created or
  changed, provide a Swagger checklist with sample input, expected success response, and important
  failure cases. Authentication setup is intentionally omitted from that checklist.
- For a foundation module with no public API, provide safe local database, application-startup, or
  log inspection steps instead of Swagger instructions.

Do not edit generated or local-only artifacts such as `target/`, `.idea/`, `.claude/settings.local.json`, or local database files. Do not commit, push, rewrite Git history, delete data, or run destructive Docker/database commands unless the user explicitly requests it.

## Build and verification commands

Use the wrapper that matches the shell:

- Windows compile: `.\mvnw.cmd -DskipTests compile`
- Windows focused test: `.\mvnw.cmd -Dtest=ClassName test`
- Windows full test suite: `.\mvnw.cmd test`
- Linux/macOS compile: `./mvnw -DskipTests compile`
- Linux/macOS focused test: `./mvnw -Dtest=ClassName test`
- Linux/macOS full test suite: `./mvnw test`
- Run the application: `.\mvnw.cmd spring-boot:run` or `./mvnw spring-boot:run`
- Start the local MinIO dependency: `docker compose up -d`

- For Java, `pom.xml`, configuration, migration, prompt, or schema changes, run at least the relevant focused tests. Run the full suite when the local environment supports it.
- Documentation-only or instruction-only changes do not require Maven tests.
- Never claim a command passed unless it ran successfully. If MySQL, MinIO, Gemini, network access, or another prerequisite blocks a check, name the exact blocker and report what did run.
- Unit tests must not require a live Gemini API, public network, MinIO, or developer database. Mock external boundaries; use an explicitly identified integration test when live infrastructure is the subject.

## Java design and style

- Use four-space indentation, no wildcard or unused imports, and no trailing whitespace. Follow the surrounding file and avoid unrelated formatting churn.
- Use clear English names for Java symbols, JSON fields, database objects, and error codes. Preserve the language of existing user-facing text unless translation is part of the task.
- Prefer small cohesive methods and early validation over deeply nested control flow. Do not add speculative abstractions.
- Use constructor injection. Dependencies should normally be `private final`; use `@RequiredArgsConstructor` when it keeps the class clear. Never use field injection.
- Prefer immutable `record` types for DTOs, configuration properties, and small value carriers. Never expose a JPA entity directly from an API endpoint.
- Keep existing service interfaces for application boundaries. Do not create an interface only for a private implementation detail with no boundary or testing value.
- Use `Clock` for business timestamps and deterministic tests and `Instant` for stored timestamps. Do not call `Instant.now()` where a `Clock` is available.
- Use `Optional` for a possibly absent return value, not for entity/DTO fields or parameters. Return empty collections rather than `null` collections.
- Use SLF4J parameterized logging. Do not use `System.out`, swallow exceptions, or catch broadly without deliberate translation or rethrowing.
- Comments and Javadocs explain business intent, non-obvious tradeoffs, or framework behavior; they should not restate the code.
- Lombok is allowed where already used, but do not add `@Data` to JPA entities. Be explicit about entity equality, relationships, and mutability.

## Layering and architecture

- Controllers own HTTP parsing, Jakarta validation, authorization annotations, status codes, headers/cookies, and response DTOs. They must not call repositories or infrastructure SDKs.
- Services own use-case orchestration, business invariants, ownership checks, and transaction boundaries. Keep controller logic thin.
- Repositories own database queries. Use explicit batch/fetch queries when collection mapping would otherwise cause N+1 selects.
- Mappers own response mapping. Do not duplicate entity-to-DTO logic in controllers and services.
- Keep external systems behind the existing ports such as `FileStorageService` and
  `CvParserClient`. Infrastructure-specific exceptions must be translated into meaningful domain
  failures at the boundary.
- Put environment-backed settings in validated `@ConfigurationProperties` records. Do not scatter
  `@Value` expressions or duplicate configuration keys across application code.
- Preserve async CV parsing boundaries and explicit CV status transitions. Consider retries,
  duplicate submissions, queue rejection, and partial failure before changing an upload/parse flow.
- For workflows spanning storage and the database, consider compensation and idempotency so a
  failure does not silently leave an orphaned object or inconsistent row.

## API and validation rules

- Keep REST endpoints under `/api` and follow the resource-oriented routes already present.
- Validate untrusted input at the boundary with Jakarta Validation and `@Valid`; enforce invariants requiring database or domain state again in the service layer.
- Use the existing `@CurrentUser`, `@IsAuthenticated`, `@IsUser`, and `@IsAdmin` mechanisms instead of manually decoding authentication in controllers.
- Scope user-owned resource queries by both user ID and resource ID. Do not fetch by resource ID alone and check ownership only after returning or mutating data.
- Use intentional HTTP status codes. Preserve existing response shapes and refresh-cookie behavior
  unless an API contract change is explicitly requested.
- Add or update `@Operation`, `@Tag`, and `@SecurityRequirement` when endpoint behavior or security
  changes. Update the relevant file under `docs/` for material API workflow changes.
- Represent expected business failures with the existing `DomainException`, `ErrorCode`, and
  `Message` mechanisms. Let `GlobalExceptionHandler` produce the standard `ApiError` response.
  Use a controller-local handler only for endpoint-specific response effects such as clearing a
  refresh cookie.
- Never expose stack traces, SQL details, storage keys, provider error bodies, or internal exception
  messages to API clients.
- Treat upload metadata as untrusted. Keep content size, PDF structure, encryption, and page-count
  checks in the validation boundary; do not trust only the filename or declared content type.

## Persistence and Liquibase

- Put transaction boundaries on service methods. Mark pure query methods
  `@Transactional(readOnly = true)` where a transaction is useful. Avoid holding a database
  transaction open across slow storage, network, or AI calls unless correctness requires it and the
  tradeoff is documented.
- Keep `spring.jpa.hibernate.ddl-auto=validate`. Never use Hibernate auto-update to evolve schemas.
- Every schema or seed-data change requires a new Liquibase migration in
  `src/main/resources/db/changelog`; never edit a changeset that may have been applied.
- Continue the zero-padded numeric filename and changeset sequence, include the new file in
  `db.changelog-master.yaml`, and provide a safe `--rollback` instruction where practical.
- Use explicit constraints, foreign-key actions, indexes, lengths, nullability, charset, and
  collation consistent with the existing MySQL migrations. Index foreign keys and frequent lookup
  paths, but do not add speculative indexes without a query use case.
- Keep entity mappings synchronized with the migration. Review cascade/orphan behavior and avoid
  accidental eager loading or unbounded collection loading.
- Preserve database constraints as the final concurrency-safe guard. Translate expected constraint
  failures into the established API error contract.

## Security and privacy

- Never commit or print real passwords, JWT secrets, OAuth tokens, Gemini keys, storage credentials,
  private CV contents, refresh tokens, ID tokens, or presigned URLs. Development defaults must stay
  clearly non-production and be overridable by environment variables.
- Do not log authentication credentials or raw uploaded/AI content. Log stable identifiers and
  bounded, sanitized provider diagnostics only when operationally useful.
- Preserve password hashing, JWT validation, refresh-token rotation/revocation, HttpOnly cookie
  behavior, CORS restrictions, and method-level authorization unless the task explicitly changes
  the security design.
- Apply least privilege. New public endpoints must have an explicit decision about authentication
  and authorization rather than inheriting accidental access.
- Validate and normalize all data crossing HTTP, file, database, storage, OAuth, or AI trust
  boundaries. Avoid revealing whether another user's resource exists.

## AI parsing and versioned contracts

- Keep CV prompts and JSON schemas in `src/main/resources/ai`; do not embed large prompts or schemas
  in Java source.
- Treat the prompt, JSON schema, `CvParsedPayload`, mapper, and persisted `schema_version` as one
  contract. Update and test them together.
- When a parsing contract changes incompatibly, add a new versioned prompt/schema instead of
  silently changing the meaning of an already persisted schema version.
- Validate model output and map provider timeouts, rate limits, unavailable service, malformed
  output, and missing API configuration to the existing CV parse failure model.
- Preserve the ability to start the application without `GEMINI_API_KEY`; failure should occur only
  when parsing is requested, as currently designed.
- Tests must use mocked model responses and assert the generated request and parsed result without
  sending real CVs to an external provider.

## Testing conventions

- Mirror the production package in `src/test/java` and name test classes `<ClassUnderTest>Test`.
- Name tests by observable behavior, for example `listLoadsAllProfilesInOneBatch` or
  `verifyRejectsExpiredToken`.
- Prefer a fast unit test with Mockito for service logic and external adapters. Use Spring context,
  MVC, or persistence tests only when Spring wiring, serialization, security filters, transactions,
  or queries are the behavior under test.
- Use a fixed `Clock` and fixed identifiers/timestamps. Tests must be deterministic and independent
  of execution order, local time zone, developer data, and network availability.
- Cover the happy path and the important validation, authorization, ownership, state-transition,
  duplicate/idempotency, and provider-failure paths affected by the change.
- Assert observable outcomes and important collaborations; avoid overspecifying private
  implementation details. Add explicit query-count/batch behavior assertions when preventing an
  N+1 regression.
- Do not delete, disable, weaken, or catch failures from existing tests merely to make a build pass.

## Dependencies and configuration

- Prefer the JDK, Spring Boot, and dependencies already present before adding another library.
- Do not add or upgrade a production dependency unless required by the task. Explain its purpose,
  maintenance/security impact, and why existing capabilities are insufficient.
- Let the Spring Boot parent, Spring AI BOM, and AWS SDK BOM manage versions. Add an explicit version
  only when the dependency is not managed, following the existing `pom.xml` structure.
- Keep secrets and environment-specific values outside source control. Add new settings to
  `application.yaml` with environment-variable overrides and bind them through validated typed
  properties.
- Do not change Java, Spring Boot, database, provider model, or dependency versions as incidental
  cleanup.

## Completion checklist

- The requested behavior is complete and scoped; unrelated user changes are preserved.
- Layering, API compatibility, authorization, privacy, transactions, and failure paths were reviewed.
- Relevant tests were added or updated and the reported verification was actually run.
- Liquibase, OpenAPI, AI contract, and `docs/` changes accompany the code when applicable.
- No generated files, local configuration, credentials, debug output, or unrelated formatting are
  included.
- The final report lists the important changed files, tests/commands run, and any unresolved risk.
