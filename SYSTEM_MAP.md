# SYSTEM MAP

Complete codebase structure for the KOS Auth Backend. Use this to quickly find where things are and understand module boundaries.

## Top-Level Directory

```
auth/                          # Project root
├── src/                       # Main Spring Boot application (auth module)
├── keycloak-spi/              # Keycloak User Storage SPI plugin
├── scim-common/               # Shared SCIM 2.0 DTOs (library)
├── forge/                     # Spring Boot Starter SDK for SCIM (library)
├── web/                       # Vue 3 Admin Portal
├── go-nebula-sidecar/         # Go sidecar service
├── scim/                      # SCIM documentation (README)
├── docs/                      # MkDocs documentation site
├── docker/                    # Docker init scripts (init-db.sql)
├── scripts/                   # Utility scripts
├── .github/workflows/         # GitHub Actions CI/CD
├── .woodpecker/               # Woodpecker CI pipelines
├── buildSrc/                  # Gradle build logic
├── build.gradle.kts           # Root Gradle build file
├── settings.gradle.kts        # Gradle module includes
├── docker-compose.yml         # Docker Compose for local dev
├── Dockerfile                 # Auth backend Docker image
├── mkdocs.yml                 # MkDocs configuration
└── CLAUDE.md                  # AI assistant instructions
```

## Auth Backend (`src/`)

The main Spring Boot application. Entry point: `KosAuthApplication.kt`.

### Package Map

