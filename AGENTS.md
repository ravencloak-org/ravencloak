# AGENTS.md - Development Guidelines for AI Agents

This file provides guidance for AI agents working on this codebase.

## Build Commands

### Main Application (auth module)
```bash
./gradlew build              # Build entire project (all modules)
./gradlew bootRun           # Run main application
./gradlew test              # Run all tests
./gradlew test --tests "com.keeplearning.auth.KosAuthBackendApplicationTests"  # Run single test class
./gradlew clean build       # Clean build
```

### keycloak-spi Module
```bash
./gradlew :keycloak-spi:shadowJar  # Build fat JAR with relocated Kotlin stdlib
```

### Docker & Deployment
```bash
docker compose up -d        # Start PostgreSQL and Keycloak
```

## Project Structure

```
auth/
├── src/main/kotlin/com/keeplearning/auth/
│   ├── config/             # Spring configuration classes (Security, R2DBC, WebClient, OpenAPI)
│   ├── domain/             # Domain entities and repositories
│   │   ├── entity/         # R2DBC entities (User, KcClient, KcGroup, etc.)
│   │   └── repository/     # Repository interfaces
│   ├── grpc/               # gRPC service implementations and interceptors
│   ├── realm/              # Realm management (controllers, services, DTOs)
│   ├── client/             # Client/role management and onboarding
│   ├── keycloak/           # Keycloak admin client, sync services
│   ├── scim/               # SCIM 2.0 provisioning API
│   ├── audit/              # Audit logging and revert functionality
│   ├── security/           # Security guards, authorization managers
│   ├── auth/               # Authentication controllers (OAuth2 login)
│   ├── public/             # Public endpoints
│   └── exception/          # Global exception handler
├── src/main/proto/         # Protobuf/gRPC service definitions
├── keycloak-spi/           # Keycloak User Storage SPI module
├── scim-common/            # Shared SCIM 2.0 DTOs
├── forge/                  # Client SDK for SCIM API (deprecated, replaced by gRPC)
└── web/                    # Vue 3 + Vite + TypeScript admin portal (in-repo)
```

## Code Style Guidelines

### Language Version
- Kotlin 2.2.21
- Java 21

### Naming Conventions
- **Classes**: PascalCase (e.g., `UserService`, `RealmController`)
- **Functions**: camelCase (e.g., `listUsers`, `getUserByEmail`)
- **Variables/Properties**: camelCase (e.g., `userRepository`, `keycloakAdminClient`)
- **Constants**: UPPER_SNAKE_CASE
- **Packages**: lowercase, dot-separated (e.g., `com.keeplearning.auth.realm`)
- **DTOs**: Suffix with `Request`, `Response`, `Dto` (e.g., `CreateRealmUserRequest`, `RealmUserResponse`)
- **Entities**: Simple names (e.g., `User`, `KcClient` - no "Entity" suffix)
- **Repositories**: Entity name + `Repository` (e.g., `UserRepository`)

### Import Organization
No specific import ordering enforced. Wildcard imports are used for DTOs and repositories:
```kotlin
import com.keeplearning.auth.realm.dto.*
import com.keeplearning.auth.domain.repository.*
```

### Architecture Patterns

#### Controllers
- Use constructor injection
- Marked with `@RestController` and `@RequestMapping`
- Use `suspend` functions for async operations
- Return domain DTOs, not entities directly
- Use `@AuthenticationPrincipal` to get JWT actor info

```kotlin
@RestController
@RequestMapping("/api/super/realms/{realmName}/users")
class UserController(
    private val userService: UserService
) {
    @GetMapping
    suspend fun listUsers(@PathVariable realmName: String): List<RealmUserResponse> {
        return userService.listUsers(realmName)
    }
}
```

#### Services
- Marked with `@Service`
- Use constructor injection
- Use `LoggerFactory.getLogger()` for logging
- Throw `ResponseStatusException` for HTTP errors
- Use `awaitSingle()` / `awaitSingleOrNull()` for R2DBC operations

```kotlin
@Service
class UserService(
    private val userRepository: UserRepository,
    private val keycloakAdminClient: KeycloakAdminClient
) {
    private val logger = LoggerFactory.getLogger(UserService::class.java)

    suspend fun getUser(realmName: String, userId: UUID): RealmUserDetailResponse {
        val kcUser = try {
            keycloakAdminClient.getUser(realmName, userId.toString())
        } catch (e: Exception) {
            logger.error("Failed to fetch user $userId", e)
            throw ResponseStatusException(HttpStatus.NOT_FOUND, "User not found")
        }
        // ...
    }
}
```

#### Domain Entities
- Use R2DBC `@Table` and `@Column` annotations
- Use `@Id` for primary key
- Use data classes
- Prefer nullable types (`String?`) for optional fields

```kotlin
@Table("users")
data class User(
    @Id
    val id: UUID? = null,
    @Column("keycloak_user_id")
    val keycloakUserId: String,
    val email: String,
    @Column("display_name")
    val displayName: String? = null,
    // ...
)
```

#### Repositories
- Extend `R2dbcRepository<Entity, ID>`
- Use return types: `Mono<Entity>`, `Flux<Entity>`, `Mono<Boolean>`

