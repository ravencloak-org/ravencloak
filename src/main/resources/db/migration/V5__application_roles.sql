-- =========================================================================
-- APPLICATION-LEVEL ROLES AND CUSTOM CLIENT ROLES
-- =========================================================================

-- ========================= APPLICATION ROLES =========================

-- System-wide application roles (not Keycloak-specific)
CREATE TABLE app_roles (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name TEXT NOT NULL UNIQUE,
    display_name TEXT NOT NULL,
    description TEXT,
    scope TEXT NOT NULL CHECK (scope IN ('global', 'realm', 'client')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ
);

-- Seed default application roles
INSERT INTO app_roles (name, display_name, description, scope) VALUES
    ('administrator', 'Administrator', 'Full access across all realms', 'global'),
    ('realm_admin', 'Realm Admin', 'Admin access within a specific realm', 'realm'),
    ('client_admin', 'Client Admin', 'Admin access for clients within a realm', 'client');

-- ========================= CUSTOM CLIENT ROLES =========================

-- Dynamic roles created by client admins for specific clients
CREATE TABLE client_custom_roles (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    client_id UUID NOT NULL REFERENCES kc_clients(id) ON DELETE CASCADE,
    name TEXT NOT NULL,
    display_name TEXT,
    description TEXT,
    created_by UUID REFERENCES users(id) ON DELETE SET NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ,
    UNIQUE (client_id, name)
);

CREATE INDEX idx_client_custom_roles_client_id ON client_custom_roles(client_id);

-- ========================= USER APPLICATION ROLE ASSIGNMENTS =========================

-- Assign application roles to users with optional realm/client scope
CREATE TABLE user_app_roles (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    app_role_id UUID NOT NULL REFERENCES app_roles(id) ON DELETE CASCADE,
    realm_id UUID REFERENCES kc_realms(id) ON DELETE CASCADE,  -- NULL for global roles
    client_id UUID REFERENCES kc_clients(id) ON DELETE CASCADE, -- NULL for realm/global roles
    assigned_by UUID REFERENCES users(id) ON DELETE SET NULL,
    assigned_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    -- Ensure unique assignment per scope combination
    UNIQUE NULLS NOT DISTINCT (user_id, app_role_id, realm_id, client_id)
);

CREATE INDEX idx_user_app_roles_user_id ON user_app_roles(user_id);
CREATE INDEX idx_user_app_roles_app_role_id ON user_app_roles(app_role_id);
CREATE INDEX idx_user_app_roles_realm_id ON user_app_roles(realm_id);
CREATE INDEX idx_user_app_roles_client_id ON user_app_roles(client_id);

-- ========================= USER CUSTOM CLIENT ROLE ASSIGNMENTS =========================

-- Assign custom client roles to users
CREATE TABLE user_client_custom_roles (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    custom_role_id UUID NOT NULL REFERENCES client_custom_roles(id) ON DELETE CASCADE,
    assigned_by UUID REFERENCES users(id) ON DELETE SET NULL,
    assigned_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (user_id, custom_role_id)
);

CREATE INDEX idx_user_client_custom_roles_user_id ON user_client_custom_roles(user_id);
CREATE INDEX idx_user_client_custom_roles_custom_role_id ON user_client_custom_roles(custom_role_id);

-- ========================= ADD FIELDS TO KC_CLIENTS =========================

-- Add flow configuration fields to clients
ALTER TABLE kc_clients ADD COLUMN IF NOT EXISTS standard_flow_enabled BOOLEAN NOT NULL DEFAULT true;
ALTER TABLE kc_clients ADD COLUMN IF NOT EXISTS direct_access_grants_enabled BOOLEAN NOT NULL DEFAULT false;
ALTER TABLE kc_clients ADD COLUMN IF NOT EXISTS service_accounts_enabled BOOLEAN NOT NULL DEFAULT false;
ALTER TABLE kc_clients ADD COLUMN IF NOT EXISTS authorization_services_enabled BOOLEAN NOT NULL DEFAULT false;

COMMIT;
