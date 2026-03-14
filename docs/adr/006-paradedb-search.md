# ADR-006: Use ParadeDB BM25 for Full-Text Search

## Status

Accepted

## Context

The admin portal needs user search across email, name, title, department, and bio fields. PostgreSQL's built-in `tsvector`/`tsquery` works but lacks relevance scoring and modern tokenization. Elasticsearch or Typesense would add operational complexity for a relatively small dataset.

## Decision

Use **ParadeDB pg_search** extension for BM25 full-text search directly in PostgreSQL. Also install **pgvector** for future hybrid search (semantic + keyword).

BM25 index on users table covers: `email` (raw tokenizer), `display_name`, `first_name`, `last_name`, `bio`, `job_title`, `department` (ICU tokenizer).

Search operators: `|||` (any term), `&&&` (all terms). Relevance scoring via `pdb.score(id)`.

## Consequences

- **Positive**: No external search service — all search runs inside PostgreSQL
- **Positive**: BM25 relevance scoring is industry-standard (same algorithm as Elasticsearch)
- **Positive**: ICU tokenization handles international names correctly
- **Positive**: pgvector is ready for future semantic search
- **Negative**: Requires ParadeDB binaries installed on the PostgreSQL server
- **Negative**: Postgres 16 and earlier need `shared_preload_libraries` config change
- **Mitigated**: Docker Compose uses `paradedb/paradedb` image with everything pre-installed
