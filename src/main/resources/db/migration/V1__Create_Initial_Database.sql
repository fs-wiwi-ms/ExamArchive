CREATE TABLE modules
(
    moduleID VARCHAR(36) PRIMARY KEY,
    name     VARCHAR(255) NOT NULL
);

CREATE TABLE professors
(
    professorID VARCHAR(36) PRIMARY KEY,
    firstname   VARCHAR(255) NOT NULL,
    lastname    VARCHAR(255) NOT NULL
);

CREATE TABLE users
(
    userID    VARCHAR(255) PRIMARY KEY,
    firstname VARCHAR(255) NOT NULL,
    lastname  VARCHAR(255) NOT NULL,
    lastLogin TIMESTAMP    NOT NULL,
    createdAt TIMESTAMP    NOT NULL,
    email     VARCHAR(255) NOT NULL,
    role      VARCHAR(255) NOT NULL
);

CREATE TABLE exams
(
    examID      VARCHAR(36) PRIMARY KEY,
    name        VARCHAR(255) NOT NULL,
    moduleID    VARCHAR(36) REFERENCES modules (moduleID),
    semester    VARCHAR(255) NOT NULL,
    year        INTEGER      NOT NULL,
    uploadDate  TIMESTAMP    NOT NULL,
    fileID      VARCHAR(255) NOT NULL,
    uploaderID  VARCHAR(36)  REFERENCES users (userID) ON DELETE SET NULL,
    status      VARCHAR(255) NOT NULL,
    professorID VARCHAR(36) References professors (professorID)
);