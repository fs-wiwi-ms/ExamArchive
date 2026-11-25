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
     * The LLM creates a task plan from the reference exams
     */
    PLANNING,
    /**
     * The LLM generates the planned tasks
     */
    GENERATING_TASKS,
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
