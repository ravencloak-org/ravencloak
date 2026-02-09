# Usage Guide

This guide covers common patterns and recipes for using the Forge SDK.

## Look Up a User

### By email

```kotlin
@Service
class UserService(private val userRepo: ForgeUserRepository<AppUser>) {

    suspend fun findByEmail(email: String): AppUser? {
        return userRepo.findByEmail(email)
    }
}
```

### By ID

```kotlin
suspend fun findById(id: String): AppUser? {
    return userRepo.findById(id)
}
```

### With a SCIM filter

For more complex lookups, use `findAll` with a [SCIM filter](#scim-filters):

```kotlin
suspend fun findActiveUsers(): List<ScimUserResource> {
    val response = userRepo.findAll(filter = "active eq true")
    return response.resources
}
```

## Create a User

```kotlin
suspend fun createUser(email: String, firstName: String, lastName: String): AppUser {
    val user = AppUser().apply {
        this.email = email
        this.firstName = firstName
        this.lastName = lastName
        this.displayName = "$firstName $lastName"
        this.active = true
    }
    return userRepo.create(user)
}
```

The returned `AppUser` will have `id`, `createdAt`, and `updatedAt` populated by the server.

**Required fields:** Only `email` (mapped to `userName`) is required. All other fields are optional.

## Update a User

### Full replace

`update()` replaces the entire user resource. Any fields not set on the object will be cleared:

```kotlin
suspend fun updateUser(id: String, newDisplayName: String): AppUser {
    val user = userRepo.findById(id) ?: throw NotFoundException("User $id not found")
    user.displayName = newDisplayName
    return userRepo.update(user)
}
```

!!! warning
    `update()` performs a full replace (HTTP PUT). Always fetch the current user first to avoid unintentionally clearing fields.

### Partial patch

`patch()` modifies only the specified fields:

```kotlin
suspend fun deactivateUser(id: String): AppUser {
    return userRepo.patch(id, listOf(
        ScimPatchOperation(op = "replace", path = "active", value = false)
    ))
}
```

```kotlin
suspend fun updateName(id: String, firstName: String, lastName: String): AppUser {
    return userRepo.patch(id, listOf(
        ScimPatchOperation(op = "replace", path = "name.givenName", value = firstName),
        ScimPatchOperation(op = "replace", path = "name.familyName", value = lastName)
    ))
}
```

## Delete a User

```kotlin
suspend fun deleteUser(id: String) {
    try {
        userRepo.delete(id)
    } catch (e: ForgeException) {
        if (e.status == 404) {
            // User already deleted — safe to ignore
        } else {
            throw e
        }
    }
}
```

## Pagination

The SCIM API uses 1-based indexing. Use `startIndex` and `count` to paginate:

```kotlin
suspend fun listAllUsers(): List<ScimUserResource> {
    val allUsers = mutableListOf<ScimUserResource>()
    var startIndex = 1
    val pageSize = 50

    do {
        val response = userRepo.findAll(startIndex = startIndex, count = pageSize)
        allUsers.addAll(response.resources)
        startIndex += response.itemsPerPage
    } while (allUsers.size < response.totalResults)

    return allUsers
}
```

| Parameter | Description |
|-----------|-------------|
| `startIndex` | 1-based index of the first result (default: `1`) |
| `count` | Maximum results per page (default: `100`) |
| `totalResults` | Total matching users (returned in response) |
| `itemsPerPage` | Actual number of results in this page |

## SCIM Filters

The `filter` parameter accepts [SCIM filter syntax](https://datatracker.ietf.org/doc/html/rfc7644#section-3.4.2.2):

| Operator | Example | Description |
|----------|---------|-------------|
| `eq` | `userName eq "john@example.com"` | Equals |
| `ne` | `active ne false` | Not equals |
| `co` | `displayName co "John"` | Contains |
| `sw` | `userName sw "john"` | Starts with |
| `ew` | `userName ew "@example.com"` | Ends with |
| `gt`, `ge`, `lt`, `le` | `meta.created gt "2024-01-01"` | Comparison |

### Combining filters

```kotlin
// AND — both conditions must match
val filter = "active eq true and name.familyName eq \"Smith\""

// OR — either condition matches
val filter = "userName eq \"alice@example.com\" or userName eq \"bob@example.com\""
```

## Error Handling

All API errors throw `ForgeException`:

```kotlin
suspend fun safeGetUser(id: String): AppUser? {
    return try {
        userRepo.findById(id)
    } catch (e: ForgeException) {
        when (e.status) {
            404 -> null
            401, 403 -> throw SecurityException("Not authorized", e)
            else -> throw e
        }
    }
}
```

### Inspecting the SCIM error

```kotlin
try {
    userRepo.create(user)
} catch (e: ForgeException) {
    val scimError = e.scimError
    if (scimError != null) {
        println("Status: ${scimError.status}")
        println("Type: ${scimError.scimType}")
        println("Detail: ${scimError.detail}")
    }
}
```

## Repository Pattern

### Define your domain class

Extend `ForgeUser` with application-specific fields:

```kotlin
class AppUser : ForgeUser() {
    // Add custom fields as needed
    var department: String? = null
    var employeeId: String? = null
}
```

### Register the repository bean

```kotlin
@Configuration
class UserConfig {

    @Bean
    fun appUserRepository(scimClient: ScimClient): ForgeUserRepository<AppUser> =
        DefaultForgeUserRepository(scimClient) { AppUser() }
}
```

The factory lambda `{ AppUser() }` creates new instances when mapping from SCIM responses. Standard `ForgeUser` fields are automatically mapped; custom fields need manual handling.

### Inject and use

```kotlin
@Service
class UserService(private val userRepo: ForgeUserRepository<AppUser>) {

    suspend fun getUser(id: String): AppUser? = userRepo.findById(id)

    suspend fun createUser(email: String, name: String): AppUser {
        val user = AppUser().apply {
            this.email = email
            this.displayName = name
        }
        return userRepo.create(user)
    }
}
```

## Low-Level ScimClient

Use `ScimClient` directly when you need full control over SCIM resources:

```kotlin
@Service
class ScimService(private val scimClient: ScimClient) {

    suspend fun createUserWithEmails(
        email: String,
        firstName: String,
        personalEmail: String
    ): ScimUserResource {
        return scimClient.createUser(
            ScimUserResource(
                userName = email,
                name = ScimName(givenName = firstName),
                emails = listOf(
                    ScimEmail(value = email, type = "work", primary = true),
                    ScimEmail(value = personalEmail, type = "home", primary = false)
                ),
                active = true
            )
        )
    }

    suspend fun patchUserTitle(userId: UUID, title: String): ScimUserResource {
        return scimClient.patchUser(
            userId,
            ScimPatchRequest(
                operations = listOf(
                    ScimPatchOperation(op = "replace", path = "title", value = title)
                )
            )
        )
    }
}
```

### When to use ScimClient vs ForgeUserRepository

| Use case | Recommended |
|----------|-------------|
| Simple CRUD with domain objects | `ForgeUserRepository` |
| Multiple email addresses or phone numbers | `ScimClient` |
| Custom SCIM attributes | `ScimClient` |
| SCIM PATCH with multiple operations | Either (repository wraps patch) |
| Pagination over all users | Either |
