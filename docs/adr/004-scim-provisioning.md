# ADR-004: Implement SCIM 2.0 for User Provisioning

## Status

Accepted

## Context

External services in the KOS ecosystem need to manage users (CRUD + bulk operations). A proprietary API would require custom client libraries for each service. A standard protocol enables interoperability and reusable SDKs.

## Decision

Implement **SCIM 2.0** (RFC 7644/7643) as the user provisioning API. Build a companion **Forge SDK** (Spring Boot Starter) that wraps the SCIM API with type-safe Kotlin classes and automatic startup sync.

Key features:
- Standard SCIM endpoints (Users CRUD, Bulk, ServiceProviderConfig, Schemas, ResourceTypes)
- Header-based API versioning (`API-Version: 1.0`)
- SCIM filter → R2DBC SQL translation
- Deterministic user checksum for drift detection
- Startup sync: compare checksums, bulk-reconcile differences

## Consequences

- **Positive**: Standards-compliant API — works with any SCIM 2.0 client
- **Positive**: Forge SDK makes integration trivial for Spring Boot services
- **Positive**: Checksum-based sync detects drift without full data comparison
- **Negative**: SCIM filter parsing adds complexity (`ScimFilterTranslator`)
- **Negative**: SCIM schema is more verbose than a custom API would be
