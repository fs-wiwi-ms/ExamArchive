CREATE EXTENSION IF NOT EXISTS pg_trgm;

CREATE INDEX idx_users_search_trgm ON users
USING gin ((firstname || ' ' || lastname || ' ' || email) gin_trgm_ops);