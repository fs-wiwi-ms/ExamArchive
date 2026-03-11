package ms.wiwi.examarchive;

import ms.wiwi.examarchive.model.*;
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
            statement.setTimestamp(5, Timestamp.from(exam.uploadDate()));
            statement.setString(6, exam.fileID());
            statement.setString(7, exam.uploaderID());
            statement.setString(8, exam.status().name());
            statement.setString(9, exam.professorID());
            statement.executeUpdate();
        } catch (SQLException e) {
            logger.error("Could not add exam to database", e);
        }
    }

    /**
     * Inserts a new user into the database or updates the existing users last login time. Returns the inserted user
     * with up-to-date information.
     * @param user User to insert
     */
    public User addOrUpdateUser(User user) {
        String sql = """
        INSERT INTO users (userid, firstname, lastname, lastlogin, createdat, email, role)
        VALUES (?, ?, ?, ?, ?, ?, ?)
        ON CONFLICT (userid) DO UPDATE SET lastlogin = ?
        RETURNING userid, firstname, lastname, lastlogin, createdat, email, role
        """;

        try (Connection connection = dbManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {

            statement.setString(1, user.id());
            statement.setString(2, user.firstname());
            statement.setString(3, user.lastname());
            statement.setTimestamp(4, Timestamp.from(user.lastLogin()));
            statement.setTimestamp(5, Timestamp.from(user.createdAt()));
            statement.setString(6, user.email());
            statement.setString(7, user.role().name());
            statement.setTimestamp(8, Timestamp.from(Instant.now()));

            try (ResultSet rs = statement.executeQuery()) {
                if (rs.next()) {
                    return new User(
                            rs.getString("userid"),
                            rs.getString("firstname"),
                            rs.getString("lastname"),
                            rs.getTimestamp("lastlogin").toInstant(),
                            rs.getTimestamp("createdat").toInstant(),
                            rs.getString("email"),
                            Role.valueOf(rs.getString("role"))
                    );
                } else {
                    throw new RuntimeException("Upsert failed: No user data returned.");
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Database error during user upsert", e);
        }
    }
}
