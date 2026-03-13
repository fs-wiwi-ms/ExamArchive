package ms.wiwi.examarchive;

import ms.wiwi.examarchive.admin.AdminExamList;
import ms.wiwi.examarchive.model.*;
import ms.wiwi.examarchive.model.Module;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public class Repository {

    private final DBManager dbManager;
    Logger logger = LoggerFactory.getLogger(Repository.class);

    public Repository(DBManager dbManager){
        this.dbManager = dbManager;
    }

    public @Nullable Exam getExam(String id){
        try(Connection connection = dbManager.getConnection();
            PreparedStatement statement = connection.prepareStatement("SELECT examid, name, moduleid, year, semester, uploaddate, fileid, uploaderid, status, professorid FROM exams WHERE exams.examid = ?")) {
            statement.setString(1, id);
            ResultSet resultSet = statement.executeQuery();
            if(!resultSet.next()){
                return null;
            }
            String examId = resultSet.getString("examid");
            String name = resultSet.getString("name");
            String moduleId = resultSet.getString("moduleid");
            int year = resultSet.getInt("year");
            Semester semester = Semester.valueOf(resultSet.getString("semester"));
            String fileId = resultSet.getString("fileid");
            String uploaderId = resultSet.getString("uploaderid");
            ExamStatus status = ExamStatus.valueOf(resultSet.getString("status"));
            String professorId = resultSet.getString("professorid");
            Instant uploaddate = resultSet.getTimestamp("uploaddate").toInstant();
            return new Exam(name, examId, moduleId, year, semester, uploaddate, fileId, uploaderId, status, professorId);
        } catch (SQLException e) {
            logger.error("Could not get exam from database", e);
            return null;
        }
    }

    /**
     * Deletes an exam from the database.
     *
     * @param moduleId Module ID of the exam to delete
     * @return True if the exam was deleted, false otherwise
     */
    public boolean deleteModule(String moduleId) {
        try (Connection connection = dbManager.getConnection();
             PreparedStatement statement = connection.prepareStatement("DELETE FROM modules WHERE moduleid = ?")) {
            statement.setString(1, moduleId);
            statement.executeUpdate();
            refreshModuleSearchView();
            return true;
        } catch (SQLException e) {
            logger.error("Could not delete module from database", e);
            return false;
        }
    }

    /**
     * Adds an exam to the database. This action requires a that the professor as well as the module exist
     * @param exam Exam to add
     */
    public void addExam(Exam exam){
        try (Connection connection = dbManager.getConnection();
             PreparedStatement statement = connection.prepareStatement("INSERT INTO exams (examid, name, moduleid, semester, year, uploaddate, fileid, uploaderid, status, professorid) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)")){
            statement.setString(1, exam.examID());
            statement.setString(2, exam.name());
            statement.setString(3, exam.moduleID());
            statement.setInt(4, exam.year());
            statement.setString(5, exam.semester().name());
            statement.setTimestamp(6, Timestamp.from(exam.uploadDate()));
            statement.setString(7, exam.fileID());
            statement.setString(8, exam.uploaderID());
            statement.setString(9, exam.status().name());
            statement.setString(10, exam.professorID());
            statement.executeUpdate();
            refreshModuleSearchView();
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

    /**
     * Adds a new module to the database.
     *
     * @param name Name of the module
     * @param id   Module ID
     */
    public void addModule(String name, String id) {
        try (Connection connection = dbManager.getConnection();
             PreparedStatement statement = connection.prepareStatement("INSERT INTO modules (moduleid, name) VALUES (?, ?)")) {
            statement.setString(1, id);
            statement.setString(2, name);
            statement.executeUpdate();
        } catch (Exception e) {
            logger.error("Could not add module to database", e);
        }
        refreshModuleSearchView();
    }

    /**
     * Updates an exam in the database. The exam must already exist in the database.
     *
     * @param newExam Exam to update with modified fields
     */
    public void updateExam(Exam newExam) {
        try (Connection connection = dbManager.getConnection();
             PreparedStatement statement = connection.prepareStatement("""
                     UPDATE exams
                     SET name = ?, moduleid = ?, year = ?, semester = ?, uploaddate = ?, fileid = ?, uploaderid = ?, status = ?, professorid = ?
                     WHERE examid = ?
                     """)) {
            statement.setString(1, newExam.name());
            statement.setString(2, newExam.moduleID());
            statement.setInt(3, newExam.year());
            statement.setString(4, newExam.semester().name());
            statement.setTimestamp(5, Timestamp.from(newExam.uploadDate()));
            statement.setString(6, newExam.fileID());
            statement.setString(7, newExam.uploaderID());
            statement.setString(8, newExam.status().name());
            statement.setString(9, newExam.professorID());
            statement.setString(10, newExam.examID());
            statement.executeUpdate();
            refreshModuleSearchView();
        } catch (Exception e) {
            logger.error("Could not update exam in database", e);
        }
    }

    /**
     * Deletes an exam from the database.
     *
     * @param id Exam ID to delete
     */
    public void deleteExam(String id) {
        try (Connection connection = dbManager.getConnection();
             PreparedStatement statement = connection.prepareStatement("DELETE FROM exams WHERE examid = ?")) {
            statement.setString(1, id);
            statement.executeUpdate();
            refreshModuleSearchView();
        } catch (Exception e) {
            logger.error("Could not delete exam from database", e);
        }
    }

    public List<AdminExamList> getAllExams() {
        List<AdminExamList> allExams = new ArrayList<>();

        String query = """
            SELECT
                e.examID, e.name AS exam_name, e.semester, e.year, e.uploadDate, e.fileID, e.status,
                m.moduleID, m.name AS module_name,
                p.professorID, p.firstname AS prof_firstname, p.lastname AS prof_lastname,
                u.userID, u.firstname AS user_firstname, u.lastname AS user_lastname, u.lastLogin AS user_lastLogin, u.createdAt AS user_createdAt, u.email AS user_email, u.role AS user_role
            FROM exams e
            LEFT JOIN modules m ON e.moduleID = m.moduleID
            LEFT JOIN professors p ON e.professorID = p.professorID
            LEFT JOIN users u ON e.uploaderID = u.userID
        """;

        try (Connection connection = dbManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(query);
             ResultSet resultSet = statement.executeQuery()) {
            while (resultSet.next()) {
                String examId = resultSet.getString("examID");
                String name = resultSet.getString("exam_name");
                String status = resultSet.getString("status");
                int year = resultSet.getInt("year");
                Semester semester = Semester.valueOf(resultSet.getString("semester"));
                Instant uploadDate = resultSet.getTimestamp("uploadDate").toInstant();
                String fileId = resultSet.getString("fileID");
                String moduleId = resultSet.getString("moduleID");
                String moduleName = resultSet.getString("module_name");
                String professorId = resultSet.getString("professorID");
                String professorFirstName = resultSet.getString("prof_firstname");
                String professorLastName = resultSet.getString("prof_lastname");
                String uploaderId = resultSet.getString("userID");
                User uploader = null;
                if (uploaderId != null) {
                    String uploaderFirstName = resultSet.getString("user_firstname");
                    String uploaderLastName = resultSet.getString("user_lastname");
                    String uploaderEmail = resultSet.getString("user_email");
                    Instant uploaderLastLogin = resultSet.getTimestamp("user_lastLogin").toInstant();
                    Instant uploaderCreatedAt = resultSet.getTimestamp("user_createdAt").toInstant();
                    Role uploaderRole = Role.valueOf(resultSet.getString("user_role"));
                    uploader = new User(uploaderId, uploaderFirstName, uploaderLastName, uploaderLastLogin, uploaderCreatedAt, uploaderEmail, uploaderRole);
                }
                Module module = new Module(moduleName, moduleId);
                Professor professor = new Professor(professorId, professorFirstName, professorLastName);
                Exam exam = new Exam(name, examId, moduleId, year, semester, uploadDate, fileId, uploaderId, ExamStatus.valueOf(status), professorId);
                allExams.add(new AdminExamList(module, exam, professor, uploader));
            }
            return allExams;
        } catch (SQLException e) {
            logger.error("Could not get all exams from database", e);
            return List.of();
        }
   }

   public List<Module> getAllModules() {
        List<Module> allModules = new ArrayList<>();
        try (Connection connection = dbManager.getConnection();
             PreparedStatement preparedStatement = connection.prepareStatement("SELECT * FROM modules ORDER BY name ASC");
             ResultSet resultSet = preparedStatement.executeQuery()){
            while (resultSet.next()){
                String moduleId = resultSet.getString("moduleid");
                String moduleName = resultSet.getString("name");
                allModules.add(new Module(moduleName, moduleId));
            }
            return allModules;
        } catch (SQLException e){
            logger.error("Could not get all modules from database", e);
            return List.of();
        }
   }

    /**
     * Counts the number of users with a certain role.
     * @param role Role to count
     * @return Number of users with the role
     */
   public int countUsersByRole(Role role){
       try(Connection connection = dbManager.getConnection();
           PreparedStatement statement = connection.prepareStatement("SELECT COUNT(*) FROM users WHERE role = ?")) {
           statement.setString(1, role.name());
           ResultSet resultSet = statement.executeQuery();
           resultSet.next();
           return resultSet.getInt("count");
       } catch (SQLException e) {
           logger.error("Could not count all " + role.name() + " accounts", e);
           return 0;
       }
   }

    /**
     * Searches for the top 10 users matching the given search query.
     * Matches against a concatenation of firstname, lastname, and email using pg_trgm similarity.
     *
     * @param searchQuery The string to search for
     * @return List of up to 10 matching users
     */
    public List<User> searchUsers(String searchQuery) {
        List<User> matchingUsers = new ArrayList<>();
        try (Connection connection = dbManager.getConnection();
             PreparedStatement statement = connection.prepareStatement("""
            SELECT userid, firstname, lastname, lastlogin, createdat, email, role
                FROM users
                WHERE ? <% (firstname || ' ' || lastname || ' ' || email)
                ORDER BY word_similarity(?, firstname || ' ' || lastname || ' ' || email) DESC
                LIMIT 10
            """)) {
            statement.setString(1, searchQuery);
            statement.setString(2, searchQuery);
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    matchingUsers.add(new User(
                            rs.getString("userid"),
                            rs.getString("firstname"),
                            rs.getString("lastname"),
                            rs.getTimestamp("lastlogin").toInstant(),
                            rs.getTimestamp("createdat").toInstant(),
                            rs.getString("email"),
                            Role.valueOf(rs.getString("role"))
                    ));
                }
            }
        } catch (SQLException e) {
            logger.error("Could not search for users using query: {}", searchQuery, e);
        }
        return matchingUsers;
    }

    /**
     * Gets a user by their ID.
     * @param id User ID
     * @return the user with the given ID, or null if the user does not exist
     */
    public @Nullable User getUser(String id) {
        try(Connection connection = dbManager.getConnection();
            PreparedStatement statement = connection.prepareStatement("SELECT userid, firstname, lastname, lastlogin, createdat, email, role FROM users WHERE userid = ?")){
            statement.setString(1, id);
            ResultSet resultSet = statement.executeQuery();
            if(!resultSet.next()){
                return null;
            }
            String userId = resultSet.getString("userid");
            String firstName = resultSet.getString("firstname");
            String lastName = resultSet.getString("lastname");
            Instant lastLogin = resultSet.getTimestamp("lastlogin").toInstant();
            Instant createdAt = resultSet.getTimestamp("createdat").toInstant();
            String email = resultSet.getString("email");
            Role role = Role.valueOf(resultSet.getString("role"));
            return new User(userId, firstName, lastName, lastLogin, createdAt, email, role);
        } catch (SQLException e) {
            logger.error("Could not get user from database", e);
            return null;
        }
    }

    public void updateUserRole(String id, Role role){
        try(Connection connection = dbManager.getConnection();
            PreparedStatement statement = connection.prepareStatement("UPDATE users SET role = ? WHERE userid = ?")){
            statement.setString(1, role.name());
            statement.setString(2, id);
            statement.executeUpdate();
        } catch (SQLException e) {
            logger.error("Could not update user role in database", e);
        }
    }

    /**
     * Searches for the top 10 modules matching the given search query.
     * Matches against a concatenation of module name, keywords, and professor names.
     *
     * @param searchQuery The string to search for
     * @return List of up to 10 matching modules
     */
    public List<ModuleSearchResult> searchModules(String searchQuery) {
        List<ModuleSearchResult> matchingModules = new ArrayList<>();
        try (Connection connection = dbManager.getConnection();
             PreparedStatement statement = connection.prepareStatement("""
                         SELECT module_name, moduleid, professors_array, exam_count
                         FROM module_search_view
                         WHERE ? <% search_text
                         ORDER BY word_similarity(?, search_text) DESC
                         LIMIT 10
                     """)) {
            statement.setString(1, searchQuery);
            statement.setString(2, searchQuery);
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    Array profArray = rs.getArray("professors_array");
                    String[] professors = profArray != null ? (String[]) profArray.getArray() : new String[0];

                    matchingModules.add(new ModuleSearchResult(
                            rs.getString("module_name"),
                            rs.getString("moduleid"),
                            professors,
                            rs.getInt("exam_count")
                    ));
                }
            }
        } catch (SQLException e) {
            logger.error("Could not search for modules using query: {}", searchQuery, e);
        }
        return matchingModules;
    }

    private void refreshModuleSearchView() {
        CompletableFuture.runAsync(() -> {
            try (Connection connection = dbManager.getConnection();
                 Statement statement = connection.createStatement()) {
                statement.executeUpdate("REFRESH MATERIALIZED VIEW CONCURRENTLY module_search_view");
            } catch (SQLException e) {
                logger.error("Could not refresh module search view", e);
            }
        });
    }
}
