package ms.wiwi.examarchive;

import ms.wiwi.examarchive.admin.AdminExamListDTO;
import ms.wiwi.examarchive.admin.ModuleDegreeDTO;
import ms.wiwi.examarchive.model.*;
import ms.wiwi.examarchive.model.Module;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public class Repository {

    private final DBManager dbManager;
    Logger logger = LoggerFactory.getLogger(Repository.class);
    private List<ModuleSearchResultDTO> topModulesCache = new ArrayList<>();
    private Instant lastTopModulesCacheUpdate = Instant.MIN;

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
             PreparedStatement statement = connection.prepareStatement("INSERT INTO exams (examid, name, moduleid, semester, year, uploaddate, fileid, uploaderid, status, professorid) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)")){
            statement.setString(1, exam.examID());
            statement.setString(2, exam.name());
            statement.setString(3, exam.moduleID());
            statement.setString(4, exam.semester().name());
            statement.setInt(5, exam.year());
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

    public List<AdminExamListDTO> getAllExams() {
        List<AdminExamListDTO> allExams = new ArrayList<>();

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
                allExams.add(new AdminExamListDTO(module, exam, professor, uploader));
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
             PreparedStatement preparedStatement = connection.prepareStatement("SELECT * FROM modules ORDER BY name");
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
     * @param degreeIds Optional list of degree IDs to filter modules by. If provided, only modules with a matching degree mapping will be returned.
     * @return List of up to 10 matching modules
     */
    public List<ModuleSearchResultDTO> searchModules(String searchQuery, List<String> degreeIds) {
        List<ModuleSearchResultDTO> matchingModules = new ArrayList<>();
        boolean hasDegrees = degreeIds != null && !degreeIds.isEmpty();
        String query = """
                 SELECT m.module_name, m.moduleid, m.professors_array, m.exam_count
                 FROM module_search_view m
                 WHERE ? <% m.search_text
                 """;
        if (hasDegrees) {
            query += """
                 AND EXISTS (
                     SELECT 1 FROM module_degrees md
                     WHERE md.module_id = m.moduleid
                       AND md.degree_id = ANY(?)
                 )
                 """;
        }
        query += " ORDER BY word_similarity(?, m.search_text) DESC LIMIT 10";
        try (Connection connection = dbManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(query)) {
            int paramIndex = 1;
            statement.setString(paramIndex++, searchQuery);

            if (hasDegrees) {
                Array degreeArray = connection.createArrayOf("varchar", degreeIds.toArray());
                statement.setArray(paramIndex++, degreeArray);
            }
            statement.setString(paramIndex++, searchQuery);
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    Array profArray = rs.getArray("professors_array");
                    String[] professors = profArray != null ? (String[]) profArray.getArray() : new String[0];

                    matchingModules.add(new ModuleSearchResultDTO(
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

    public @Nullable Module getModule(String id) {
        try (Connection connection = dbManager.getConnection();
             PreparedStatement statement = connection.prepareStatement("SELECT moduleid, name FROM modules WHERE moduleid = ?")) {
            statement.setString(1, id);
            ResultSet resultSet = statement.executeQuery();
            if (!resultSet.next()) {
                return null;
            }
            String moduleId = resultSet.getString("moduleid");
            String moduleName = resultSet.getString("name");
            return new Module(moduleName, moduleId);
        } catch (SQLException e) {
            logger.error("Could not get module from database", e);
            return null;
        }
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

    public List<ProfessorExamDTO> getExamsAndProfessors(String moduleID) {
        try (Connection connection = dbManager.getConnection();
             PreparedStatement statement = connection.prepareStatement("""
                     SELECT e.examid, e.name, e.uploaddate, e.uploaderid, e.year,
                            e.semester, e.fileid, e.status, e.professorid,
                            p.firstname, p.lastname
                     FROM exams e
                     LEFT JOIN professors p ON e.professorid = p.professorid
                     WHERE e.moduleid = ? AND e.status = ?
                     ORDER BY e.year DESC, e.semester DESC
                     """)
        ) {
            statement.setString(1, moduleID);
            statement.setString(2, ExamStatus.ACCEPTED.name());
            ResultSet resultSet = statement.executeQuery();
            List<ProfessorExamDTO> exams = new ArrayList<>();
            while (resultSet.next()) {
                String examid = resultSet.getString("examid");
                String name = resultSet.getString("name");
                Instant uploadDate = resultSet.getTimestamp("uploaddate").toInstant();
                String uploaderid = resultSet.getString("uploaderid");
                int year = resultSet.getInt("year");
                Semester semester = Semester.valueOf(resultSet.getString("semester"));
                String fileid = resultSet.getString("fileid");
                ExamStatus status = ExamStatus.valueOf(resultSet.getString("status"));
                String professorid = resultSet.getString("professorid");
                String firstname = resultSet.getString("firstname");
                String lastname = resultSet.getString("lastname");
                Exam exam = new Exam(name, examid, moduleID, year, semester, uploadDate, fileid, uploaderid, status, professorid);
                Professor professor = new Professor(professorid, firstname, lastname);
                exams.add(new ProfessorExamDTO(exam, professor));
            }
            return exams;
        } catch (SQLException e) {
            logger.error("Could not get exams and professors for module {}", moduleID, e);
            return List.of();
        }
    }
    /**
     * Gets a professor by first and last name (case-insensitive).
     * If they do not exist, creates a new one with a generated UUID.
     * Relies on the prof_name_unique_idx database index.
     *
     * @param firstName First name of the professor
     * @param lastName Last name of the professor
     * @return The existing or newly created Professor object, or null if an error occurs
     */
    public @Nullable Professor getOrCreateProfessor(String firstName, String lastName) {
        String sql = """
            INSERT INTO professors (professorid, firstname, lastname)
            VALUES (gen_random_uuid(), ?, ?)
            ON CONFLICT (LOWER(firstname), LOWER(lastname))
            DO UPDATE SET firstname = EXCLUDED.firstname
            RETURNING professorid, firstname, lastname
            """;

        try (Connection connection = dbManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {

            statement.setString(1, firstName);
            statement.setString(2, lastName);

            try (ResultSet rs = statement.executeQuery()) {
                if (rs.next()) {
                    return new Professor(
                            rs.getString("professorid"),
                            rs.getString("firstname"),
                            rs.getString("lastname")
                    );
                }
            }
        } catch (SQLException e) {
            logger.error("Could not get or create professor in database", e);
        }
        return null;
    }

    /**
     * Gets all distinct professors who have an exam registered for a given module, optionally filtering by name.
     *
     * @param moduleID The module ID to search for
     * @param firstName Optional first name filter
     * @param lastName Optional last name filter
     */
    public List<Professor> searchProfessorsForModule(String moduleID, String firstName, String lastName) {
        String query = """
                SELECT DISTINCT p.professorid, p.firstname, p.lastname
                FROM professors p
                JOIN exams e ON p.professorid = e.professorid
                WHERE e.moduleid = ?
                AND e.status = 'ACCEPTED'
                """;

        boolean hasFirst = firstName != null && !firstName.isBlank();
        boolean hasLast = lastName != null && !lastName.isBlank();

        if (hasFirst) query += " AND p.firstname ILIKE ?";
        if (hasLast) query += " AND p.lastname ILIKE ?";

        try (Connection connection = dbManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(query)) {

            int paramIndex = 1;
            statement.setString(paramIndex++, moduleID);
            if (hasFirst) statement.setString(paramIndex++, "%" + firstName + "%");
            if (hasLast) statement.setString(paramIndex++, "%" + lastName + "%");

            ResultSet resultSet = statement.executeQuery();
            List<Professor> professors = new ArrayList<>();
            while (resultSet.next()) {
                professors.add(new Professor(
                        resultSet.getString("professorid"),
                        resultSet.getString("firstname"),
                        resultSet.getString("lastname")
                ));
            }
            return professors;
        } catch (SQLException e) {
            logger.error("Could not search professors for module {}", moduleID, e);
            return List.of();
        }
    }

    /**
     * Returns a professor based on its id or null if not found
     *
     * @param id Professor id
     */
    public Professor getProfessor(String id) {
        try(Connection connection = dbManager.getConnection();
            PreparedStatement statement = connection.prepareStatement("""
        SELECT professorid, firstname, lastname FROM professors WHERE professorid = ?
        """)){
            statement.setString(1, id);
            ResultSet resultSet = statement.executeQuery();
            if(!resultSet.next()){
                return null;
            }
            String professorId = resultSet.getString("professorid");
            String firstName = resultSet.getString("firstname");
            String lastName = resultSet.getString("lastname");
            return new Professor(professorId, firstName, lastName);
        } catch (SQLException e) {
            logger.error("Could not get professor from database", e);
            return null;
        }
    }

    /**
     * Deletes a keyword from the database.
     * @param keyword The keyword to delete
     */
    public void deleteKeyword(KeyWord keyword) {
        try (Connection connection = dbManager.getConnection();
             PreparedStatement statement = connection.prepareStatement("DELETE FROM keywords WHERE keyword = ? AND module = ?")) {
            statement.setString(1, keyword.keyword());
            statement.setString(2, keyword.moduleID());
            statement.executeUpdate();
            refreshModuleSearchView();
        } catch (SQLException e) {
            logger.error("Could not delete keyword from database", e);
        }
    }

    /**
     * Adds a keyword to the database. If the keyword already exists, it does nothing.
     * @param keyWord The keyword to add
     */
    public void addKeyword(KeyWord keyWord) {
        try (Connection connection = dbManager.getConnection();
             PreparedStatement statement = connection.prepareStatement("INSERT INTO keywords (keyword, module) VALUES (?, ?) ON CONFLICT DO NOTHING")) {
            statement.setString(1, keyWord.keyword());
            statement.setString(2, keyWord.moduleID());
            statement.executeUpdate();
            refreshModuleSearchView();
        } catch (SQLException e){
            logger.error("Could not add keyword to database", e);
        }
    }

    /**
     * Gets all keywords from the database.
     * @return List of KeyWords
     */
    public List<KeyWord> getKeywords() {
        try(Connection connection = dbManager.getConnection();
            PreparedStatement statement = connection.prepareStatement("SELECT keyword, module FROM keywords")){
            ResultSet resultSet = statement.executeQuery();
            List<KeyWord> keywords = new ArrayList<>();
            while(resultSet.next()){
                String keyword = resultSet.getString("keyword");
                String module = resultSet.getString("module");
                keywords.add(new KeyWord(keyword, module));
            }
            refreshModuleSearchView();
            return keywords;
        } catch (SQLException e) {
            logger.error("Could not get keywords from database", e);
            return List.of();
        }
    }

    /**
     * Gets the MOTD text from the database.
     * @return motd record
     */
    public Motd getMotdText() {
        try (Connection connection = dbManager.getConnection();
            PreparedStatement statement = connection.prepareStatement("SELECT message, expires_at FROM motd LIMIT 1")){
            ResultSet resultSet = statement.executeQuery();
            if(!resultSet.next()){
                return null;
            }
            String message = resultSet.getString("message");
            Instant expiresAt = resultSet.getTimestamp("expires_at").toInstant();
            Motd motd = new Motd(message, expiresAt);
            if(motd.isExpired()){
                return null;
            }
            return motd;
        } catch (SQLException e) {
            logger.error("Could not get motd text from database", e);
            return null;
        }
    }


    /**
     * Updates the motd in the database by overwriting it
     * @param motd motd to update
     */
    public void updateMotd(Motd motd) {
        try(Connection connection = dbManager.getConnection();
            PreparedStatement statement1 = connection.prepareStatement("DELETE FROM motd");
            PreparedStatement statement2 = connection.prepareStatement("INSERT INTO motd (message, expires_at) VALUES (?, ?)")){
            statement1.executeUpdate();
            statement2.setString(1, motd.text());
            statement2.setTimestamp(2, Timestamp.from(motd.expires()));
            statement2.executeUpdate();
        } catch (SQLException e){
            logger.error("Could not update motd text in database", e);
        }
    }

    public void addDegree(Degree degree) {
        try (Connection connection = dbManager.getConnection(); PreparedStatement statement = connection.prepareStatement("INSERT INTO degrees (id, name) VALUES (?, ?)")) {
            statement.setString(1, degree.degreeID());
            statement.setString(2, degree.name());
            statement.executeUpdate();
        } catch (SQLException e) {
            logger.error("Could not add degree to database", e);
        }
    }

    public void deleteDegree(String id) {
        try (Connection connection = dbManager.getConnection(); PreparedStatement statement = connection.prepareStatement("DELETE FROM degrees WHERE id = ?")) {
            statement.setString(1, id);
            statement.executeUpdate();
        } catch (SQLException e) {
            logger.error("Could not delete degree from database", e);
        }
    }

    public List<Degree> getDegrees() {
        try (Connection connection = dbManager.getConnection(); PreparedStatement statement = connection.prepareStatement("SELECT id, name FROM degrees")) {
            ResultSet resultSet = statement.executeQuery();
            List<Degree> degrees = new ArrayList<>();
            while (resultSet.next()) {
                String id = resultSet.getString("id");
                String name = resultSet.getString("name");
                degrees.add(new Degree(id, name));
            }
            return degrees;

        } catch (SQLException e) {
            logger.error("Could not get degrees from database", e);
            return List.of();
        }
    }

    public List<ModuleDegreeDTO> getDegreesAndModules() {
        try (Connection connection = dbManager.getConnection(); PreparedStatement statement = connection.prepareStatement("""
                SELECT modules.moduleid AS module_id, degrees.id AS degree_id, degrees.name AS degree_name
                FROM module_degrees
                LEFT JOIN degrees ON module_degrees.degree_id = degrees.id
                LEFT JOIN modules ON module_degrees.module_id = modules.moduleid
                """); ResultSet resultSet = statement.executeQuery()) {
            List<ModuleDegreeDTO> moduleDegrees = new ArrayList<>();
            while (resultSet.next()) {
                String moduleId = resultSet.getString("module_id");
                String degreeId = resultSet.getString("degree_id");
                String degreeName = resultSet.getString("degree_name");
                ModuleDegreeDTO dto = moduleDegrees.stream().filter(m -> m.moduleID().equals(moduleId)).findFirst().orElse(null);
                if (dto == null) {
                    dto = new ModuleDegreeDTO(moduleId, new ArrayList<>());
                    moduleDegrees.add(dto);
                }
                dto.degrees().add(new Degree(degreeId, degreeName));
            }
            return moduleDegrees;
        } catch (SQLException e) {
            logger.error("Could not get degrees and modules from database", e);
            return List.of();
        }

    }

    public void addDegreeToModule(String moduleID, String degreeID) {
        try(Connection connection = dbManager.getConnection();
        PreparedStatement statement = connection.prepareStatement("INSERT INTO module_degrees (module_id, degree_id) VALUES (?, ?)")) {
            statement.setString(1, moduleID);
            statement.setString(2, degreeID);
            statement.executeUpdate();
        } catch (SQLException e) {
            logger.error("Could not add degree to module in database", e);
        }
    }


    public void removeDegreeFromModule(String moduleID, String degreeID) {
        try(Connection connection = dbManager.getConnection();
            PreparedStatement statement = connection.prepareStatement("DELETE FROM module_degrees WHERE module_id = ? AND degree_id = ?")) {
            statement.setString(1, moduleID);
            statement.setString(2, degreeID);
            statement.executeUpdate();
        } catch (SQLException e) {
            logger.error("Could not remove degree to module in database", e);
        }
    }

    public void countDownload(Exam exam) {
        try (Connection connection = dbManager.getConnection();
             PreparedStatement statement = connection.prepareStatement("INSERT INTO downloads (examid) VALUES (?)")) {
            statement.setString(1, exam.examID());
            statement.executeUpdate();
        } catch (SQLException e) {
            logger.error("Could not count download in database", e);
        }
    }

    /**
     * Gets the most downloaded modules from the database from the last 2 years
     * Cached for one day
     * @return list of modules
     */
    public List<ModuleSearchResultDTO> getMostDownloadedModules() {
        if(lastTopModulesCacheUpdate.plus(1, ChronoUnit.DAYS).isAfter(Instant.now())){
            return topModulesCache;
        }
        List<ModuleSearchResultDTO> topModules = new ArrayList<>();
        String query = """
            SELECT m.module_name, m.moduleid, m.professors_array, m.exam_count
            FROM (
                SELECT e.moduleid, COUNT(d.examid) as dl_count
                FROM downloads d
                JOIN exams e ON d.examid = e.examid
                WHERE d.downloaded_at >= NOW() - INTERVAL '2 years'
                GROUP BY e.moduleid
                ORDER BY dl_count DESC
                LIMIT 10
            ) top_dl
            JOIN module_search_view m ON top_dl.moduleid = m.moduleid
            ORDER BY top_dl.dl_count DESC
            """;

        try (Connection connection = dbManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(query);
             ResultSet rs = statement.executeQuery()) {

            while (rs.next()) {
                Array profArray = rs.getArray("professors_array");
                String[] professors = profArray != null ? (String[]) profArray.getArray() : new String[0];

                topModules.add(new ModuleSearchResultDTO(
                        rs.getString("module_name"),
                        rs.getString("moduleid"),
                        professors,
                        rs.getInt("exam_count")
                ));
            }
        } catch (SQLException e) {
            logger.error("Could not get most downloaded modules from database", e);
        }
        lastTopModulesCacheUpdate = Instant.now();
        topModulesCache = topModules;
        return topModules;
    }

    /**
     * Retrieves the top downloaded exams along with their professor details.
     *
     * @param limit Maximum number of exams to return
     * @return List of top downloaded exams
     */
    public List<ProfessorExamDTO> getTopDownloadedExams(int limit) {
        List<ProfessorExamDTO> topExams = new ArrayList<>();
        String query = """
            SELECT e.examid, e.name, e.moduleid, e.semester, e.year, e.uploaddate,
                   e.fileid, e.uploaderid, e.status, e.professorid,
                   p.firstname, p.lastname
            FROM (
                SELECT examid, COUNT(examid) as dl_count
                FROM downloads
                GROUP BY examid
                ORDER BY dl_count DESC
                LIMIT ?
            ) d
            JOIN exams e ON d.examid = e.examid
            LEFT JOIN professors p ON e.professorid = p.professorid
            ORDER BY d.dl_count DESC
            """;

        try (Connection connection = dbManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(query)) {

            statement.setInt(1, limit);
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    Exam exam = new Exam(
                            rs.getString("name"),
                            rs.getString("examid"),
                            rs.getString("moduleid"),
                            rs.getInt("year"),
                            Semester.valueOf(rs.getString("semester")),
                            rs.getTimestamp("uploaddate").toInstant(),
                            rs.getString("fileid"),
                            rs.getString("uploaderid"),
                            ExamStatus.valueOf(rs.getString("status")),
                            rs.getString("professorid")
                    );

                    Professor professor = null;
                    if (rs.getString("professorid") != null) {
                        professor = new Professor(
                                rs.getString("professorid"),
                                rs.getString("firstname"),
                                rs.getString("lastname")
                        );
                    }
                    topExams.add(new ProfessorExamDTO(exam, professor));
                }
            }
        } catch (SQLException e) {
            logger.error("Could not get top downloaded exams", e);
        }
        return topExams;
    }

    /**
     * Gets the total number of downloads in the last 7 days.
     *
     * @return Weekly download count
     */
    public int getWeeklyDownloads() {
        return getDownloadsSince("1 week");
    }

    /**
     * Gets the total number of downloads in the last 30 days.
     *
     * @return Monthly download count
     */
    public int getMonthlyDownloads() {
        return getDownloadsSince("1 month");
    }

    /**
     * Gets the total number of downloads in the last 365 days.
     *
     * @return Yearly download count
     */
    public int getYearlyDownloads() {
        return getDownloadsSince("1 year");
    }

    /**
     * Gets the absolute total number of downloads across all time.
     *
     * @return Total download count
     */
    public int getTotalDownloads() {
        String query = "SELECT COUNT(examid) FROM downloads";
        try (Connection connection = dbManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(query);
             ResultSet rs = statement.executeQuery()) {
            if (rs.next()) {
                return rs.getInt(1);
            }
        } catch (SQLException e) {
            logger.error("Could not count total downloads", e);
        }
        return 0;
    }

    /**
     * Helper method to count downloads dynamically based on a Postgres interval.
     */
    private int getDownloadsSince(String postgresInterval) {
        String query = "SELECT COUNT(examid) FROM downloads WHERE downloaded_at >= NOW() - INTERVAL '" + postgresInterval + "'";
        try (Connection connection = dbManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(query);
             ResultSet rs = statement.executeQuery()) {
            if (rs.next()) {
                return rs.getInt(1);
            }
        } catch (SQLException e) {
            logger.error("Could not count downloads since interval: {}", postgresInterval, e);
        }
        return 0;
    }
}
