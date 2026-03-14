# Architecture Decision Records

This section documents key architectural decisions made during the development of KOS Auth Backend.

| ADR | Title | Status |
|-----|-------|--------|
| [ADR-001](001-reactive-stack.md) | Use Spring WebFlux with R2DBC for Reactive Stack | Accepted |
| [ADR-002](002-keycloak-shadow-tables.md) | Mirror Keycloak Entities in Shadow Tables | Accepted |
| [ADR-003](003-uuidv7-primary-keys.md) | Use UUIDv7 for Primary Keys | Accepted |
| [ADR-004](004-scim-provisioning.md) | Implement SCIM 2.0 for User Provisioning | Accepted |
| [ADR-005](005-paired-clients.md) | Paired Frontend/Backend OAuth2 Clients | Accepted |
| [ADR-006](006-paradedb-search.md) | Use ParadeDB BM25 for Full-Text Search | Accepted |
| [ADR-007](007-audit-trail-jsonb.md) | JSONB-Based Audit Trail with Revert | Accepted |