```
src/main/kotlin/com/keeplearning/auth/
│
├── KosAuthApplication.kt                 # @SpringBootApplication entry point
│
├── config/                                # SPRING CONFIGURATION
│   ├── SecurityConfig.kt                  #   SecurityWebFilterChain, route authorization rules
│   ├── JwtAuthorityConverter.kt           #   Extracts realm_access.roles from JWT → ROLE_* authorities
│   ├── KeycloakAdminConfig.kt             #   Keycloak admin client WebClient bean
│   ├── R2dbcConfig.kt                     #   R2DBC custom converters (JSONB ↔ Kotlin)
│   ├── WebClientConfig.kt                 #   WebClient builder configuration
│   ├── WebFluxVersioningConfig.kt         #   API-Version header routing for SCIM
│   └── OpenApiConfig.kt                   #   SpringDoc/Swagger UI configuration
│
├── security/                              # AUTHORIZATION UTILITIES
│   ├── SuperAdminAuthorizationManager.kt  #   Validates super admin (saas-admin realm + SUPER_ADMIN role)
│   └── SecurityGuards.kt                  #   Realm/role check utility functions
│
├── domain/                                # SHARED DOMAIN MODEL
│   ├── entity/                            #   R2DBC entities (@Table)
│   │   ├── User.kt                        #     Shadow user (keycloak_user_id, email, profile fields)
│   │   ├── KcRealm.kt                     #     Keycloak realm shadow
│   │   ├── KcClient.kt                    #     Keycloak client shadow (paired_client_id)
│   │   ├── KcGroup.kt                     #     Hierarchical group
│   │   ├── KcRole.kt                      #     Realm/client role
│   │   ├── KcSyncLog.kt                   #     Sync tracking (entity_type, direction, status)
│   │   ├── KcUserClient.kt               #     User-client authorization
│   │   ├── KcUserStorageProvider.kt       #     SPI provider configuration
│   │   ├── AppRole.kt                     #     Application-level role (global/realm/client scope)
│   │   ├── ClientCustomRole.kt            #     Dynamic per-client role (is_default)
│   │   ├── UserAppRole.kt                 #     User ↔ app role assignment
│   │   └── UserClientCustomRole.kt        #     User ↔ custom role assignment
│   ├── repository/                        #   R2DBC repositories (R2dbcRepository<T, UUID>)
│   │   ├── UserRepository.kt             #     findByEmail, findByRealmIdAndEmail, search
│   │   ├── KcRealmRepository.kt           #     findByRealmName, findByAccountId
│   │   ├── KcClientRepository.kt          #     findByKeycloakId, findByPairedClientId
│   │   ├── KcGroupRepository.kt           #     findByRealmId, findByParentId
│   │   ├── KcRoleRepository.kt            #     findByRealmId, findByClientId
│   │   ├── KcSyncLogRepository.kt         #     Sync log queries
│   │   ├── KcUserClientRepository.kt      #     User-client auth queries
│   │   ├── KcUserStorageProviderRepository.kt
│   │   ├── AppRoleRepository.kt
│   │   ├── ClientCustomRoleRepository.kt  #     findByClientId
│   │   ├── UserAppRoleRepository.kt
│   │   └── UserClientCustomRoleRepository.kt
│   └── types/
│       └── JsonValue.kt                   #     Custom R2DBC JSONB type wrapper
│
├── realm/                                 # REALM-SCOPED ENTITY MANAGEMENT
│   ├── controller/                        #   REST controllers (/api/super/realms/{realm}/...)
│   │   ├── RealmController.kt             #     CRUD realms, trigger sync
│   │   ├── ClientController.kt            #     CRUD clients, create applications, integration snippets
│   │   ├── RoleController.kt              #     CRUD realm/client roles
│   │   ├── GroupController.kt             #     CRUD hierarchical groups, role assignments
│   │   ├── IdpController.kt               #     CRUD identity providers
│   │   └── UserController.kt              #     List/search users, group/role assignments
│   ├── dto/                               #   Request/response data classes
│   │   ├── RealmDtos.kt
│   │   ├── ClientDtos.kt                  #     CreateClientRequest, CreateApplicationRequest, etc.
│   │   ├── RoleDtos.kt
│   │   ├── GroupDtos.kt
│   │   ├── IdpDtos.kt
│   │   ├── UserDtos.kt
│   │   └── IntegrationSnippetsDtos.kt     #     Code snippet generator (JS, React, Vue, Spring Boot)
│   └── service/                           #   Business logic
│       ├── RealmService.kt                #     Realm CRUD + Keycloak sync
│       ├── ClientService.kt               #     Client CRUD + paired client creation
│       ├── RoleService.kt                 #     Role CRUD (realm + client)
│       ├── GroupService.kt                #     Group hierarchy management
│       ├── IdpService.kt                  #     Identity provider management
│       └── UserService.kt                #     User queries and role/group assignment
│
├── client/                                # CLIENT-SCOPED OPERATIONS
│   ├── controller/
│   │   ├── ClientOnboardingController.kt  #     POST /api/clients/onboard
│   │   ├── ClientRoleController.kt        #     CRUD /api/clients/{id}/roles
│   │   └── ClientUserController.kt        #     CRUD /api/clients/{id}/users
│   ├── dto/
│   │   ├── ClientRoleDtos.kt
│   │   └── ClientUserDtos.kt              #     OnboardUsersRequest/Response
│   └── service/
│       ├── ClientOnboardingService.kt     #     User onboarding with paired client resolution
│       ├── ClientRoleService.kt           #     Custom client role management
│       └── ClientUserService.kt           #     User-client authorization management
│
├── audit/                                 # AUDIT TRAIL
│   ├── controller/
│   │   └── AuditController.kt            #     GET/POST /api/super/realms/{realm}/audit
│   ├── domain/
│   │   ├── EntityActionLog.kt             #     @Table entity for audit records
│   │   └── EntityActionLogRepository.kt   #     Query by realm, entity, actor, date range
│   ├── dto/
│   │   └── AuditDtos.kt                  #     AuditLogResponse, RevertRequest
│   └── service/
│       ├── AuditService.kt               #     Interface for logging entity actions
│       ├── AuditServiceImpl.kt            #     Implementation: captures JWT actor + before/after JSONB
│       ├── AuditQueryService.kt           #     Paginated audit log queries with filters
│       └── RevertService.kt              #     Restore entity to before_state (DB + Keycloak)
│
├── keycloak/                              # KEYCLOAK ADMIN INTEGRATION
│   ├── client/
│   │   ├── KeycloakAdminClient.kt         #     WebClient wrapper for Keycloak Admin REST API
│   │   └── dto/
│   │       ├── KeycloakDtos.kt            #     Keycloak API response types
│   │       └── KeycloakUserManagementDtos.kt
│   ├── controller/
│   │   ├── KeycloakUserManagementController.kt  # Direct Keycloak user CRUD
│   │   └── AccountUserRoleController.kt         # Email-based role management
│   ├── service/
│   │   ├── KeycloakUserManagementService.kt     # User CRUD via Keycloak Admin API
│   │   └── AccountUserRoleService.kt            # ECS integration (roles + approval_scopes)
│   └── sync/
│       ├── KeycloakSyncService.kt         #     Bidirectional sync: Keycloak ↔ shadow tables
│       └── StartupSyncRunner.kt           #     ApplicationRunner: triggers sync on boot
│
├── scim/                                  # SCIM 2.0 PROVISIONING API
│   ├── ScimUserController.kt             #     CRUD /api/scim/v2/realms/{realm}/Users
│   ├── ScimBulkController.kt             #     POST /api/scim/v2/realms/{realm}/Bulk
│   ├── ScimChecksumController.kt          #     GET  /api/scim/v2/realms/{realm}/Users/checksum
│   ├── ScimDiscoveryController.kt         #     ServiceProviderConfig, Schemas, ResourceTypes
│   ├── ScimUserService.kt                #     SCIM business logic
│   ├── ScimBulkService.kt                #     Bulk create/update processing
│   ├── ScimChecksumService.kt             #     SHA-256 user checksum computation
│   ├── ScimUserMapper.kt                 #     User entity ↔ SCIM resource mapping
│   ├── ScimFilterTranslator.kt            #     SCIM filter → R2DBC SQL WHERE clause
│   ├── ScimDtos.kt                       #     SCIM-specific request/response types
│   ├── ScimException.kt                  #     SCIM error types
│   └── ScimExceptionHandler.kt            #     RFC 7644 §3.12 error responses
│
├── auth/                                  # SUPER ADMIN AUTH
│   ├── SuperAdminAuthController.kt        #     OAuth2 login flow, /auth/super/me
│   └── SuperAdminMeResponse.kt
│
├── public/                                # PUBLIC (UNAUTHENTICATED) ENDPOINTS
│   ├── controller/
│   │   └── PublicUserController.kt        #     GET /api/public/users/{email} (for SPI)
│   └── dto/
│       └── PublicUserResponse.kt
│
└── exception/
    └── GlobalExceptionHandler.kt          #     @ControllerAdvice for REST API errors
```

