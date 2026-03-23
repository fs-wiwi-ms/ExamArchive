CREATE TABLE degrees
(
    id   VARCHAR(36) PRIMARY KEY NOT NULL,
    name VARCHAR(255)            NOT NULL
);

CREATE TABLE module_degrees(
    module_id VARCHAR(36) REFERENCES modules (moduleid) ON DELETE CASCADE,
    degree_id VARCHAR(36) REFERENCES degrees (id) ON DELETE CASCADE,
    PRIMARY KEY (module_id, degree_id)
);