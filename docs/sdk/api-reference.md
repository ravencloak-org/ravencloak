# API Reference

## ScimClient

The primary low-level client for the SCIM 2.0 API. All methods are `suspend` functions.

```kotlin
class ScimClient(webClient: WebClient, properties: AuthProperties)
```

### Methods

#### `listUsers`

```kotlin
suspend fun listUsers(
    filter: String? = null,
    startIndex: Int = 1,
    count: Int = 100
): ScimListResponse
```

Lists users with optional SCIM filtering and pagination.

| Parameter | Type | Default | Description |
|-----------|------|---------|-------------|
| `filter` | `String?` | `null` | SCIM filter expression (e.g., `userName eq "john@example.com"`) |
| `startIndex` | `Int` | `1` | 1-based index of the first result |
| `count` | `Int` | `100` | Maximum number of results per page |

#### `getUser`

```kotlin
suspend fun getUser(userId: UUID): ScimUserResource
```

Retrieves a single user by ID. Throws `AuthException` if not found.

#### `createUser`

```kotlin
suspend fun createUser(resource: ScimUserResource): ScimUserResource
```

Creates a new user. Returns the created resource with server-assigned `id` and `meta`.

#### `replaceUser`

```kotlin
suspend fun replaceUser(userId: UUID, resource: ScimUserResource): ScimUserResource
```

Fully replaces a user resource (HTTP PUT). All fields not included will be cleared.

#### `patchUser`

```kotlin
suspend fun patchUser(userId: UUID, patchRequest: ScimPatchRequest): ScimUserResource
```

Applies partial modifications to a user (HTTP PATCH). Only specified fields are changed.

#### `deleteUser`

```kotlin
suspend fun deleteUser(userId: UUID)
```

Deletes a user by ID. Throws `AuthException` if not found.

#### `bulkRequest`

```kotlin
suspend fun bulkRequest(request: ScimBulkRequest): ScimBulkResponse
```

Sends a bulk request containing multiple create (POST) and/or update (PUT) operations. Each operation is processed independently — one failure does not block others.

#### `getChecksum`

```kotlin
suspend fun getChecksum(): ScimChecksumResponse
```

Returns a SHA-256 checksum of all users in the configured realm, along with the user count. Used by the [startup sync](startup-sync.md) mechanism to detect drift.

---

## AuthRepository

A higher-level repository interface that maps between `AuthUser` domain objects and SCIM resources.

```kotlin
interface AuthRepository<T : AuthUser>
```

!!! note "Migration from ForgeUserRepository"
    `AuthRepository` was previously named `ForgeUserRepository`. The old name is still available as a `@Deprecated` typealias for backward compatibility.

### Methods

#### `findById`

```kotlin
suspend fun findById(id: String): T?
```

Retrieves a user by ID. Returns `null` if not found.

#### `findByEmail`

```kotlin
suspend fun findByEmail(email: String): T?
```

Searches for a user by email using a SCIM filter (`userName eq "email"`). Returns `null` if not found.

#### `findAll`

```kotlin
suspend fun findAll(
    filter: String? = null,
    startIndex: Int = 1,
    count: Int = 100
): ScimListResponse
```

Lists users with optional filtering and pagination. Returns the raw `ScimListResponse`.

#### `create`

```kotlin
suspend fun create(user: T): T
```

Creates a new user. Returns the persisted instance with `id`, `createdAt`, and `updatedAt` populated.

#### `update`

```kotlin
suspend fun update(user: T): T
```

Fully replaces an existing user. Requires `user.id` to be non-null.

#### `createAll`

```kotlin
suspend fun createAll(users: List<T>): ScimBulkResponse
```

Creates multiple users in a single bulk request. Each user is sent as a POST operation. Returns a `ScimBulkResponse` with per-operation status codes.

#### `updateAll`

```kotlin
suspend fun updateAll(users: List<T>): ScimBulkResponse
```

