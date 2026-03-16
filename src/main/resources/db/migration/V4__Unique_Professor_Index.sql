CREATE UNIQUE INDEX IF NOT EXISTS prof_name_unique_idx
    ON professors (LOWER(firstname), LOWER(lastname));