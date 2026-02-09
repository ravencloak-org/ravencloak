# SCIM 2.0 Implementation

This module implements [SCIM 2.0](https://datatracker.ietf.org/doc/html/rfc7644) (System for Cross-domain Identity Management) endpoints for user provisioning and deprovisioning.

## API Versioning

This project uses **Spring Framework 7's native API versioning** with header-based resolution.

| Header | Value | Description |
|--------|-------|-------------|
| `API-Version` | `1.0` | Current SCIM API version |

All SCIM endpoints require the `API-Version` header. Requests without it (or with unsupported versions) receive a `400 Bad Request` response.

Existing (non-SCIM) endpoints are **not affected** — they continue to work without any version header.

## Endpoints

Base path: `/api/scim/v2`

| Method | Path | Description | SCIM Spec |
|--------|------|-------------|-----------|
| `GET` | `/Users` | List/search users | RFC 7644 §3.4.2 |
| `GET` | `/Users/{id}` | Get user by ID | RFC 7644 §3.4.1 |
| `POST` | `/Users` | Create user | RFC 7644 §3.3 |
| `PUT` | `/Users/{id}` | Replace user | RFC 7644 §3.5.1 |
| `PATCH` | `/Users/{id}` | Partial update | RFC 7644 §3.5.2 |
| `DELETE` | `/Users/{id}` | Delete user | RFC 7644 §3.6 |
| `GET` | `/ServiceProviderConfig` | Provider capabilities | RFC 7644 §4 |
| `GET` | `/Schemas` | All schemas | RFC 7644 §4 |
| `GET` | `/ResourceTypes` | Resource type definitions | RFC 7644 §4 |

## Authentication

SCIM endpoints require a valid OAuth2 Bearer token (JWT). Tokens are validated against Keycloak using the same multi-issuer resolution as other API endpoints.

```bash
curl -H "Authorization: Bearer <token>" \
     -H "API-Version: 1.0" \
     https://auth.example.com/api/scim/v2/Users
```

## SCIM User Resource Schema

The SCIM User resource maps to the internal `User` entity:

```json
{
  "schemas": ["urn:ietf:params:scim:schemas:core:2.0:User"],
  "id": "550e8400-e29b-41d4-a716-446655440000",
  "userName": "john@example.com",
  "name": {
    "givenName": "John",
    "familyName": "Doe",
    "formatted": "John Doe"
  },
  "displayName": "John Doe",
  "emails": [
    {
      "value": "john@example.com",
      "type": "work",
      "primary": true
    }
  ],
  "phoneNumbers": [
    {
      "value": "+1-555-0100",
      "type": "work"
    }
  ],
  "title": "Software Engineer",
  "active": true,
  "meta": {
    "resourceType": "User",
    "created": "2024-01-15T10:30:00Z",
    "lastModified": "2024-06-20T14:22:00Z",
    "location": "https://auth.example.com/api/scim/v2/Users/550e8400-e29b-41d4-a716-446655440000"
  }
}
```

### Field Mapping

| SCIM Attribute | User Entity Field | Notes |
|----------------|-------------------|-------|
| `id` | `id` | UUID |
| `externalId` | `keycloakUserId` | Keycloak user ID |
| `userName` | `email` | Unique identifier |
| `name.givenName` | `firstName` | |
| `name.familyName` | `lastName` | |
| `displayName` | `displayName` | |
| `emails[0].value` | `email` | Primary email |
| `phoneNumbers[0].value` | `phone` | |
| `title` | `jobTitle` | |
| `active` | `status` | `true` = ACTIVE |
| `meta.created` | `createdAt` | |
| `meta.lastModified` | `updatedAt` | |

## SCIM Filter Syntax

The `GET /Users` endpoint supports SCIM filter expressions (RFC 7644 §3.4.2.2):

```bash
# Filter by email
GET /api/scim/v2/Users?filter=userName eq "john@example.com"

# Filter by active status
GET /api/scim/v2/Users?filter=active eq true

# Filter by name (contains)
GET /api/scim/v2/Users?filter=name.familyName co "Doe"

# Combine filters
GET /api/scim/v2/Users?filter=active eq true and name.givenName sw "J"
```

### Supported Filter Operators

| Operator | Description | Example |
|----------|-------------|---------|
| `eq` | Equal | `userName eq "john@example.com"` |
| `ne` | Not equal | `active ne false` |
| `co` | Contains | `displayName co "John"` |
| `sw` | Starts with | `userName sw "john"` |
| `ew` | Ends with | `userName ew "@example.com"` |
| `gt` | Greater than | `meta.created gt "2024-01-01T00:00:00Z"` |
| `ge` | Greater than or equal | — |
| `lt` | Less than | — |
| `le` | Less than or equal | — |
| `and` | Logical AND | `active eq true and title co "Engineer"` |
| `or` | Logical OR | `userName eq "a@b.com" or userName eq "c@d.com"` |

### Pagination

```bash
# Page through results (1-indexed)
GET /api/scim/v2/Users?startIndex=1&count=25
```

## SCIM Error Format

Errors follow RFC 7644 §3.12:

```json
{
  "schemas": ["urn:ietf:params:scim:api:messages:2.0:Error"],
  "status": "404",
  "scimType": "invalidValue",
  "detail": "User not found"
}
```

## Client SDK (`forge`)

A Spring Boot Starter that wraps the SCIM API for client applications. Package: `com.keeplearning.forge`. Shared DTOs live in the `scim-common` module.

### Gradle Dependency

```kotlin
repositories {
    maven {
        url = uri("https://maven.pkg.github.com/dsjkeeplearning/kos-auth-backend")
        credentials {
            username = System.getenv("GITHUB_ACTOR")
            password = System.getenv("GITHUB_TOKEN")
        }
    }
}

dependencies {
    implementation("com.keeplearning:forge:0.1.0")
}
```

### Configuration

```yaml
forge:
  base-url: https://auth.example.com
  realm-name: my-realm
  client-registration-id: forge  # OAuth2 client registration
  api-version: "1.0"
```

### Usage

```kotlin
@Service
class UserService(private val scimClient: ScimClient) {

    suspend fun findUser(email: String): ScimUserResource? {
        val response = scimClient.listUsers(filter = "userName eq \"$email\"", count = 1)
        return response.resources.firstOrNull()
    }

    suspend fun createUser(email: String, firstName: String, lastName: String): ScimUserResource {
        return scimClient.createUser(
            ScimUserResource(
                userName = email,
                name = ScimName(givenName = firstName, familyName = lastName),
                active = true
            )
        )
    }
}
```

### Repository Pattern

For entity-mapped access, use `DefaultAuthRepository`:

```kotlin
class AppUser : AuthUser()

@Bean
fun appUserRepository(scimClient: ScimClient) =
    DefaultAuthRepository(scimClient) { AppUser() }
```

## References

- [RFC 7643 — SCIM Core Schema](https://datatracker.ietf.org/doc/html/rfc7643)
- [RFC 7644 — SCIM Protocol](https://datatracker.ietf.org/doc/html/rfc7644)
- [RFC 7642 — SCIM Definitions, Overview, Concepts](https://datatracker.ietf.org/doc/html/rfc7642)
- [UnboundID SCIM 2 SDK](https://github.com/pingidentity/scim2)
- [Spring Framework 7 API Versioning](https://docs.spring.io/spring-framework/reference/web/webflux/config.html#webflux-config-api-version)
