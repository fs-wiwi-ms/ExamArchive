package ms.wiwi.examarchive;

import ms.wiwi.examarchive.model.Exam;
import ms.wiwi.examarchive.model.ExamStatus;
import ms.wiwi.examarchive.model.Semester;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;
import java.time.Instant;

public class Repository {

    private final DBManager dbManager;
    Logger logger = LoggerFactory.getLogger(Repository.class);

    public Repository(DBManager dbManager){
        this.dbManager = dbManager;
    }

    public @Nullable Exam getExam(String id){
        try(Connection connection = dbManager.getConnection();
            PreparedStatement statement = connection.prepareStatement("SELECT (examid, name, moduleid, semester, uploaddate, fileid, uploaderid, status, professorid) FROM exams WHERE exams.examid = ?")){
            statement.setString(1, id);
            ResultSet resultSet = statement.executeQuery();
            if(!resultSet.next()){
                return null;
            }
            String examId = resultSet.getString("examid");
            String name = resultSet.getString("name");
            String moduleId = resultSet.getString("moduleid");
            Semester semester = Semester.valueOf(resultSet.getString("semester"));
            String fileId = resultSet.getString("fileid");
            String uploaderId = resultSet.getString("uploaderid");
            ExamStatus status = ExamStatus.valueOf(resultSet.getString("status"));
            String professorId = resultSet.getString("professorid");
            Instant uploaddate = resultSet.getTimestamp("uploaddate").toInstant();
            return new Exam(name, examId, moduleId, semester, uploaddate, fileId, uploaderId, status, professorId);
        } catch (SQLException e) {
            logger.error("Could not get exam from database", e);
            return null;
        }
    }

    /**
     * Adds an exam to the database. This action requires a that the professor as well as the module exist
     * @param exam Exam to add
     */
    public void addExam(Exam exam){
        try (Connection connection = dbManager.getConnection();
            PreparedStatement statement = connection.prepareStatement("INSERT INTO exams (examid, name, moduleid, semester, uploaddate, fileid, uploaderid, status, professorid) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)")){
            statement.setString(1, exam.examID());
            statement.setString(2, exam.name());
            statement.setString(3, exam.moduleID());
            statement.setString(4, exam.semester().name());
            statement.setDate(5, new Date(exam.uploadDate().toEpochMilli()));
            statement.setString(6, exam.fileID());
            statement.setString(7, exam.uploaderID());
            statement.setString(8, exam.status().name());
            statement.setString(9, exam.professorID());
            statement.executeUpdate();
        } catch (SQLException e) {
            logger.error("Could not add exam to database", e);
        }
    }
}
