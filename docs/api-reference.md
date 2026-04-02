# API Reference

This document provides a complete reference for all REST API endpoints in the KOS Auth Backend. For SCIM 2.0 endpoints, see [SCIM 2.0](api/scim.md).

## Authentication

All endpoints (except public ones) require a valid OAuth2 Bearer token (JWT):

```bash
curl -H "Authorization: Bearer <token>" https://api.example.com/api/...
```

Tokens are issued by Keycloak. The backend accepts tokens from any realm matching the configured `KEYCLOAK_ISSUER_PREFIX`.

## Super Admin Endpoints

These endpoints require a token from the `saas-admin` realm with `ROLE_SUPER_ADMIN`.

### Authentication

| Method | Path | Description |
|--------|------|-------------|
| `GET` | `/auth/super/login` | Initiate OAuth2 login flow for super admin |
| `GET` | `/auth/super/me` | Get current super admin's profile |

### Realm Management

| Method | Path | Description |
|--------|------|-------------|
| `GET` | `/api/super/realms` | List all realms |
| `GET` | `/api/super/realms/{realmName}` | Get realm details |
| `POST` | `/api/super/realms` | Create a new realm |
| `PUT` | `/api/super/realms/{realmName}` | Update realm |
| `DELETE` | `/api/super/realms/{realmName}` | Delete realm |
| `POST` | `/api/super/realms/{realmName}/sync` | Trigger Keycloak sync for realm |

### Client Management

| Method | Path | Description |
|--------|------|-------------|
| `GET` | `/api/super/realms/{realm}/clients` | List clients in realm |
| `GET` | `/api/super/realms/{realm}/clients/{clientId}` | Get client details |
| `POST` | `/api/super/realms/{realm}/clients` | Create client |
| `PUT` | `/api/super/realms/{realm}/clients/{clientId}` | Update client |
| `DELETE` | `/api/super/realms/{realm}/clients/{clientId}` | Delete client |
| `GET` | `/api/super/realms/{realm}/clients/{clientId}/integration-snippets` | Get code snippets |

### Application Management (Paired Clients)

| Method | Path | Description |
|--------|------|-------------|
| `POST` | `/api/super/realms/{realm}/applications` | Create application (paired clients) |
| `GET` | `/api/super/realms/{realm}/applications` | List applications |

**Create Application Request:**

```json
{
  "applicationName": "my-app",
  "applicationType": "FULL_STACK",
  "rootUrl": "https://my-app.example.com",
  "redirectUris": ["https://my-app.example.com/*"],
  "webOrigins": ["https://my-app.example.com"]
}
```

Application types: `FRONTEND_ONLY`, `BACKEND_ONLY`, `FULL_STACK`.

### Role Management

| Method | Path | Description |
|--------|------|-------------|
| `GET` | `/api/super/realms/{realm}/roles` | List realm roles |
| `GET` | `/api/super/realms/{realm}/roles/{roleName}` | Get role details |
| `POST` | `/api/super/realms/{realm}/roles` | Create realm role |
| `PUT` | `/api/super/realms/{realm}/roles/{roleName}` | Update role |
| `DELETE` | `/api/super/realms/{realm}/roles/{roleName}` | Delete role |
| `GET` | `/api/super/realms/{realm}/clients/{clientId}/roles` | List client roles |
| `POST` | `/api/super/realms/{realm}/clients/{clientId}/roles` | Create client role |

### Group Management

| Method | Path | Description |
|--------|------|-------------|
| `GET` | `/api/super/realms/{realm}/groups` | List groups (hierarchical) |
| `GET` | `/api/super/realms/{realm}/groups/{groupId}` | Get group details |
| `POST` | `/api/super/realms/{realm}/groups` | Create group |
| `POST` | `/api/super/realms/{realm}/groups/{parentId}/children` | Create child group |
| `PUT` | `/api/super/realms/{realm}/groups/{groupId}` | Update group |
| `DELETE` | `/api/super/realms/{realm}/groups/{groupId}` | Delete group |
| `POST` | `/api/super/realms/{realm}/groups/{groupId}/roles` | Assign roles to group |
| `DELETE` | `/api/super/realms/{realm}/groups/{groupId}/roles` | Remove roles from group |

### Identity Provider Management

| Method | Path | Description |
|--------|------|-------------|
| `GET` | `/api/super/realms/{realm}/identity-providers` | List IDPs |
| `GET` | `/api/super/realms/{realm}/identity-providers/{alias}` | Get IDP details |
| `POST` | `/api/super/realms/{realm}/identity-providers` | Create IDP |
| `PUT` | `/api/super/realms/{realm}/identity-providers/{alias}` | Update IDP |
| `DELETE` | `/api/super/realms/{realm}/identity-providers/{alias}` | Delete IDP |

### User Management

