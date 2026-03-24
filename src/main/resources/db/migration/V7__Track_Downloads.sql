CREATE TABLE downloads (
  examid VARCHAR(36) REFERENCES exams (examid) ON DELETE CASCADE NOT NULL,
  downloaded_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_exam_downloads_file
    ON downloads (examid);

CREATE INDEX idx_exam_downloads_time
    ON downloads (downloaded_at);