### Resources

```
src/main/resources/
├── application.yml                        # Spring Boot configuration
└── db/migration/                          # Flyway SQL migrations
    ├── V0__setup_uuidv7.sql               #   UUIDv7 function
    ├── V1__admin_schema.sql               #   Core: accounts, institutes, apps, users, roles
    ├── V2__paradedb_extensions.sql         #   pg_search + pgvector extensions
    ├── V3__user_search_and_keycloak_mapping.sql  # User profile + BM25 index + KC shadow tables
    ├── V4__realm_management_and_user_realm.sql   # User-realm link, SPI config, storage providers
    ├── V5__application_roles.sql           #   App roles, custom client roles, flow flags
    ├── V6__audit_trail_and_user_clients.sql  # Entity action log, user-client associations
    ├── V7__paired_client_id.sql            #   Paired frontend/backend client link
    └── V8__client_role_default.sql         #   Default role per client for onboarding
```

## Keycloak SPI (`keycloak-spi/`)

A read-only User Storage Provider that validates users against the Auth Backend.

```
keycloak-spi/
├── build.gradle.kts                       # Shadow JAR build (fat JAR, relocated Kotlin)
└── src/main/kotlin/.../spi/
    ├── ExternalUser.kt                    # User data class from REST API
    ├── ExternalUserAdapter.kt             # Maps ExternalUser → Keycloak UserModel
    ├── ExternalUserStorageProvider.kt      # getUserByEmail → GET /api/users/{email}
    └── ExternalUserStorageProviderFactory.kt  # SPI factory (registered with Keycloak)
```

**Entry point**: Keycloak calls `ExternalUserStorageProvider.getUserByEmail()` during login.

## SCIM Common (`scim-common/`)

Shared DTOs used by both the auth backend and the Forge SDK.

```
scim-common/
├── build.gradle.kts
└── src/main/kotlin/.../scim/common/
    ├── ScimUserResource.kt                # SCIM User representation
    ├── ScimListResponse.kt                # Paginated list response
    ├── ScimBulkRequest.kt / ScimBulkResponse.kt  # Bulk operations
    ├── ScimErrorResponse.kt               # RFC 7644 error format
    └── ScimChecksumUtil.kt                # Deterministic SHA-256 checksum algorithm
```

## Forge SDK (`forge/`)

Spring Boot Starter for SCIM 2.0 user provisioning.