```kotlin
interface UserRepository : R2dbcRepository<User, UUID> {
    fun findByEmail(email: String): Mono<User>
    fun findByAccountId(accountId: UUID): Flux<User>
    fun existsByEmail(email: String): Mono<Boolean>
}
```

### Error Handling

#### Global Exception Handler
- Located in `com.keeplearning.auth.exception.GlobalExceptionHandler`
- Uses `@RestControllerAdvice`
- Returns standardized `ErrorResponse`:
```kotlin
data class ErrorResponse(
    val timestamp: Instant = Instant.now(),
    val status: Int,
    val error: String,
    val message: String,
    val details: List<FieldError>? = null
)
```

#### Service-Level Errors
- Throw `ResponseStatusException` with appropriate `HttpStatus`
- Log errors with context before throwing
- Use meaningful error messages

```kotlin
throw ResponseStatusException(HttpStatus.NOT_FOUND, "User not found")
throw ResponseStatusException(HttpStatus.CONFLICT, "User already exists")
```

### Testing

#### Test Framework
- JUnit 5 with Kotlin test
- MockK for mocking
- Test location: `src/test/kotlin/`

#### Test Naming
- Use backticks for descriptive names with spaces
```kotlin
@Test
fun `toScimResource maps all fields correctly`() { }

@Test
fun `applyPatch replace operations`() { }
```

#### Assertions
- Use Kotlin test assertions: `assertEquals`, `assertNull`, `assertTrue`
```kotlin
assertEquals(userId.toString(), resource.id)
assertNull(resource.displayName)
assertTrue(resource.active)
```

### Security

#### Authorization
- Super admin routes: `/api/super/**` - requires `ROLE_SUPER_ADMIN` from `saas-admin` realm
- Account routes: `/api/account/**` - requires `ACCOUNT_ADMIN` or `INSTITUTE_ADMIN` role
- SCIM routes: `/api/scim/v2/**` - requires authenticated JWT
- gRPC endpoints: authenticated via `GrpcJwtAuthInterceptor` (Bearer token in `authorization` metadata)

#### JWT Handling
- Use `JwtIssuerReactiveAuthenticationManagerResolver` for multi-issuer validation
- Extract roles via `JwtAuthorityConverter` (prefixes with `ROLE_`)
- Get actor info from JWT claims (`sub`, `email`)
- gRPC uses a server interceptor that validates JWT from the `authorization` metadata header

### Database

#### Migrations
- Flyway migrations in `src/main/resources/db/migration`
- PostgreSQL with R2DBC
- ParadeDB BM25 for full-text search (V3+)

#### Reactive Patterns
- Use `suspend` functions for service layer
- Use `Mono`/`Flux` at repository boundary
- Use `awaitSingle()`, `awaitSingleOrNull()`, `.collectList().awaitSingle()`
- Never block on reactive streams

### API Conventions

#### REST Endpoints
- Plural nouns for collections: `/api/realms/{name}/users`
- Use HTTP verbs appropriately: GET (read), POST (create), PUT (replace), PATCH (update), DELETE (remove)
- Return 201 CREATED for successful POST
- Return 204 NO_CONTENT for successful DELETE

#### Versioning
- Header-based: `API-Version: 1.0`
- SCIM API: `/api/scim/v2/...`

### gRPC

#### Overview
- gRPC server runs on port `9090` (configurable via `GRPC_PORT` env var), alongside WebFlux on `8080`
- Uses Spring gRPC starter (`spring-grpc-spring-boot-starter`) with Kotlin coroutine stubs
- Proto definitions in `src/main/proto/keeplearning/auth/provisioning/v1/`

#### Service: `UserProvisioning`
- 8 RPCs: `CreateUser`, `GetUser`, `ListUsers`, `UpdateUser`, `DeleteUser`, `BulkCreateUsers`, `BulkUpdateUsers`, `GetChecksum`
- Implemented in `UserProvisioningGrpcService` as a thin adapter over existing SCIM services
- Error mapping: SCIM HTTP 404 -> gRPC `NOT_FOUND`, 409 -> `ALREADY_EXISTS`, 400 -> `INVALID_ARGUMENT`

#### Authentication
- `GrpcJwtAuthInterceptor` validates Bearer tokens from the `authorization` metadata header
- Uses the same `KEYCLOAK_ISSUER_PREFIX`-based multi-issuer resolution as REST endpoints
- Validated JWT is stored in gRPC `Context` for downstream access

#### Patterns
```kotlin
@Service
class MyGrpcService(
    private val someService: SomeService
) : MyServiceGrpcKt.MyServiceCoroutineImplBase() {

    override suspend fun myMethod(request: MyRequest): MyResponse {
        return handleScimErrors {
            // delegate to existing service layer
        }
    }
}
```

### Dependencies

#### Key Libraries
- Spring Boot 4.0.1 with WebFlux (reactive)
- Spring gRPC 1.0.2 with Kotlin coroutine stubs
- R2DBC for reactive PostgreSQL
- Spring Security with OAuth2/OIDC
- Kotlin Coroutines & Reactor
- Jackson for JSON
- Protobuf + gRPC code generation via `com.google.protobuf` Gradle plugin

#### Keycloak SPI
- Located in `keycloak-spi/` module
- Uses `compileOnly` for Keycloak dependencies (provided at runtime)
- Kotlin stdlib relocated via Shadow plugin to avoid conflicts