| Method | Path | Description |
|--------|------|-------------|
| `GET` | `/api/super/realms/{realm}/users` | List/search users in realm |
| `GET` | `/api/super/realms/{realm}/users/{userId}` | Get user details |
| `POST` | `/api/super/realms/{realm}/users/{userId}/groups/{groupId}` | Add user to group |
| `DELETE` | `/api/super/realms/{realm}/users/{userId}/groups/{groupId}` | Remove from group |
| `POST` | `/api/super/realms/{realm}/users/{userId}/roles` | Assign roles to user |
| `DELETE` | `/api/super/realms/{realm}/users/{userId}/roles` | Remove roles from user |

### Keycloak User Management (Direct)

| Method | Path | Description |
|--------|------|-------------|
| `POST` | `/api/super/realms/{realm}/keycloak-users` | Create user in Keycloak |
| `PUT` | `/api/super/realms/{realm}/keycloak-users/{userId}` | Update user in Keycloak |
| `DELETE` | `/api/super/realms/{realm}/keycloak-users/{userId}` | Delete user from Keycloak |
| `POST` | `/api/super/realms/{realm}/keycloak-users/{userId}/reset-password` | Reset user password |

### Audit Trail

| Method | Path | Description |
|--------|------|-------------|
| `GET` | `/api/super/realms/{realm}/audit` | List audit logs (filterable) |
| `GET` | `/api/super/realms/{realm}/audit/{id}` | Get single audit entry |
| `POST` | `/api/super/realms/{realm}/audit/{id}/revert` | Revert entity to before state |
| `GET` | `/api/super/my-actions` | Current user's actions across realms |

**Audit query parameters:**

| Parameter | Type | Description |
|-----------|------|-------------|
| `entityType` | String | Filter by CLIENT, ROLE, GROUP, IDP, USER |
| `actionType` | String | Filter by CREATE, UPDATE, DELETE |
| `actorKeycloakId` | String | Filter by actor |
| `from` | ISO DateTime | Start date |
| `to` | ISO DateTime | End date |
| `page` | Integer | Page number (0-indexed) |
| `size` | Integer | Page size |

## Client-Scoped Endpoints

These endpoints are accessed by service applications using client credentials tokens.

### User Onboarding

| Method | Path | Description |
|--------|------|-------------|
| `POST` | `/api/clients/onboard` | Onboard users for the calling client |

The calling client is identified from the JWT's `azp` claim. If the caller is a backend client with a paired frontend, users are authorized for the frontend client.

**Request:**

```json
{
  "users": [
    {
      "email": "alice@example.com",
      "displayName": "Alice Smith",
      "firstName": "Alice",
      "lastName": "Smith",
      "roles": ["viewer"]
    }
  ]
}
```

### Client Roles

| Method | Path | Description |
|--------|------|-------------|
| `GET` | `/api/clients/{clientId}/roles` | List custom roles for client |
| `POST` | `/api/clients/{clientId}/roles` | Create custom role |
| `PUT` | `/api/clients/{clientId}/roles/{roleId}` | Update custom role |
| `DELETE` | `/api/clients/{clientId}/roles/{roleId}` | Delete custom role |

### Client Users

| Method | Path | Description |
|--------|------|-------------|
| `GET` | `/api/clients/{clientId}/users` | List authorized users |
| `POST` | `/api/clients/{clientId}/users` | Add users to client |
| `DELETE` | `/api/clients/{clientId}/users` | Remove users from client |
| `GET` | `/api/clients/{clientId}/users/{email}/authorized` | Check user authorization |

## Account Admin Endpoints

Require `ROLE_ACCOUNT_ADMIN` or `ROLE_INSTITUTE_ADMIN`.

| Method | Path | Description |
|--------|------|-------------|
| `GET` | `/api/account/users/{email}/roles` | Get user roles and approval scopes |
| `PUT` | `/api/account/users/{email}/roles` | Update user roles and approval scopes |

## Public Endpoints

No authentication required.

| Method | Path | Description |
|--------|------|-------------|
| `GET` | `/api/public/users/{email}` | Check if user exists (used by Keycloak SPI) |

## SCIM 2.0 Endpoints

See [SCIM 2.0 documentation](api/scim.md) for the full SCIM API reference. All SCIM endpoints require the `API-Version: 1.0` header.

| Method | Path | Description |
|--------|------|-------------|
| `GET/POST/PUT/PATCH/DELETE` | `/api/scim/v2/realms/{realm}/Users[/{id}]` | User CRUD |
| `POST` | `/api/scim/v2/realms/{realm}/Bulk` | Bulk operations |
| `GET` | `/api/scim/v2/realms/{realm}/Users/checksum` | User checksum |
| `GET` | `/api/scim/v2/ServiceProviderConfig` | Provider config |
| `GET` | `/api/scim/v2/Schemas` | SCIM schemas |
| `GET` | `/api/scim/v2/ResourceTypes` | Resource types |

## API Documentation (Runtime)

When the application is running:

- **OpenAPI spec**: `GET /v3/api-docs`
- **Swagger UI**: `GET /swagger-ui.html`
