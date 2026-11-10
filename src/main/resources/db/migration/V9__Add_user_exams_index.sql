CREATE INDEX idx_user_exams_user_date
    ON user_exams (user_id, creation_date DESC);

CREATE INDEX idx_user_exams_creation_date
    ON user_exams (creation_date DESC);