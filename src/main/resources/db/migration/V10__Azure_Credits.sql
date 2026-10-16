CREATE TABLE azure_credits (
    id SERIAL PRIMARY KEY,
    current_amount NUMERIC,
    current_credit NUMERIC,
    last_update TIMESTAMP DEFAULT NOW()
);

INSERT INTO azure_credits (current_amount, current_credit) VALUES (0, 0);