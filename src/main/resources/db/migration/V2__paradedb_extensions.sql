-- =========================================================================
-- PARADEDB EXTENSIONS FOR FULL-TEXT SEARCH
-- =========================================================================
-- Prerequisites:
--   1. Install ParadeDB binaries on PostgreSQL server
--   2. For Postgres 16 and earlier ONLY: Add 'pg_search' to shared_preload_libraries
--      and restart PostgreSQL (not needed for Postgres 17+)
-- =========================================================================

-- Install ParadeDB pg_search extension for BM25 full-text search
CREATE EXTENSION IF NOT EXISTS pg_search;

-- Install pgvector for hybrid search (semantic + keyword)
CREATE EXTENSION IF NOT EXISTS vector;

COMMIT;