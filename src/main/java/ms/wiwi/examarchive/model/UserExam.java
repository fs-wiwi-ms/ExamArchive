package ms.wiwi.examarchive.model;

import java.sql.Timestamp;

public record UserExam(String id, Timestamp creationDate, String userID, String fileID) {
}
