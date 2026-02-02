-- =========================================================================
-- USER SEARCH ENHANCEMENTS & KEYCLOAK ENTITY MAPPING
-- =========================================================================

-- ========================= ENHANCED USER SCHEMA =========================

-- Add searchable user profile fields
ALTER TABLE users ADD COLUMN first_name TEXT;
ALTER TABLE users ADD COLUMN last_name TEXT;
ALTER TABLE users ADD COLUMN phone TEXT;
ALTER TABLE users ADD COLUMN bio TEXT;
ALTER TABLE users ADD COLUMN job_title TEXT;
ALTER TABLE users ADD COLUMN department TEXT;
ALTER TABLE users ADD COLUMN avatar_url TEXT;
ALTER TABLE users ADD COLUMN status TEXT NOT NULL DEFAULT 'ACTIVE'
    CHECK (status IN ('ACTIVE', 'INACTIVE', 'SUSPENDED'));
ALTER TABLE users ADD COLUMN last_login_at TIMESTAMPTZ;
ALTER TABLE users ADD COLUMN updated_at TIMESTAMPTZ;

-- Create BM25 index for user search
-- Indexes: email, display_name, first_name, last_name, bio, job_title, department
CREATE INDEX idx_users_bm25 ON users
USING bm25 (id, email, display_name, first_name, last_name, bio, job_title, department)
WITH (
    key_field = 'id',
    text_fields = '{
        "email": {"fast": true, "tokenizer": {"type": "raw"}},
        "display_name": {"fast": true, "tokenizer": {"type": "icu"}},
        "first_name": {"fast": true, "tokenizer": {"type": "icu"}},
        "last_name": {"fast": true, "tokenizer": {"type": "icu"}},
        "bio": {"tokenizer": {"type": "icu"}},
        "job_title": {"fast": true, "tokenizer": {"type": "icu"}},
        "department": {"fast": true, "tokenizer": {"type": "icu"}}
    }'
);

-- ========================= KEYCLOAK REALMS =========================

CREATE TABLE kc_realms (
    id UUID PRIMARY KEY DEFAULT uuidv7(),
    account_id UUID REFERENCES accounts(id) ON DELETE SET NULL,
    realm_name TEXT NOT NULL UNIQUE,
    display_name TEXT,
    enabled BOOLEAN NOT NULL DEFAULT true,
    -- Sync metadata
    keycloak_id TEXT NOT NULL UNIQUE,
    synced_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ
);

CREATE INDEX idx_kc_realms_account_id ON kc_realms(account_id);

-- ========================= KEYCLOAK CLIENTS =========================

CREATE TABLE kc_clients (
    id UUID PRIMARY KEY DEFAULT uuidv7(),
    realm_id UUID NOT NULL REFERENCES kc_realms(id) ON DELETE CASCADE,
    client_id TEXT NOT NULL,  -- Keycloak client_id (e.g., "admin-console")
    name TEXT,
    description TEXT,
    enabled BOOLEAN NOT NULL DEFAULT true,
    public_client BOOLEAN NOT NULL DEFAULT false,
    protocol TEXT NOT NULL DEFAULT 'openid-connect',
    -- OAuth2 settings (cached from Keycloak)
    root_url TEXT,
    base_url TEXT,
    redirect_uris JSONB DEFAULT '[]',
    web_origins JSONB DEFAULT '[]',
    -- Sync metadata
    keycloak_id TEXT NOT NULL,
    synced_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ,
    UNIQUE (realm_id, client_id),
    UNIQUE (keycloak_id)
);

CREATE INDEX idx_kc_clients_realm_id ON kc_clients(realm_id);

-- ========================= KEYCLOAK CLIENT SCOPES =========================

CREATE TABLE kc_client_scopes (
    id UUID PRIMARY KEY DEFAULT uuidv7(),
    realm_id UUID NOT NULL REFERENCES kc_realms(id) ON DELETE CASCADE,
    name TEXT NOT NULL,
    description TEXT,
    protocol TEXT NOT NULL DEFAULT 'openid-connect',
    -- Sync metadata
    keycloak_id TEXT NOT NULL,
    synced_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ,
    UNIQUE (realm_id, name),
    UNIQUE (keycloak_id)
);

CREATE INDEX idx_kc_client_scopes_realm_id ON kc_client_scopes(realm_id);

-- ========================= CLIENT-SCOPE MAPPINGS =========================

CREATE TABLE kc_client_scope_mappings (
    client_id UUID NOT NULL REFERENCES kc_clients(id) ON DELETE CASCADE,
    scope_id UUID NOT NULL REFERENCES kc_client_scopes(id) ON DELETE CASCADE,
    scope_type TEXT NOT NULL CHECK (scope_type IN ('DEFAULT', 'OPTIONAL')),
    PRIMARY KEY (client_id, scope_id)
);

CREATE INDEX idx_kc_client_scope_mappings_scope_id ON kc_client_scope_mappings(scope_id);

-- ========================= KEYCLOAK GROUPS (HIERARCHICAL) =========================

CREATE TABLE kc_groups (
    id UUID PRIMARY KEY DEFAULT uuidv7(),
    realm_id UUID NOT NULL REFERENCES kc_realms(id) ON DELETE CASCADE,
    parent_id UUID REFERENCES kc_groups(id) ON DELETE CASCADE,
    name TEXT NOT NULL,
    path TEXT NOT NULL,  -- Full path like "/org/department/team"
    attributes JSONB DEFAULT '{}',
    -- Sync metadata
    keycloak_id TEXT NOT NULL,
    synced_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ,
    UNIQUE (realm_id, path),
    UNIQUE (keycloak_id)
);