Updates multiple users in a single bulk request. Each user is sent as a PUT operation. All users must have a non-null `id`. Returns a `ScimBulkResponse` with per-operation status codes.

#### `patch`

```kotlin
suspend fun patch(id: String, operations: List<ScimPatchOperation>): T
```

Applies partial modifications via SCIM PATCH operations.

#### `delete`

```kotlin
suspend fun delete(id: String)
```

Deletes a user by ID.

---

## DefaultAuthRepository

The default implementation of `AuthRepository`. Created with a `ScimClient` and a factory function for your domain type.

```kotlin
class DefaultAuthRepository<T : AuthUser>(
    scimClient: ScimClient,
    factory: () -> T
) : AuthRepository<T>
```

The `factory` lambda creates new instances of your `AuthUser` subclass. The repository handles all mapping between your domain type and `ScimUserResource`.

!!! note "Migration from DefaultForgeUserRepository"
    `DefaultAuthRepository` was previously named `DefaultForgeUserRepository`. The old name is still available as a `@Deprecated` typealias.

**Field mapping:**

| AuthUser field | ScimUserResource field |
|----------------|----------------------|
| `id` | `id` |
| `externalId` | `externalId` |
| `email` | `userName` |
| `firstName` | `name.givenName` |
| `lastName` | `name.familyName` |
| `displayName` | `displayName` |
| `phone` | `phoneNumbers[0].value` |
| `jobTitle` | `title` |
| `active` | `active` |
| `createdAt` | `meta.created` |
| `updatedAt` | `meta.lastModified` |

---

## AuthUser

Abstract base class for user domain objects. Extend this to add application-specific fields.

```kotlin
abstract class AuthUser {
    open var id: String? = null
    open var externalId: String? = null
    open var email: String = ""
    open var firstName: String? = null
    open var lastName: String? = null
    open var displayName: String? = null
    open var phone: String? = null
    open var jobTitle: String? = null
    open var active: Boolean = true
    open var createdAt: Instant? = null
    open var updatedAt: Instant? = null
}
```

All properties are `open` so subclasses can override them with custom accessors or annotations.

!!! note "Migration from ForgeUser"
    `AuthUser` was previously named `ForgeUser`. The old name is still available as a `@Deprecated` typealias.

---

## DTOs (scim-common)

Shared data transfer objects from the `scim-common` module. All classes use `@JsonInclude(NON_NULL)` — null fields are omitted from JSON serialization.

### ScimUserResource

```kotlin
data class ScimUserResource(
    val schemas: List<String> = listOf(ScimSchemas.USER),
    val id: String? = null,
    val externalId: String? = null,
    val userName: String,                    // required
    val name: ScimName? = null,
    val displayName: String? = null,
    val emails: List<ScimEmail>? = null,
    val phoneNumbers: List<ScimPhoneNumber>? = null,
    val title: String? = null,
    val active: Boolean = true,
    val meta: ScimMeta? = null
)
```

### ScimName

```kotlin
data class ScimName(
    val givenName: String? = null,
    val familyName: String? = null,
    val formatted: String? = null
)
```

### ScimEmail

```kotlin
data class ScimEmail(
    val value: String,                       // required
    val type: String = "work",
    val primary: Boolean = true
)
```

### ScimPhoneNumber

```kotlin
data class ScimPhoneNumber(
    val value: String,                       // required
    val type: String = "work"
)
```

### ScimMeta

```kotlin
data class ScimMeta(
    val resourceType: String = "User",
    val created: String? = null,             // ISO-8601 timestamp
    val lastModified: String? = null,        // ISO-8601 timestamp
    val location: String? = null             // resource URI
)
```

### ScimListResponse

```kotlin
data class ScimListResponse(
    val schemas: List<String> = listOf(ScimSchemas.LIST_RESPONSE),
    val totalResults: Int,
    val startIndex: Int = 1,
    val itemsPerPage: Int,
    @JsonProperty("Resources")
    val resources: List<ScimUserResource>
)
```

