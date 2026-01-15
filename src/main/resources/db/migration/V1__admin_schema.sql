-- ========================= -- ACCOUNTS -- =========================
CREATE TABLE accounts (
    id UUID PRIMARY KEY DEFAULT uuidv7(),
    name TEXT NOT NULL,
    slug TEXT NOT NULL UNIQUE,
    keycloak_realm TEXT NOT NULL UNIQUE,
    status TEXT NOT NULL CHECK (status IN ('ACTIVE', 'SUSPENDED')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by UUID NOT NULL,
    updated_at TIMESTAMPTZ
);

-- ========================= -- INSTITUTES -- =========================
CREATE TABLE institutes (
    id UUID PRIMARY KEY DEFAULT uuidv7(),
    account_id UUID NOT NULL REFERENCES accounts(id) ON DELETE CASCADE,
    name TEXT NOT NULL,
    code TEXT NOT NULL,
    status TEXT NOT NULL CHECK (status IN ('ACTIVE', 'ARCHIVED')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (account_id, code)
);

-- ========================= -- APPS -- =========================
CREATE TABLE apps (
    id UUID PRIMARY KEY DEFAULT uuidv7(),
    key TEXT NOT NULL UNIQUE,
    name TEXT NOT NULL,
    description TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- ========================= -- INSTITUTE ↔ APPS -- =========================
CREATE TABLE institute_apps (
    institute_id UUID NOT NULL REFERENCES institutes(id) ON DELETE CASCADE,
    app_id UUID NOT NULL REFERENCES apps(id) ON DELETE CASCADE,
    enabled_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (institute_id, app_id)
);

-- ========================= -- ROLES -- =========================
CREATE TABLE roles (
    id UUID PRIMARY KEY DEFAULT uuidv7(),
    app_id UUID REFERENCES apps(id), -- NULL = cross-app role
    name TEXT NOT NULL,
    description TEXT,
    scope TEXT NOT NULL CHECK (scope IN ('ACCOUNT', 'INSTITUTE', 'APP')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (app_id, name)
);

-- ========================= -- USERS (SHADOW TABLE) -- =========================
CREATE TABLE users (
    id UUID PRIMARY KEY DEFAULT uuidv7(),
    keycloak_user_id TEXT NOT NULL UNIQUE,
    email TEXT NOT NULL,
    display_name TEXT,
    account_id UUID NOT NULL REFERENCES accounts(id) ON DELETE CASCADE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (account_id, email)
);

-- ========================= -- ROLE ASSIGNMENTS -- =========================
CREATE TABLE role_assignments (
    id UUID PRIMARY KEY DEFAULT uuidv7(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    role_id UUID NOT NULL REFERENCES roles(id) ON DELETE RESTRICT,
    institute_id UUID REFERENCES institutes(id) ON DELETE CASCADE,
    app_id UUID REFERENCES apps(id) ON DELETE CASCADE,
    granted_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CHECK (
        (institute_id IS NOT NULL AND app_id IS NULL)
        OR (app_id IS NOT NULL)
    )
);

-- ========================= -- ACCOUNT ADMINS -- =========================
CREATE TABLE account_admins (
    account_id UUID NOT NULL REFERENCES accounts(id) ON DELETE CASCADE,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    PRIMARY KEY (account_id, user_id)
);

-- ========================= -- AUDIT LOG -- =========================
CREATE TABLE audit_log (
    id UUID PRIMARY KEY DEFAULT uuidv7(),
    actor_id UUID,
    action TEXT NOT NULL,
    target TEXT,
    metadata JSONB,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- ========================= -- INDEXES (IMPORTANT FOR SCALE) -- =========================
CREATE INDEX idx_institutes_account_id ON institutes(account_id);
CREATE INDEX idx_users_account_id ON users(account_id);
CREATE INDEX idx_role_assignments_user_id ON role_assignments(user_id);
CREATE INDEX idx_role_assignments_role_id ON role_assignments(role_id);
CREATE INDEX idx_audit_log_created_at ON audit_log(created_at);

COMMIT;