CREATE INDEX idx_kc_groups_realm_id ON kc_groups(realm_id);
CREATE INDEX idx_kc_groups_parent_id ON kc_groups(parent_id);
CREATE INDEX idx_kc_groups_path ON kc_groups(path);

-- ========================= KEYCLOAK ROLES (REALM AND CLIENT) =========================

CREATE TABLE kc_roles (
    id UUID PRIMARY KEY DEFAULT uuidv7(),
    realm_id UUID NOT NULL REFERENCES kc_realms(id) ON DELETE CASCADE,
    client_id UUID REFERENCES kc_clients(id) ON DELETE CASCADE,  -- NULL = realm role
    name TEXT NOT NULL,
    description TEXT,
    composite BOOLEAN NOT NULL DEFAULT false,
    -- Sync metadata
    keycloak_id TEXT NOT NULL,
    synced_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ,
    UNIQUE (keycloak_id)
);

CREATE INDEX idx_kc_roles_realm_id ON kc_roles(realm_id);
CREATE INDEX idx_kc_roles_client_id ON kc_roles(client_id);
CREATE UNIQUE INDEX idx_kc_roles_realm_name ON kc_roles(realm_id, name) WHERE client_id IS NULL;
CREATE UNIQUE INDEX idx_kc_roles_client_name ON kc_roles(client_id, name) WHERE client_id IS NOT NULL;

-- ========================= COMPOSITE ROLE MAPPINGS =========================

CREATE TABLE kc_role_composites (
    parent_role_id UUID NOT NULL REFERENCES kc_roles(id) ON DELETE CASCADE,
    child_role_id UUID NOT NULL REFERENCES kc_roles(id) ON DELETE CASCADE,
    PRIMARY KEY (parent_role_id, child_role_id)
);

-- ========================= USER-GROUP ASSIGNMENTS =========================

CREATE TABLE kc_user_groups (
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    group_id UUID NOT NULL REFERENCES kc_groups(id) ON DELETE CASCADE,
    assigned_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    assigned_by UUID REFERENCES users(id),
    PRIMARY KEY (user_id, group_id)
);

CREATE INDEX idx_kc_user_groups_group_id ON kc_user_groups(group_id);

-- ========================= USER-ROLE ASSIGNMENTS (DIRECT) =========================

CREATE TABLE kc_user_roles (
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    role_id UUID NOT NULL REFERENCES kc_roles(id) ON DELETE CASCADE,
    assigned_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    assigned_by UUID REFERENCES users(id),
    PRIMARY KEY (user_id, role_id)
);

CREATE INDEX idx_kc_user_roles_role_id ON kc_user_roles(role_id);

-- ========================= GROUP-ROLE ASSIGNMENTS =========================

CREATE TABLE kc_group_roles (
    group_id UUID NOT NULL REFERENCES kc_groups(id) ON DELETE CASCADE,
    role_id UUID NOT NULL REFERENCES kc_roles(id) ON DELETE CASCADE,
    assigned_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (group_id, role_id)
);

CREATE INDEX idx_kc_group_roles_role_id ON kc_group_roles(role_id);

-- ========================= IDENTITY PROVIDERS (SSO FEDERATION) =========================

CREATE TABLE kc_identity_providers (
    id UUID PRIMARY KEY DEFAULT uuidv7(),
    realm_id UUID NOT NULL REFERENCES kc_realms(id) ON DELETE CASCADE,
    alias TEXT NOT NULL,
    display_name TEXT,
    provider_id TEXT NOT NULL,  -- e.g., "google", "saml", "oidc"
    enabled BOOLEAN NOT NULL DEFAULT true,
    trust_email BOOLEAN NOT NULL DEFAULT false,
    config JSONB DEFAULT '{}',
    -- Sync metadata
    keycloak_internal_id TEXT NOT NULL,
    synced_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ,
    UNIQUE (realm_id, alias),
    UNIQUE (keycloak_internal_id)
);

CREATE INDEX idx_kc_idps_realm_id ON kc_identity_providers(realm_id);

-- ========================= SYNC TRACKING =========================

CREATE TABLE kc_sync_log (
    id UUID PRIMARY KEY DEFAULT uuidv7(),
    realm_id UUID REFERENCES kc_realms(id) ON DELETE CASCADE,
    entity_type TEXT NOT NULL CHECK (entity_type IN (
        'REALM', 'CLIENT', 'CLIENT_SCOPE', 'GROUP', 'ROLE', 'USER', 'IDP'
    )),
    sync_direction TEXT NOT NULL CHECK (sync_direction IN ('FROM_KC', 'TO_KC')),
    status TEXT NOT NULL CHECK (status IN ('STARTED', 'COMPLETED', 'FAILED')),
    entities_processed INTEGER DEFAULT 0,
    error_message TEXT,
    started_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    completed_at TIMESTAMPTZ
);

CREATE INDEX idx_kc_sync_log_realm_id ON kc_sync_log(realm_id);
CREATE INDEX idx_kc_sync_log_started_at ON kc_sync_log(started_at);

COMMIT;