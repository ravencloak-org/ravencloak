# ADR-005: Paired Frontend/Backend OAuth2 Clients

## Status

Accepted

## Context

Full-stack applications need two OAuth2 clients: a public client for the frontend (browser, PKCE flow) and a confidential client for the backend (client credentials flow, service accounts). Managing these independently is error-prone — developers forget to create one, misconfigure scopes, or lose track of which clients belong together.

## Decision

Introduce a **paired client** concept: when creating a full-stack application, the system automatically creates both clients and links them via `kc_clients.paired_client_id`.

- Frontend client: `{name}-web` (public, redirect URIs, PKCE)
- Backend client: `{name}-backend` (confidential, service accounts enabled)
- Link: frontend → backend via `paired_client_id`
- When the backend client onboards users, they're automatically authorized for the paired frontend client

The system also generates integration code snippets (Vanilla JS, React, Vue for frontend; Spring Boot YAML, Security Config, WebClient for backend).

## Consequences

- **Positive**: One-step application creation for full-stack developers
- **Positive**: Correct OAuth2 configuration by default
- **Positive**: Backend can onboard users that the frontend can authenticate
- **Positive**: Integration snippets reduce time-to-first-login
- **Negative**: Adds a `paired_client_id` FK and lookup logic to the client flow
