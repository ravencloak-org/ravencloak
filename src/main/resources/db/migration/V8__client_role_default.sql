-- V8: Add is_default column to client_custom_roles
-- This allows marking one role per client as the default for user onboarding

BEGIN;

-- Add is_default column
ALTER TABLE client_custom_roles ADD COLUMN IF NOT EXISTS is_default BOOLEAN NOT NULL DEFAULT false;

-- Create partial unique index to ensure only one default role per client
CREATE UNIQUE INDEX IF NOT EXISTS idx_client_custom_roles_default_per_client
    ON client_custom_roles(client_id)
    WHERE is_default = true;

COMMIT;
