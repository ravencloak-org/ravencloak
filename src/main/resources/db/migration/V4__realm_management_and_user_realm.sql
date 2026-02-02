-- =========================================================================
-- REALM MANAGEMENT AND USER-REALM LINKING
-- =========================================================================

-- ========================= ADD REALM_ID TO USERS =========================

-- Link users to their Keycloak realm
ALTER TABLE users ADD COLUMN realm_id UUID REFERENCES kc_realms(id) ON DELETE SET NULL;
CREATE INDEX idx_users_realm_id ON users(realm_id);

-- ========================= ENHANCE KC_REALMS FOR SPI =========================

-- Add User Storage SPI configuration fields to realms
ALTER TABLE kc_realms ADD COLUMN spi_enabled BOOLEAN NOT NULL DEFAULT false;
ALTER TABLE kc_realms ADD COLUMN spi_api_url TEXT;
ALTER TABLE kc_realms ADD COLUMN attributes JSONB DEFAULT '{}';

-- ========================= USER STORAGE PROVIDER SHADOW TABLE =========================

-- Shadow table for Keycloak User Storage Provider components
CREATE TABLE kc_user_storage_providers (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    realm_id UUID NOT NULL REFERENCES kc_realms(id) ON DELETE CASCADE,
    name TEXT NOT NULL,
    provider_id TEXT NOT NULL,  -- e.g., "kos-auth-storage"
    priority INTEGER NOT NULL DEFAULT 0,
    config JSONB DEFAULT '{}',  -- Provider-specific configuration
    -- Sync metadata
    keycloak_id TEXT NOT NULL UNIQUE,
    synced_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ,
    UNIQUE (realm_id, name)
);

CREATE INDEX idx_kc_user_storage_providers_realm_id ON kc_user_storage_providers(realm_id);

COMMIT;