```
forge/
├── build.gradle.kts                       # Spring Boot Starter, depends on scim-common
└── src/main/kotlin/.../forge/
    ├── AuthAutoConfiguration.kt           # Auto-configures WebClient, ScimClient, StartupSyncRunner
    ├── AuthProperties.kt                  # forge.base-url, forge.realm-name, etc.
    ├── AuthUser.kt                        # Abstract base class for user entities
    ├── AuthRepository.kt                  # Generic CRUD + bulk operations interface
    ├── DefaultAuthRepository.kt           # Default implementation (AuthUser ↔ ScimUserResource)
    ├── ScimClient.kt                      # Low-level SCIM HTTP client
    ├── AuthException.kt                   # Exception wrapping SCIM errors
    ├── EnableAuth.kt                      # Optional @Enable annotation
    └── sync/
        ├── AuthStartupSync.kt            # Abstract class: provide local users + mapping
        └── StartupSyncRunner.kt           # ApplicationRunner: checksum → diff → bulk sync
```

**Entry point**: Add `forge` dependency → auto-configuration creates `ScimClient` bean → use `AuthRepository` for CRUD.

## Vue 3 Admin Portal (`web/`)

Frontend for managing Keycloak entities.

```
web/
├── src/
│   ├── api/                               # Axios-based API clients
│   │   ├── client.ts                      #   Base axios instance with auth interceptors
│   │   ├── clients.ts                     #   Client CRUD + applications
│   │   ├── audit.ts                       #   Audit trail queries
│   │   └── ...
│   ├── components/                        # Reusable Vue components
│   │   ├── ClientCard.vue                 #   Client display card
│   │   ├── ClientList.vue                 #   Card grid for clients
│   │   ├── AuditTimeline.vue              #   Timeline view for audit logs
│   │   └── AuditDiffViewer.vue            #   Before/after diff display
│   ├── pages/                             # File-based routing (unplugin-vue-router)
│   │   └── realms/[name]/                 #   Dynamic realm routes
│   ├── stores/                            # Pinia state management
│   ├── types/                             # TypeScript interfaces
│   └── utils/
│       └── urlTransform.ts                # URL auto-formatting for redirect URIs
├── package.json
├── vite.config.ts
└── tsconfig.json
```

## CI/CD

```
.github/workflows/
├── keycloak-spi.yml                       # Build + release SPI JAR
├── auth-sdk-publish.yml                   # Build + publish Forge SDK to GitHub Packages
└── deploy-docs.yml                        # Build + deploy MkDocs to GitHub Pages

.woodpecker/
├── auth.yml                               # Build auth backend on push
├── auth-release.yml                       # Release auth backend (Docker image)
├── keycloak-spi-release.yml               # Deploy SPI from GitHub Release
├── release-all.yml                        # Combined release
├── deploy.yml                             # Manual deploy
└── README.md                              # Pipeline documentation
```

## Key Entry Points

| What you want to do | Start here |
|---------------------|-----------|
| Understand route authorization | `config/SecurityConfig.kt` |
| Add a new REST endpoint | `realm/controller/` or `client/controller/` |
| Add a new database table | `src/main/resources/db/migration/V{N}__name.sql` + `domain/entity/` + `domain/repository/` |
| Understand JWT validation | `config/JwtAuthorityConverter.kt` + `security/SuperAdminAuthorizationManager.kt` |
| Understand Keycloak sync | `keycloak/sync/KeycloakSyncService.kt` |
| Add a SCIM endpoint | `scim/` package |
| Modify the admin frontend | `web/src/pages/` |
| Build the SPI JAR | `./gradlew :keycloak-spi:shadowJar` |
| Publish the SDK | `./gradlew :forge:publish` |

## Request Lifecycle

```
1. HTTP Request arrives at Netty (Spring WebFlux)
2. SecurityWebFilterChain:
   a. Extract JWT from Authorization header
   b. Validate issuer matches KEYCLOAK_ISSUER_PREFIX
   c. Fetch JWK Set from Keycloak (cached)
   d. Verify signature and claims
   e. JwtAuthorityConverter: extract realm_access.roles → ROLE_* authorities
   f. Route-level authorization (SuperAdminAuthorizationManager for /api/super/**)
3. Controller: validate request, map DTO
4. Service: business logic
   a. Database operations via R2DBC repository
   b. Keycloak Admin API calls (if needed)
   c. Audit trail logging (if entity mutation)
5. Response serialized as JSON
```
