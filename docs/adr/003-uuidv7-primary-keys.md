# ADR-003: Use UUIDv7 for Primary Keys

## Status

Accepted

## Context

The system needs globally unique, non-sequential identifiers for multi-tenant entities. Standard UUIDv4 is random, leading to B-tree index fragmentation and poor insert performance at scale.

## Decision

Use **UUIDv7** (RFC draft) for all primary keys via a custom PostgreSQL function `uuidv7()`. UUIDv7 encodes a Unix timestamp in the first 48 bits, providing:

- Chronological ordering (new records sort after old ones)
- Better B-tree index locality (sequential inserts, less page splitting)
- Global uniqueness without coordination

## Consequences

- **Positive**: Natural time ordering without a separate `created_at` index for ordering
- **Positive**: Better index performance than UUIDv4 for insert-heavy workloads
- **Positive**: Compatible with standard UUID columns and tooling
- **Negative**: Requires a custom PostgreSQL function (defined in V0 migration)
- **Negative**: Timestamp is extractable from the UUID (not a security concern for this use case)
