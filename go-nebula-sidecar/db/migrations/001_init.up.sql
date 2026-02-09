-- Nebula certificate metadata
CREATE TABLE IF NOT EXISTS nebula_certificates (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id VARCHAR(255) NOT NULL,
    node_type VARCHAR(50) NOT NULL,
    node_name VARCHAR(255) NOT NULL,
    certificate_pem TEXT NOT NULL,
    private_key_pem TEXT NOT NULL,
    ip_address VARCHAR(50) NOT NULL UNIQUE,
    environment VARCHAR(50),
    groups VARCHAR(255)[] DEFAULT ARRAY[]::VARCHAR[],
    device_info JSONB,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    expires_at TIMESTAMP NOT NULL,
    is_revoked BOOLEAN DEFAULT FALSE,
    revoked_at TIMESTAMP,
    revoke_reason VARCHAR(255),
    last_accessed TIMESTAMP,
    UNIQUE(user_id, node_type, node_name)
);

-- Token validation cache
CREATE TABLE IF NOT EXISTS token_cache (
    token_hash VARCHAR(255) PRIMARY KEY,
    user_id VARCHAR(255) NOT NULL,
    roles VARCHAR(255)[] NOT NULL,
    cached_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    expires_at TIMESTAMP NOT NULL
);

-- Certificate revocation list
CREATE TABLE IF NOT EXISTS certificate_revocation_list (
    certificate_serial VARCHAR(255) PRIMARY KEY,
    revoked_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    reason VARCHAR(255)
);

CREATE INDEX IF NOT EXISTS idx_nebula_certs_user ON nebula_certificates(user_id);
CREATE INDEX IF NOT EXISTS idx_nebula_certs_ip ON nebula_certificates(ip_address);
CREATE INDEX IF NOT EXISTS idx_nebula_certs_type ON nebula_certificates(node_type);
CREATE INDEX IF NOT EXISTS idx_token_cache_expires ON token_cache(expires_at);
