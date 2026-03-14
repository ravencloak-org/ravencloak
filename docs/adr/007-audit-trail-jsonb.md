# ADR-007: JSONB-Based Audit Trail with Revert

## Status

Accepted

## Context

The admin portal manages critical identity infrastructure (clients, roles, groups, IDPs). Changes must be auditable — who changed what, when, and what the before/after state looked like. Additionally, administrators should be able to revert accidental changes.

## Decision

Implement an **entity action log** (`entity_action_log` table) that captures:

- **Actor**: Keycloak user ID, email, display name, and issuer from the JWT
- **Action**: CREATE, UPDATE, DELETE
- **Entity**: Type, ID, name, realm
- **State**: Before/after snapshots as JSONB, plus an array of changed field names
- **Revert**: Ability to restore the `before_state` to both the database and Keycloak

The `AuditService` is called after every entity mutation. The `RevertService` can restore an entity to its previous state by applying the `before_state` JSONB snapshot.

## Consequences

- **Positive**: Complete audit trail with before/after diffs
- **Positive**: Revert capability for accidental changes
- **Positive**: JSONB snapshots are schema-flexible — work for any entity type
- **Positive**: Actor info from JWT means no additional user lookup needed
- **Negative**: JSONB snapshots grow the database over time — may need archival policy
- **Negative**: Revert only goes back one step (to the specific action's before state)
- **Negative**: If Keycloak state has diverged, revert may conflict — handled by error responses
