-- =========================================================================
-- AUDIT TRAIL & USER-CLIENT ASSOCIATIONS
-- =========================================================================

-- ========================= ENTITY ACTION LOG =========================
-- Tracks all CRUD operations on entities with before/after snapshots

CREATE TABLE entity_action_log (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),

    -- Actor (from JWT)
    actor_keycloak_id TEXT NOT NULL,
    actor_email TEXT,
    actor_display_name TEXT,
    actor_issuer TEXT,

    -- Action
    action_type TEXT NOT NULL CHECK (action_type IN ('CREATE', 'UPDATE', 'DELETE')),
    entity_type TEXT NOT NULL CHECK (entity_type IN ('CLIENT', 'REALM', 'ROLE', 'GROUP', 'IDP', 'USER')),

    -- Entity
    entity_id UUID NOT NULL,
    entity_keycloak_id TEXT,
    entity_name TEXT NOT NULL,
    realm_name TEXT NOT NULL,
    realm_id UUID REFERENCES kc_realms(id) ON DELETE SET NULL,

    -- Before/After snapshots (JSONB)
    before_state JSONB,
    after_state JSONB,
    changed_fields TEXT[],

    -- Revert tracking
    reverted BOOLEAN NOT NULL DEFAULT false,
    reverted_at TIMESTAMPTZ,
    reverted_by_keycloak_id TEXT,
    revert_reason TEXT,
    revert_of_action_id UUID REFERENCES entity_action_log(id),

    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Indexes for common query patterns
CREATE INDEX idx_action_log_actor ON entity_action_log(actor_keycloak_id);
CREATE INDEX idx_action_log_entity ON entity_action_log(entity_type, entity_id);
CREATE INDEX idx_action_log_realm ON entity_action_log(realm_name);
CREATE INDEX idx_action_log_created ON entity_action_log(created_at DESC);
CREATE INDEX idx_action_log_not_reverted ON entity_action_log(created_at DESC) WHERE reverted = false;

-- ========================= USER-CLIENT ASSOCIATIONS =========================
-- Tracks which users are assigned to which clients

CREATE TABLE kc_user_clients (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    realm_id UUID NOT NULL REFERENCES kc_realms(id) ON DELETE CASCADE,
    user_keycloak_id TEXT NOT NULL,
    user_email TEXT NOT NULL,
    client_id UUID NOT NULL REFERENCES kc_clients(id) ON DELETE CASCADE,
    assigned_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    assigned_by_keycloak_id TEXT,

    UNIQUE(realm_id, user_keycloak_id, client_id)
);

CREATE INDEX idx_user_clients_realm ON kc_user_clients(realm_id);
CREATE INDEX idx_user_clients_client ON kc_user_clients(client_id);
CREATE INDEX idx_user_clients_user ON kc_user_clients(user_keycloak_id);

COMMIT;
