CREATE TABLE motd (
    id SERIAL PRIMARY KEY,
    message TEXT NOT NULL,
    expires_at TIMESTAMP NOT NULL
);