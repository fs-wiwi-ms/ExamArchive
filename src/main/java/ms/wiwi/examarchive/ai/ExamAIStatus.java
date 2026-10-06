package ms.wiwi.examarchive.ai;

public enum ExamAIStatus {
    /**
     * Fetch exams from DB
     */
    FETCH_EXAMS,
    /**
     * If not chached: OCR Scans exams
     */
    SCAN,
    /**
     * Request is sent to the LLM and is processed
     */
    GENERATING,
    /**
     * Compiles the LaTeX to PDF
     */
    COMPILING,
    /**
     * Uploads the generated exam to S3
     */
    UPLOADING,
    /**
     * Job succeeded
     */
    DONE,
    /**
     * Job failed
     */
    FAILED
}

