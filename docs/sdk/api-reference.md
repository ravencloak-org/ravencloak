# API Reference

## ScimClient

The primary low-level client for the SCIM 2.0 API. All methods are `suspend` functions.

```kotlin
class ScimClient(webClient: WebClient, properties: ForgeProperties)
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

Retrieves a single user by ID. Throws `ForgeException` if not found.

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

Deletes a user by ID. Throws `ForgeException` if not found.

---

## ForgeUserRepository

A higher-level repository interface that maps between `ForgeUser` domain objects and SCIM resources.

```kotlin
interface ForgeUserRepository<T : ForgeUser>
```

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

## DefaultForgeUserRepository

The default implementation of `ForgeUserRepository`. Created with a `ScimClient` and a factory function for your domain type.

```kotlin
class DefaultForgeUserRepository<T : ForgeUser>(
    scimClient: ScimClient,
    factory: () -> T
) : ForgeUserRepository<T>
```

The `factory` lambda creates new instances of your `ForgeUser` subclass. The repository handles all mapping between your domain type and `ScimUserResource`.

**Field mapping:**

| ForgeUser field | ScimUserResource field |
|-----------------|----------------------|
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

## ForgeUser

Abstract base class for user domain objects. Extend this to add application-specific fields.

```kotlin
abstract class ForgeUser {
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
}
```

---

## ForgeException

Thrown when the SCIM API returns an error response.

```kotlin
class ForgeException(
    val status: Int,
    val scimError: ScimErrorResponse? = null,
    message: String? = scimError?.detail ?: "Forge SDK error (HTTP $status)",
    cause: Throwable? = null
) : RuntimeException(message, cause)
```

| Field | Type | Description |
|-------|------|-------------|
| `status` | `Int` | HTTP status code from the API response |
| `scimError` | `ScimErrorResponse?` | Parsed SCIM error body, if available |
| `message` | `String?` | Uses `scimError.detail` if present, otherwise `"Forge SDK error (HTTP $status)"` |
| `cause` | `Throwable?` | Underlying exception, if any |
