# SCIM 2.0 Provisioning API

This module implements [SCIM 2.0](https://datatracker.ietf.org/doc/html/rfc7644) (System for Cross-domain Identity Management) endpoints for user provisioning and deprovisioning.

## API Versioning

Header-based versioning via Spring Framework 7:

| Header | Value | Description |
|--------|-------|-------------|
| `API-Version` | `1.0` | Current SCIM API version |

All SCIM endpoints require the `API-Version` header. Requests without it receive a `400 Bad Request` response. Existing non-SCIM endpoints are not affected.

## Endpoints

Base path: `/api/scim/v2`

| Method | Path | Description | SCIM Spec |
|--------|------|-------------|-----------|
| `GET` | `/realms/{realm}/Users` | List/search users | RFC 7644 §3.4.2 |
| `GET` | `/realms/{realm}/Users/{id}` | Get user by ID | RFC 7644 §3.4.1 |
| `POST` | `/realms/{realm}/Users` | Create user | RFC 7644 §3.3 |
| `PUT` | `/realms/{realm}/Users/{id}` | Replace user | RFC 7644 §3.5.1 |
| `PATCH` | `/realms/{realm}/Users/{id}` | Partial update | RFC 7644 §3.5.2 |
| `DELETE` | `/realms/{realm}/Users/{id}` | Delete user | RFC 7644 §3.6 |
| `POST` | `/realms/{realm}/Bulk` | Bulk create/update | RFC 7644 §3.7 |
| `GET` | `/realms/{realm}/Users/checksum` | Checksum of all users | Custom |
| `GET` | `/ServiceProviderConfig` | Provider capabilities | RFC 7644 §4 |
| `GET` | `/Schemas` | All schemas | RFC 7644 §4 |
| `GET` | `/ResourceTypes` | Resource type definitions | RFC 7644 §4 |

## Authentication

SCIM endpoints require a valid OAuth2 Bearer token (JWT):

```bash
curl -H "Authorization: Bearer <token>" \
     -H "API-Version: 1.0" \
     https://auth.example.com/api/scim/v2/Users
```

## User Resource Schema

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
    "location": "https://auth.example.com/api/scim/v2/Users/550e8400-..."
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

## Filter Syntax

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

### Supported Operators

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

## Bulk Operations

The `POST /Bulk` endpoint processes multiple create and update operations in a single request (RFC 7644 §3.7). Each operation is independent — one failure does not block others.

```bash
curl -X POST \
  -H "Authorization: Bearer <token>" \
  -H "API-Version: 1.0" \
  -H "Content-Type: application/json" \
  https://auth.example.com/api/scim/v2/realms/my-realm/Bulk \
  -d '{
    "schemas": ["urn:ietf:params:scim:api:messages:2.0:BulkRequest"],
    "Operations": [
      {
        "method": "POST",
        "path": "/Users",
        "bulkId": "user-1",
        "data": {
          "schemas": ["urn:ietf:params:scim:schemas:core:2.0:User"],
          "userName": "alice@example.com",
          "name": { "givenName": "Alice", "familyName": "Smith" },
          "active": true
        }
      },
      {
        "method": "PUT",
        "path": "/Users/550e8400-e29b-41d4-a716-446655440000",
        "bulkId": "user-2",
        "data": {
          "schemas": ["urn:ietf:params:scim:schemas:core:2.0:User"],
          "userName": "bob@example.com",
          "displayName": "Bob Jones (Updated)",
          "active": true
        }
      }
    ]
  }'
```

### Bulk Response

Each operation returns its own status code:

```json
{
  "schemas": ["urn:ietf:params:scim:api:messages:2.0:BulkResponse"],
  "Operations": [
    {
      "method": "POST",
      "bulkId": "user-1",
      "status": "201",
      "location": "https://auth.example.com/api/scim/v2/Users/...",
      "response": { "id": "...", "userName": "alice@example.com", ... }
    },
    {
      "method": "PUT",
      "bulkId": "user-2",
      "status": "200",
      "response": { "id": "550e8400-...", "userName": "bob@example.com", ... }
    }
  ]
}
```

| Supported Method | Description |
|------------------|-------------|
| `POST` | Create a new user (delegates to `POST /Users`) |
| `PUT` | Replace an existing user (delegates to `PUT /Users/{id}`) |

## User Checksum

The `GET /Users/checksum` endpoint returns a deterministic SHA-256 checksum of all users in a realm, useful for detecting drift between a local database and the auth backend.

```bash
curl -H "Authorization: Bearer <token>" \
     -H "API-Version: 1.0" \
     https://auth.example.com/api/scim/v2/realms/my-realm/Users/checksum
```

```json
{
  "checksum": "a3f2b8c91d4e5f6a7b8c9d0e1f2a3b4c5d6e7f8a9b0c1d2e3f4a5b6c7d8e9f0",
  "userCount": 42
}
```

The checksum algorithm:

1. For each user, produce a canonical string: `email|externalId|firstName|lastName|displayName|phone|title|active` (nulls become empty strings)
2. Sort all strings by email (case-insensitive)
3. Join with newline
4. SHA-256 hash (hex-encoded)

This same algorithm is used by the Forge SDK's [startup sync](../sdk/startup-sync.md) mechanism.

## Error Format

Errors follow RFC 7644 §3.12:

```json
{
  "schemas": ["urn:ietf:params:scim:api:messages:2.0:Error"],
  "status": "404",
  "scimType": "invalidValue",
  "detail": "User not found"
}
```

## Key Classes

| Class | Purpose |
|-------|---------|
| `ScimUserController` | SCIM User CRUD (`/api/scim/v2/Users`) |
| `ScimBulkController` | Bulk operations (`/api/scim/v2/realms/{realm}/Bulk`) |
| `ScimChecksumController` | User checksum (`/api/scim/v2/realms/{realm}/Users/checksum`) |
| `ScimDiscoveryController` | ServiceProviderConfig, Schemas, ResourceTypes |
| `ScimUserService` | Business logic wrapping UserRepository |
| `ScimBulkService` | Processes bulk create/update operations |
| `ScimChecksumService` | Computes deterministic user checksum |
| `ScimUserMapper` | User entity to/from SCIM resource mapping |
| `ScimFilterTranslator` | SCIM filter to R2DBC SQL translation |
| `ScimExceptionHandler` | RFC 7644 §3.12 error responses |

## References

- [RFC 7643 — SCIM Core Schema](https://datatracker.ietf.org/doc/html/rfc7643)
- [RFC 7644 — SCIM Protocol](https://datatracker.ietf.org/doc/html/rfc7644)
- [RFC 7642 — SCIM Definitions, Overview, Concepts](https://datatracker.ietf.org/doc/html/rfc7642)