### ScimBulkRequest

```kotlin
data class ScimBulkRequest(
    val schemas: List<String> = listOf(ScimSchemas.BULK_REQUEST),
    @JsonProperty("Operations")
    val operations: List<ScimBulkOperation>
)
```

### ScimBulkOperation

```kotlin
data class ScimBulkOperation(
    val method: String,           // "POST" or "PUT"
    val path: String? = null,     // "/Users" for POST, "/Users/{id}" for PUT
    val bulkId: String? = null,   // correlation ID
    val data: ScimUserResource? = null
)
```

### ScimBulkResponse

```kotlin
data class ScimBulkResponse(
    val schemas: List<String> = listOf(ScimSchemas.BULK_RESPONSE),
    @JsonProperty("Operations")
    val operations: List<ScimBulkOperationResponse>
)
```

### ScimBulkOperationResponse

```kotlin
data class ScimBulkOperationResponse(
    val method: String,
    val bulkId: String? = null,
    val location: String? = null,
    val status: String,           // "201", "200", "409", etc.
    val response: Any? = null     // ScimUserResource or ScimErrorResponse
)
```

### ScimChecksumResponse

```kotlin
data class ScimChecksumResponse(
    val checksum: String,         // SHA-256 hex string
    val userCount: Int
)
```

### ScimPatchRequest

```kotlin
data class ScimPatchRequest(
    val schemas: List<String> = listOf(ScimSchemas.PATCH_OP),
    @JsonProperty("Operations")
    val operations: List<ScimPatchOperation>
)
```

### ScimPatchOperation

```kotlin
data class ScimPatchOperation(
    val op: String,                          // "add", "remove", or "replace"
    val path: String? = null,
    val value: Any? = null
)
```

### ScimErrorResponse

```kotlin
data class ScimErrorResponse(
    val schemas: List<String> = listOf(ScimSchemas.ERROR),
    val status: String,                      // HTTP status code as string
    val scimType: String? = null,
    val detail: String? = null
)
```

### ScimSchemas

Constants for SCIM 2.0 schema URIs:

```kotlin
object ScimSchemas {
    const val USER = "urn:ietf:params:scim:schemas:core:2.0:User"
    const val LIST_RESPONSE = "urn:ietf:params:scim:api:messages:2.0:ListResponse"
    const val ERROR = "urn:ietf:params:scim:api:messages:2.0:Error"
    const val PATCH_OP = "urn:ietf:params:scim:api:messages:2.0:PatchOp"
    const val SERVICE_PROVIDER_CONFIG = "urn:ietf:params:scim:schemas:core:2.0:ServiceProviderConfig"
    const val SCHEMA = "urn:ietf:params:scim:schemas:core:2.0:Schema"
    const val RESOURCE_TYPE = "urn:ietf:params:scim:schemas:core:2.0:ResourceType"
    const val BULK_REQUEST = "urn:ietf:params:scim:api:messages:2.0:BulkRequest"
    const val BULK_RESPONSE = "urn:ietf:params:scim:api:messages:2.0:BulkResponse"
}
```

---

## AuthException

Thrown when the SCIM API returns an error response.

```kotlin
class AuthException(
    val status: Int,
    val scimError: ScimErrorResponse? = null,
    message: String? = scimError?.detail ?: "Auth SDK error (HTTP $status)",
    cause: Throwable? = null
) : RuntimeException(message, cause)
```

| Field | Type | Description |
|-------|------|-------------|
| `status` | `Int` | HTTP status code from the API response |
| `scimError` | `ScimErrorResponse?` | Parsed SCIM error body, if available |
| `message` | `String?` | Uses `scimError.detail` if present, otherwise `"Auth SDK error (HTTP $status)"` |
| `cause` | `Throwable?` | Underlying exception, if any |

!!! note "Migration from ForgeException"
    `AuthException` was previously named `ForgeException`. The old name is still available as a `@Deprecated` typealias.
