ALTER TABLE exams ADD COLUMN scan TEXT;

CREATE TABLE user_exams(
    id VARCHAR(36) PRIMARY KEY,
    user_id VARCHAR(36) NOT NULL REFERENCES users.userID,
    creation_date TIMESTAMP NOT NULL,
    file_id VARCHAR(36) NOT NULL,
    input_tokens INTEGER,
    output_tokens INTEGER
);