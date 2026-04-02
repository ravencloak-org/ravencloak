# ADR-002: Mirror Keycloak Entities in Shadow Tables

## Status

Accepted

## Context

The admin portal needs to display and manage Keycloak entities (realms, clients, roles, groups, IDPs). Querying Keycloak's Admin REST API directly for every UI request is slow, doesn't support SQL joins, and creates tight coupling between the frontend and Keycloak.

## Decision

Maintain **shadow tables** in PostgreSQL that mirror Keycloak entities. All mutations go through the Auth Backend API, which writes to both the local database and Keycloak.

Tables: `kc_realms`, `kc_clients`, `kc_client_scopes`, `kc_groups`, `kc_roles`, `kc_identity_providers`, plus association tables.

The `KeycloakSyncService` performs a full sync from Keycloak to the database on startup to handle any out-of-band changes made directly in Keycloak.

## Consequences

- **Positive**: Fast queries, SQL joins, consistent API for the admin portal
- **Positive**: Enables extended metadata not supported by Keycloak (e.g., `paired_client_id`, audit trail)
- **Positive**: Admin portal is decoupled from Keycloak's API format
- **Negative**: Data can drift if Keycloak is modified directly — mitigated by startup sync
- **Negative**: Dual-write complexity — must keep both databases consistent
- **Accepted risk**: Shadow tables are eventually consistent; real-time Keycloak state is authoritative for authentication
