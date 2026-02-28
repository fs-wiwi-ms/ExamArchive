package ms.wiwi.examarchive.model;

import java.time.Instant;

/**
 * An exam represents a single exam as well as its metadata and pdf file.
 * @param name name of the exam
 * @param examID random id of the exam
 * @param moduleID module id
 * @param semester semester (Winter or Summer)
 * @param uploadDate date of upload
 * @param fileID id of the pdf file in s3
 * @param uploaderID id of the uploader. The upload might not exist anymore
 * @param status status of the exam
 * @param professorID id of the professor
 */
public record Exam(String name,
                   String examID,
                   String moduleID,
                   Semester semester,
                   Instant uploadDate,
                   Instant fileID,
                   String uploaderID,
                   ExamStatus status,
                   String professorID) {}