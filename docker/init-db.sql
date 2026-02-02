-- Create databases for auth app and keycloak
CREATE DATABASE "kos-auth";
CREATE DATABASE keycloak;

-- Connect to auth database and enable ParadeDB extensions
\c "kos-auth"
CREATE EXTENSION IF NOT EXISTS pg_search;
CREATE EXTENSION IF NOT EXISTS vector;

-- Keycloak doesn't need ParadeDB extensions
