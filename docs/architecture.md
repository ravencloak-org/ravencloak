# Architecture

## Multi-Tenant Authentication

The system supports multiple Keycloak realms via dynamic JWT issuer validation:

- `JwtIssuerReactiveAuthenticationManagerResolver` validates JWTs from any realm matching `KEYCLOAK_ISSUER_PREFIX`
- `JwtAuthorityConverter` extracts roles from Keycloak's `realm_access.roles` claim, prefixing with `ROLE_`
- Super admin access requires tokens from the `saas-admin` realm with `ROLE_SUPER_ADMIN` authority

## API Route Authorization

| Path Pattern | Authorization |
|--------------|---------------|
| `/api/public/**` | Public |
| `/auth/super/login`, `/oauth2/**` | Public (OAuth2 flow) |
| `/api/super/**` | Super admin only (`SuperAdminAuthorizationManager`) |
| `/api/account/**` | `ACCOUNT_ADMIN` or `INSTITUTE_ADMIN` role |
| `/api/scim/v2/**` | Authenticated (OAuth2 JWT) |
| All other routes | Authenticated |

## Domain Model

### Core Entities

Multi-tenant SaaS structure:

```
Account (top-level tenant, owns a Keycloak realm)
├── Institute (organizational unit)
│   └── App (feature module, enabled per institute)
├── User (shadow record linked to Keycloak user)
└── Role / RoleAssignment (RBAC with account, institute, or app scope)
```

- **Account** — top-level tenant with a dedicated Keycloak realm
- **Institute** — organizational unit within an account
- **App** — feature module that can be enabled per institute
- **User** — shadow record linked to Keycloak user, scoped to account
- **Role / RoleAssignment** — RBAC with account, institute, or app scope

### User Search (ParadeDB BM25)

The user table has a BM25 full-text search index on: `email`, `display_name`, `first_name`, `last_name`, `bio`, `job_title`, `department`.

```sql
-- Search users by name or role
SELECT id, email, display_name, pdb.score(id) as relevance
FROM users
WHERE (email, display_name, first_name, last_name) ||| 'john developer'
  AND account_id = :account_id
ORDER BY relevance DESC;
```

| Operator | Description |
|----------|-------------|
| `\|\|\|` | Match any term |
| `&&&` | Match all terms |

### Keycloak Entity Mapping

Shadow tables for Keycloak entities enable a custom admin frontend:

| Table | Description |
|-------|-------------|
| `kc_realms` | Keycloak realms linked to accounts |
| `kc_clients` | OAuth2 clients per realm |
| `kc_client_scopes` | OAuth2 scopes |
| `kc_client_scope_mappings` | Client-to-scope assignments (DEFAULT/OPTIONAL) |
| `kc_groups` | Hierarchical groups (self-referencing via `parent_id`) |
| `kc_roles` | Realm and client roles |
| `kc_role_composites` | Composite role mappings |
| `kc_user_groups` | User-to-group assignments |
| `kc_user_roles` | Direct user-to-role assignments |
| `kc_group_roles` | Group-to-role assignments |
| `kc_identity_providers` | SSO federation providers (Google, SAML, OIDC) |
| `kc_sync_log` | Sync status tracking between DB and Keycloak |
| `entity_action_logs` | Audit trail for entity changes |

### Paired Clients

Full-stack applications can create paired frontend/backend clients:

- `kc_clients.paired_client_id` links frontend to backend client
- **Frontend client**: public, `-web` suffix, configured redirect URIs
- **Backend client**: confidential, `-backend` suffix, service accounts enabled

### Audit Trail

Entity action logging with before/after state (JSONB):

| Column | Description |
|--------|-------------|
| `entity_type` | CLIENT, ROLE, GROUP, IDP |
| `action` | CREATE, UPDATE, DELETE |
| `before_state` | JSONB snapshot before change |
| `after_state` | JSONB snapshot after change |
| `actor_id` | Keycloak user ID from JWT `sub` claim |
| `actor_email` | Email from JWT `email` claim |

**Key Services:**

- `AuditService` — logs entity actions with JWT actor info
- `AuditQueryService` — query logs by realm, entity, actor
- `RevertService` — restore entities to previous state (Keycloak + DB)

**REST Endpoints:**

| Method | Path | Description |
|--------|------|-------------|
| `GET` | `/api/super/realms/{realm}/audit` | List audit logs with filters |
| `GET` | `/api/super/realms/{realm}/audit/{id}` | Get single log entry |
| `POST` | `/api/super/realms/{realm}/audit/{id}/revert` | Revert to before state |
| `GET` | `/api/super/my-actions` | Current user's actions across realms |
