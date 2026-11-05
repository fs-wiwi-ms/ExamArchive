package ms.wiwi.examarchive.integration;

import ms.wiwi.examarchive.ModuleSearchResultDTO;
import ms.wiwi.examarchive.ProfessorExamDTO;
import ms.wiwi.examarchive.Repository;
import ms.wiwi.examarchive.admin.AdminExamListDTO;
import ms.wiwi.examarchive.admin.ModuleDegreeDTO;
import ms.wiwi.examarchive.model.Degree;
import ms.wiwi.examarchive.model.Exam;
import ms.wiwi.examarchive.model.ExamStatus;
import ms.wiwi.examarchive.model.KeyWord;
import ms.wiwi.examarchive.model.Module;
import ms.wiwi.examarchive.model.Motd;
import ms.wiwi.examarchive.model.Professor;
import ms.wiwi.examarchive.model.Role;
import ms.wiwi.examarchive.model.Semester;
import ms.wiwi.examarchive.model.User;
import ms.wiwi.examarchive.model.UserExam;
import ms.wiwi.examarchive.services.DatabaseService;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.*;

@Testcontainers
public class RepositoryIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:18.3");

    static DatabaseService databaseService;
    static Repository repository;

    @BeforeAll
    static void setUp() {
        databaseService = new DatabaseService(postgres.getHost(), postgres.getUsername(),
                postgres.getPassword(), postgres.getDatabaseName(), postgres.getMappedPort(5432));
        databaseService.migrateDatabase();
        repository = new Repository(databaseService);
    }

    @AfterAll
    static void tearDown() {
        databaseService.close();
    }

    private static Instant now() {
        return Instant.now().truncatedTo(ChronoUnit.MICROS);
    }

    private static String unique(String base) {
        return base + "-" + UUID.randomUUID().toString().substring(0, 8);
    }

    private static String uniqueWord(String base) {
        return base + Long.toHexString(UUID.randomUUID().getLeastSignificantBits());
    }

    private static Module newModule(String name) {
        return new Module(name, UUID.randomUUID().toString());
    }

    private static User newUser(String id) {
        return newUser(id, Role.USER);
    }

    private static User newUser(String id, Role role) {
        return new User(id, "First", "Last", now(), now(), id + "@example.com", role);
    }

    private static Exam newExam(String moduleID, int year, Semester semester, ExamStatus status) {
        return new Exam(unique("Exam"), UUID.randomUUID().toString(), moduleID, year, semester,
                now(), UUID.randomUUID().toString(), null, status, null, null);
    }

    private static Exam withProfessor(Exam exam, Professor professor) {
        return new Exam(exam.name(), exam.examID(), exam.moduleID(), exam.year(), exam.semester(),
                exam.uploadDate(), exam.fileID(), exam.uploaderID(), exam.status(),
                professor.professorID(), exam.scan());
    }

    private static Exam withUploader(Exam exam, User uploader) {
        return new Exam(exam.name(), exam.examID(), exam.moduleID(), exam.year(), exam.semester(),
                exam.uploadDate(), exam.fileID(), uploader.id(), exam.status(), exam.professorID(), exam.scan());
    }

    private static Professor newProfessor(String firstName, String lastName) {
        return repository.getOrCreateProfessor(firstName, lastName);
    }

    /**
     * Refreshes the materialized search view synchronously. The Repository only refreshes it
     * asynchronously, which would make view-based assertions flaky.
     */
    private static void refreshSearchView() {
        for (int attempt = 0; attempt < 50; attempt++) {
            try (Connection connection = databaseService.getConnection();
                 Statement statement = connection.createStatement()) {
                statement.execute("REFRESH MATERIALIZED VIEW module_search_view");
                return;
            } catch (SQLException e) {
                try {
                    Thread.sleep(100);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException("Interrupted while refreshing module_search_view", ie);
                }
            }
        }
        throw new IllegalStateException("Could not refresh module_search_view");
    }

    /** Inserts a download with a custom timestamp (Repository.countDownload always uses NOW()). */
    private static void insertDownloadAt(String examId, Instant when) {
        try (Connection connection = databaseService.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "INSERT INTO downloads (examid, downloaded_at) VALUES (?, ?)")) {
            statement.setString(1, examId);
            statement.setTimestamp(2, Timestamp.from(when));
            statement.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("Could not insert download", e);
        }
    }

    /** Inserts a user exam with a custom creation date (addUserExam always uses the current time). */
    private static void insertUserExamAt(String id, String userId, Instant when, int inputTokens,
                                         int outputTokens, String fileid) {
        try (Connection connection = databaseService.getConnection();
             PreparedStatement statement = connection.prepareStatement("""
                     INSERT INTO user_exams (id, user_id, creation_date, file_id, input_tokens, output_tokens)
                     VALUES (?, ?, ?, ?, ?, ?)
                     """)) {
            statement.setString(1, id);
            statement.setString(2, userId);
            statement.setTimestamp(3, Timestamp.from(when));
            statement.setString(4, fileid);
            statement.setInt(5, inputTokens);
            statement.setInt(6, outputTokens);
            statement.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("Could not insert user exam", e);
        }
    }

    private static void deleteMotd() {
        try (Connection connection = databaseService.getConnection();
             Statement statement = connection.createStatement()) {
            statement.execute("DELETE FROM motd");
        } catch (SQLException e) {
            throw new IllegalStateException("Could not delete motd", e);
        }
    }

    @Nested
    class Modules {

        @Test
        void addAndGetModule() {
            Module module = newModule(unique("Module"));
            repository.addModule(module.name(), module.moduleID());

            assertEquals(module, repository.getModule(module.moduleID()));
        }

        @Test
        void getModule_unknownId_returnsNull() {
            assertNull(repository.getModule(UUID.randomUUID().toString()));
        }

        @Test
        void getAllModules_containsNewModule_sortedByName() {
            Module module = newModule(unique("Module"));
            repository.addModule(module.name(), module.moduleID());

            List<Module> all = repository.getAllModules();
            assertTrue(all.contains(module));
            for (int i = 1; i < all.size(); i++) {
                assertTrue(all.get(i - 1).name().compareTo(all.get(i).name()) <= 0,
                        "Modules must be sorted by name");
            }
        }

        @Test
        void deleteModule_withoutExams_removesTheModule() {
            Module module = newModule(unique("Module"));
            repository.addModule(module.name(), module.moduleID());

            assertTrue(repository.deleteModule(module.moduleID()));
            assertNull(repository.getModule(module.moduleID()));
        }

        @Test
        void deleteModule_withExams_failsAndKeepsModule() {
            Module module = newModule(unique("Module"));
            repository.addModule(module.name(), module.moduleID());
            repository.addExam(newExam(module.moduleID(), 2025, Semester.WINTER, ExamStatus.ACCEPTED));

            assertFalse(repository.deleteModule(module.moduleID()));
            assertNotNull(repository.getModule(module.moduleID()));
        }

        @Test
        void deleteModule_cascadesKeywordsAndDegreeMappings() {
            Module module = newModule(unique("Module"));
            repository.addModule(module.name(), module.moduleID());
            Degree degree = new Degree(UUID.randomUUID().toString(), unique("Degree"));
            repository.addDegree(degree);
            repository.addDegreeToModule(module.moduleID(), degree.degreeID());
            repository.addKeyword(new KeyWord(uniqueWord("Keyword"), module.moduleID()));

            assertTrue(repository.deleteModule(module.moduleID()));

            assertTrue(repository.getKeywords().stream().noneMatch(k -> k.moduleID().equals(module.moduleID())));
            assertTrue(repository.getDegreesAndModules().stream()
                    .noneMatch(m -> m.moduleID().equals(module.moduleID())));
        }
    }

    @Nested
    class Exams {

        @Test
        void addAndGetExam_roundTrip() {
            Module module = newModule(unique("Module"));
            repository.addModule(module.name(), module.moduleID());
            User uploader = newUser(UUID.randomUUID().toString());
            repository.addOrUpdateUser(uploader);
            Professor professor = newProfessor(uniqueWord("First"), uniqueWord("Last"));

            Exam exam = new Exam("Exam-Roundtrip", UUID.randomUUID().toString(), module.moduleID(), 2024,
                    Semester.SUMMER, now(), "file-roundtrip", uploader.id(), ExamStatus.PENDING,
                    professor.professorID(), "scan content");
            repository.addExam(exam);

            assertEquals(exam, repository.getExam(exam.examID()));
        }

        @Test
        void getExam_unknownId_returnsNull() {
            assertNull(repository.getExam(UUID.randomUUID().toString()));
        }

        @Test
        void updateExam_updatesAllFields() {
            Module module = newModule(unique("Module"));
            repository.addModule(module.name(), module.moduleID());
            Professor professor = newProfessor(uniqueWord("First"), uniqueWord("Last"));
            Exam exam = newExam(module.moduleID(), 2024, Semester.SUMMER, ExamStatus.PENDING);
            repository.addExam(exam);

            Exam updated = new Exam("Renamed", exam.examID(), module.moduleID(), 2026, Semester.WINTER,
                    now(), "new-file", exam.uploaderID(), ExamStatus.ACCEPTED, professor.professorID(), "new scan");
            repository.updateExam(updated);

            assertEquals(updated, repository.getExam(exam.examID()));
        }

        @Test
        void updateExam_unknownId_isNoOp() {
            Exam exam = newExam(UUID.randomUUID().toString(), 2025, Semester.WINTER, ExamStatus.ACCEPTED);

            repository.updateExam(exam);

            assertNull(repository.getExam(exam.examID()));
        }

        @Test
        void addExam_withUnknownModule_isNotStored() {
            Exam exam = newExam(UUID.randomUUID().toString(), 2025, Semester.WINTER, ExamStatus.ACCEPTED);

            repository.addExam(exam);

            assertNull(repository.getExam(exam.examID()));
        }

        @Test
        void addExam_withUnknownUploader_isNotStored() {
            Module module = newModule(unique("Module"));
            repository.addModule(module.name(), module.moduleID());
            Exam exam = new Exam("Exam", UUID.randomUUID().toString(), module.moduleID(), 2025, Semester.WINTER,
                    now(), "file", UUID.randomUUID().toString(), ExamStatus.ACCEPTED, null, null);
            repository.addExam(exam);

            assertNull(repository.getExam(exam.examID()));
        }

        @Test
        void deleteExam_removesTheExam() {
            Module module = newModule(unique("Module"));
            repository.addModule(module.name(), module.moduleID());
            Exam exam = newExam(module.moduleID(), 2025, Semester.WINTER, ExamStatus.ACCEPTED);
            repository.addExam(exam);

            repository.deleteExam(exam.examID());

            assertNull(repository.getExam(exam.examID()));
        }

        @Test
        void deleteExam_deletesProfessorIfItHadNoOtherExams() {
            Module module = newModule(unique("Module"));
            repository.addModule(module.name(), module.moduleID());
            Professor professor = newProfessor(uniqueWord("First"), uniqueWord("Last"));
            Exam exam = withProfessor(newExam(module.moduleID(), 2025, Semester.WINTER, ExamStatus.ACCEPTED), professor);
            repository.addExam(exam);

            repository.deleteExam(exam.examID());

            assertNull(repository.getProfessor(professor.professorID()));
        }

        @Test
        void deleteExam_keepsProfessorIfOtherExamsRemain() {
            Module module = newModule(unique("Module"));
            repository.addModule(module.name(), module.moduleID());
            Professor professor = newProfessor(uniqueWord("First"), uniqueWord("Last"));
            Exam first = withProfessor(newExam(module.moduleID(), 2025, Semester.WINTER, ExamStatus.ACCEPTED), professor);
            Exam second = withProfessor(newExam(module.moduleID(), 2026, Semester.SUMMER, ExamStatus.ACCEPTED), professor);
            repository.addExam(first);
            repository.addExam(second);

            repository.deleteExam(first.examID());

            assertNotNull(repository.getProfessor(professor.professorID()));
            assertNotNull(repository.getExam(second.examID()));
        }

        @Test
        void deleteExam_unknownId_doesNotThrow() {
            assertDoesNotThrow(() -> repository.deleteExam(UUID.randomUUID().toString()));
        }
    }

    @Nested
    class ExamListings {

        private AdminExamListDTO findAdminDto(String examId) {
            return repository.getAllExams().stream()
                    .filter(d -> d.exam().examID().equals(examId))
                    .findFirst()
                    .orElseThrow();
        }

        @Test
        void getAllExams_joinsModuleProfessorAndUploader() {
            Module module = newModule(unique("Module"));
            repository.addModule(module.name(), module.moduleID());
            User uploader = newUser(UUID.randomUUID().toString());
            repository.addOrUpdateUser(uploader);
            Professor professor = newProfessor(uniqueWord("First"), uniqueWord("Last"));
            Exam exam = withUploader(newExam(module.moduleID(), 2025, Semester.WINTER, ExamStatus.ACCEPTED), uploader);
            exam = withProfessor(exam, professor);
            repository.addExam(exam);

            AdminExamListDTO dto = findAdminDto(exam.examID());

            assertEquals(module, dto.module());
            assertEquals(exam, dto.exam());
            assertEquals(professor, dto.professor());
            assertEquals(uploader, dto.user());
        }

        @Test
        void getAllExams_deletedUploader_resultsInNullUser() {
            Module module = newModule(unique("Module"));
            repository.addModule(module.name(), module.moduleID());
            User uploader = newUser(UUID.randomUUID().toString());
            repository.addOrUpdateUser(uploader);
            Exam exam = withUploader(newExam(module.moduleID(), 2025, Semester.WINTER, ExamStatus.ACCEPTED), uploader);
            repository.addExam(exam);
            repository.deleteOldAccounts(List.of(uploader.id()));

            AdminExamListDTO dto = findAdminDto(exam.examID());

            assertNull(dto.user());
            assertNull(dto.exam().uploaderID());
        }

        @Test
        void getExamsAndProfessors_returnsOnlyAcceptedExams_orderedByYearAndSemesterDescending() {
            Module module = newModule(unique("Module"));
            repository.addModule(module.name(), module.moduleID());
            Exam winter2025 = newExam(module.moduleID(), 2025, Semester.WINTER, ExamStatus.ACCEPTED);
            Exam summer2025 = newExam(module.moduleID(), 2025, Semester.SUMMER, ExamStatus.ACCEPTED);
            Exam summer2026 = newExam(module.moduleID(), 2026, Semester.SUMMER, ExamStatus.ACCEPTED);
            Exam pending2026 = newExam(module.moduleID(), 2026, Semester.WINTER, ExamStatus.PENDING);
            Exam rejected2024 = newExam(module.moduleID(), 2024, Semester.SUMMER, ExamStatus.REJECTED);
            for (Exam exam : List.of(winter2025, summer2025, summer2026, pending2026, rejected2024)) {
                repository.addExam(exam);
            }

            List<ProfessorExamDTO> result = repository.getExamsAndProfessors(module.moduleID());

            assertEquals(List.of(summer2026.examID(), winter2025.examID(), summer2025.examID()),
                    result.stream().map(d -> d.exam().examID()).toList());
        }

        @Test
        void getExamsAndProfessors_examWithoutProfessor_hasNullProfessorFields() {
            Module module = newModule(unique("Module"));
            repository.addModule(module.name(), module.moduleID());
            repository.addExam(newExam(module.moduleID(), 2025, Semester.WINTER, ExamStatus.ACCEPTED));

            ProfessorExamDTO dto = repository.getExamsAndProfessors(module.moduleID()).get(0);

            assertNull(dto.professor().professorID());
        }

        @Test
        void getExamsAndProfessors_unknownModule_returnsEmptyList() {
            assertTrue(repository.getExamsAndProfessors(UUID.randomUUID().toString()).isEmpty());
        }

        @Test
        void queryExamsFilterByDateAndProf_returnsUpToThreeNewestAcceptedExams() {
            Module module = newModule(unique("Module"));
            repository.addModule(module.name(), module.moduleID());
            Exam exam2020 = newExam(module.moduleID(), 2020, Semester.WINTER, ExamStatus.ACCEPTED);
            Exam exam2021 = newExam(module.moduleID(), 2021, Semester.SUMMER, ExamStatus.ACCEPTED);
            Exam exam2022 = newExam(module.moduleID(), 2022, Semester.WINTER, ExamStatus.ACCEPTED);
            Exam exam2023 = newExam(module.moduleID(), 2023, Semester.SUMMER, ExamStatus.ACCEPTED);
            Exam pending2024 = newExam(module.moduleID(), 2024, Semester.WINTER, ExamStatus.PENDING);
            for (Exam exam : List.of(exam2020, exam2021, exam2022, exam2023, pending2024)) {
                repository.addExam(exam);
            }

            List<Exam> result = repository.queryExamsFilterByDateAndProf(module.moduleID(), null, null);

            assertEquals(List.of(exam2023.examID(), exam2022.examID(), exam2021.examID()),
                    result.stream().map(Exam::examID).toList());
        }

        @Test
        void queryExamsFilterByDateAndProf_filtersByYear() {
            Module module = newModule(unique("Module"));
            repository.addModule(module.name(), module.moduleID());
            Exam exam2021 = newExam(module.moduleID(), 2021, Semester.SUMMER, ExamStatus.ACCEPTED);
            Exam exam2022 = newExam(module.moduleID(), 2022, Semester.WINTER, ExamStatus.ACCEPTED);
            Exam exam2023 = newExam(module.moduleID(), 2023, Semester.SUMMER, ExamStatus.ACCEPTED);
            for (Exam exam : List.of(exam2021, exam2022, exam2023)) {
                repository.addExam(exam);
            }

            List<Exam> result = repository.queryExamsFilterByDateAndProf(module.moduleID(), 2022, null);

            assertEquals(List.of(exam2023.examID(), exam2022.examID()),
                    result.stream().map(Exam::examID).toList());
        }

        @Test
        void queryExamsFilterByDateAndProf_filtersByProfessors() {
            Module module = newModule(unique("Module"));
            repository.addModule(module.name(), module.moduleID());
            Professor professorA = newProfessor(uniqueWord("FirstA"), uniqueWord("LastA"));
            Professor professorB = newProfessor(uniqueWord("FirstB"), uniqueWord("LastB"));
            Exam examA2023 = withProfessor(newExam(module.moduleID(), 2023, Semester.SUMMER, ExamStatus.ACCEPTED), professorA);
            Exam examB2024 = withProfessor(newExam(module.moduleID(), 2024, Semester.WINTER, ExamStatus.ACCEPTED), professorB);
            Exam examA2022 = withProfessor(newExam(module.moduleID(), 2022, Semester.WINTER, ExamStatus.ACCEPTED), professorA);
            for (Exam exam : List.of(examA2023, examB2024, examA2022)) {
                repository.addExam(exam);
            }

            List<Exam> byProfessorA = repository.queryExamsFilterByDateAndProf(module.moduleID(), null, List.of(professorA));
            assertEquals(List.of(examA2023.examID(), examA2022.examID()),
                    byProfessorA.stream().map(Exam::examID).toList());

            List<Exam> byProfessorB = repository.queryExamsFilterByDateAndProf(module.moduleID(), null, List.of(professorB));
            assertEquals(List.of(examB2024.examID()),
                    byProfessorB.stream().map(Exam::examID).toList());
        }

        @Test
        void queryExamsFilterByDateAndProf_combinesYearAndProfessorFilters() {
            Module module = newModule(unique("Module"));
            repository.addModule(module.name(), module.moduleID());
            Professor professorA = newProfessor(uniqueWord("FirstA"), uniqueWord("LastA"));
            Professor professorB = newProfessor(uniqueWord("FirstB"), uniqueWord("LastB"));
            Exam examA2021 = withProfessor(newExam(module.moduleID(), 2021, Semester.SUMMER, ExamStatus.ACCEPTED), professorA);
            Exam examA2023 = withProfessor(newExam(module.moduleID(), 2023, Semester.SUMMER, ExamStatus.ACCEPTED), professorA);
            Exam examB2023 = withProfessor(newExam(module.moduleID(), 2023, Semester.WINTER, ExamStatus.ACCEPTED), professorB);
            for (Exam exam : List.of(examA2021, examA2023, examB2023)) {
                repository.addExam(exam);
            }

            List<Exam> result = repository.queryExamsFilterByDateAndProf(module.moduleID(), 2023, List.of(professorA));

            assertEquals(List.of(examA2023.examID()), result.stream().map(Exam::examID).toList());
        }
    }

    // ------------------------------------------------------------------
    // Professors
    // ------------------------------------------------------------------

    @Nested
    class Professors {

        @Test
        void getOrCreateProfessor_createsNewProfessor() {
            Professor professor = newProfessor(uniqueWord("First"), uniqueWord("Last"));

            assertNotNull(professor);
            assertEquals(professor, repository.getProfessor(professor.professorID()));
        }

        @Test
        void getOrCreateProfessor_matchesCaseInsensitively_andUpdatesFirstNameCasing() {
            String first = uniqueWord("alice");
            String last = uniqueWord("smith");
            Professor firstCreated = repository.getOrCreateProfessor(first, last);
            Professor recreated = repository.getOrCreateProfessor(first.toUpperCase(), last.toUpperCase());

            assertEquals(firstCreated.professorID(), recreated.professorID());
            // The upsert only re-cases the first name; the last name keeps its original casing.
            assertEquals(first.toUpperCase(), recreated.firstName());
            assertEquals(last, recreated.lastName());
        }

        @Test
        void getOrCreateProfessor_differentNames_createDistinctProfessors() {
            Professor professorA = newProfessor(uniqueWord("First"), uniqueWord("LastA"));
            Professor professorB = newProfessor(uniqueWord("First"), uniqueWord("LastB"));

            assertNotEquals(professorA.professorID(), professorB.professorID());
        }

        @Test
        void getProfessor_unknownId_returnsNull() {
            assertNull(repository.getProfessor(UUID.randomUUID().toString()));
        }

        @Test
        void searchProfessorsForModule_returnsOnlyProfessorsWithAcceptedExams() {
            Module module = newModule(unique("Module"));
            repository.addModule(module.name(), module.moduleID());
            Professor pendingOnly = newProfessor(uniqueWord("FirstP"), uniqueWord("LastP"));
            Professor accepted = newProfessor(uniqueWord("FirstA"), uniqueWord("LastA"));
            repository.addExam(withProfessor(
                    newExam(module.moduleID(), 2025, Semester.WINTER, ExamStatus.PENDING), pendingOnly));
            repository.addExam(withProfessor(
                    newExam(module.moduleID(), 2025, Semester.SUMMER, ExamStatus.ACCEPTED), accepted));

            List<Professor> result = repository.searchProfessorsForModule(module.moduleID(), null, null);

            assertEquals(List.of(accepted.professorID()),
                    result.stream().map(Professor::professorID).toList());
        }

        @Test
        void searchProfessorsForModule_filtersBySubstringsCaseInsensitively() {
            Module module = newModule(unique("Module"));
            repository.addModule(module.name(), module.moduleID());
            Professor alice = newProfessor(uniqueWord("Alice"), uniqueWord("Smith"));
            Professor bob = newProfessor(uniqueWord("Bob"), uniqueWord("Jones"));
            repository.addExam(withProfessor(
                    newExam(module.moduleID(), 2025, Semester.WINTER, ExamStatus.ACCEPTED), alice));
            repository.addExam(withProfessor(
                    newExam(module.moduleID(), 2025, Semester.SUMMER, ExamStatus.ACCEPTED), bob));

            assertEquals(List.of(alice.professorID()),
                    repository.searchProfessorsForModule(module.moduleID(), "lice", null)
                            .stream().map(Professor::professorID).toList());
            assertEquals(List.of(bob.professorID()),
                    repository.searchProfessorsForModule(module.moduleID(), null, "JONES")
                            .stream().map(Professor::professorID).toList());
        }

        @Test
        void searchProfessorsForModule_blankFiltersAreIgnored() {
            Module module = newModule(unique("Module"));
            repository.addModule(module.name(), module.moduleID());
            Professor professor = newProfessor(uniqueWord("First"), uniqueWord("Last"));
            repository.addExam(withProfessor(
                    newExam(module.moduleID(), 2025, Semester.WINTER, ExamStatus.ACCEPTED), professor));

            List<Professor> result = repository.searchProfessorsForModule(module.moduleID(), "   ", null);

            assertEquals(List.of(professor.professorID()),
                    result.stream().map(Professor::professorID).toList());
        }
    }

    // ------------------------------------------------------------------
    // Users
    // ------------------------------------------------------------------

    @Nested
    class Users {

        @Test
        void addOrUpdateUser_insertsNewUser() {
            User user = newUser(UUID.randomUUID().toString());

            User stored = repository.addOrUpdateUser(user);

            assertEquals(user, stored);
            assertEquals(user, repository.getUser(user.id()));
        }

        @Test
        void addOrUpdateUser_updatesExistingUser_andPreservesCreatedAt() {
            Instant createdAt = now().minus(9, ChronoUnit.DAYS);
            String id = UUID.randomUUID().toString();
            User user = new User(id, "First", "Last", createdAt, createdAt, "old@example.com", Role.USER);
            repository.addOrUpdateUser(user);

            User stored = repository.addOrUpdateUser(new User(id, "NewFirst", "NewLast", now(),
                    now().plus(1, ChronoUnit.DAYS), "new@example.com", Role.ADMIN));

            assertEquals("NewFirst", stored.firstname());
            assertEquals("NewLast", stored.lastname());
            assertEquals("new@example.com", stored.email());
            assertEquals(Role.ADMIN, stored.role());
            assertEquals(createdAt, stored.createdAt());
        }

        @Test
        void getUser_unknownId_returnsNull() {
            assertNull(repository.getUser(UUID.randomUUID().toString()));
        }

        @Test
        void updateUserRole_changesTheRole() {
            User user = newUser(UUID.randomUUID().toString());
            repository.addOrUpdateUser(user);

            repository.updateUserRole(user.id(), Role.ADMIN);

            assertEquals(Role.ADMIN, repository.getUser(user.id()).role());
        }

        @Test
        void countUsersByRole_countsPerRole() {
            int usersBefore = repository.countUsersByRole(Role.USER);
            int adminsBefore = repository.countUsersByRole(Role.ADMIN);
            int blockedBefore = repository.countUsersByRole(Role.BLOCKED);
            repository.addOrUpdateUser(newUser(UUID.randomUUID().toString(), Role.USER));
            repository.addOrUpdateUser(newUser(UUID.randomUUID().toString(), Role.ADMIN));
            repository.addOrUpdateUser(newUser(UUID.randomUUID().toString(), Role.BLOCKED));

            assertEquals(usersBefore + 1, repository.countUsersByRole(Role.USER));
            assertEquals(adminsBefore + 1, repository.countUsersByRole(Role.ADMIN));
            assertEquals(blockedBefore + 1, repository.countUsersByRole(Role.BLOCKED));
        }

        @Test
        void getAdminEmails_returnsOnlyAdminEmails() {
            User admin = newUser(UUID.randomUUID().toString(), Role.ADMIN);
            User regular = newUser(UUID.randomUUID().toString(), Role.USER);
            repository.addOrUpdateUser(admin);
            repository.addOrUpdateUser(regular);

            List<String> emails = repository.getAdminEmails();

            assertTrue(emails.contains(admin.email()));
            assertFalse(emails.contains(regular.email()));
        }

        @Test
        void searchUsers_findsUsersWithSimilarWord() {
            String surname = uniqueWord("Zqxbc");
            User user = new User(UUID.randomUUID().toString(), "Hans", surname, now(), now(),
                    "hans@example.com", Role.USER);
            repository.addOrUpdateUser(user);

            List<User> results = repository.searchUsers(surname);

            assertTrue(results.stream().anyMatch(u -> u.id().equals(user.id())));
        }

        @Test
        void searchUsers_withoutSimilarWord_returnsEmptyList() {
            assertTrue(repository.searchUsers(uniqueWord("Nonexistentword")).isEmpty());
        }

        @Test
        void searchUsers_returnsAtMostTenUsers() {
            String surname = uniqueWord("Qwzxc");
            for (int i = 0; i < 12; i++) {
                repository.addOrUpdateUser(new User(UUID.randomUUID().toString(), "First" + i, surname,
                        now(), now(), "user" + i + "@example.com", Role.USER));
            }

            List<User> results = repository.searchUsers(surname);

            assertEquals(10, results.size());
        }
    }

    @Nested
    class Keywords {

        @Test
        void addAndGetKeywords() {
            Module module = newModule(unique("Module"));
            repository.addModule(module.name(), module.moduleID());
            KeyWord keyword = new KeyWord(uniqueWord("Keyword"), module.moduleID());
            repository.addKeyword(keyword);

            assertTrue(repository.getKeywords().contains(keyword));
        }

        @Test
        void addKeyword_duplicateIsIgnored() {
            Module module = newModule(unique("Module"));
            repository.addModule(module.name(), module.moduleID());
            KeyWord keyword = new KeyWord(uniqueWord("Keyword"), module.moduleID());
            repository.addKeyword(keyword);
            repository.addKeyword(keyword);

            assertEquals(1, repository.getKeywords().stream().filter(keyword::equals).count());
        }

        @Test
        void deleteKeyword_removesTheKeyword() {
            Module module = newModule(unique("Module"));
            repository.addModule(module.name(), module.moduleID());
            KeyWord keyword = new KeyWord(uniqueWord("Keyword"), module.moduleID());
            repository.addKeyword(keyword);

            repository.deleteKeyword(keyword);

            assertFalse(repository.getKeywords().contains(keyword));
        }

        @Test
        void addKeyword_unknownModule_isNotStored() {
            KeyWord keyword = new KeyWord(uniqueWord("Keyword"), UUID.randomUUID().toString());

            repository.addKeyword(keyword);

            assertFalse(repository.getKeywords().contains(keyword));
        }
    }

    @Nested
    class ModuleSearch {

        @Test
        void searchModules_matchesOnModuleName() {
            Module module = newModule(uniqueWord("Searchmod"));
            repository.addModule(module.name(), module.moduleID());
            refreshSearchView();

            List<ModuleSearchResultDTO> results = repository.searchModules(module.name(), null);

            assertTrue(results.stream().anyMatch(r -> r.moduleID().equals(module.moduleID())));
        }

        @Test
        void searchModules_matchesOnKeyword() {
            Module module = newModule(unique("Module"));
            repository.addModule(module.name(), module.moduleID());
            String keyword = uniqueWord("Photolum");
            repository.addKeyword(new KeyWord(keyword, module.moduleID()));
            refreshSearchView();

            assertTrue(repository.searchModules(keyword, null).stream()
                    .anyMatch(r -> r.moduleID().equals(module.moduleID())));
        }

        @Test
        void searchModules_matchesOnProfessorName_andReportsProfessors() {
            Module module = newModule(unique("Module"));
            repository.addModule(module.name(), module.moduleID());
            Professor professor = newProfessor(uniqueWord("Quasar"), uniqueWord("Last"));
            repository.addExam(withProfessor(
                    newExam(module.moduleID(), 2025, Semester.WINTER, ExamStatus.ACCEPTED), professor));
            refreshSearchView();

            ModuleSearchResultDTO result = repository.searchModules(professor.firstName(), null).stream()
                    .filter(r -> r.moduleID().equals(module.moduleID()))
                    .findFirst()
                    .orElseThrow();

            assertEquals(1, result.exams());
            assertEquals(List.of(professor.firstName() + " " + professor.lastName()),
                    List.of(result.professors()));
        }

        @Test
        void searchModules_degreeFilter_limitsToModulesWithMatchingDegree() {
            String degreeId = UUID.randomUUID().toString();
            repository.addDegree(new Degree(degreeId, unique("Degree")));
            Module withDegree = newModule(uniqueWord("Degreeflt"));
            repository.addModule(withDegree.name(), withDegree.moduleID());
            repository.addDegreeToModule(withDegree.moduleID(), degreeId);
            Module withoutDegree = newModule(uniqueWord("Degreeflt"));
            repository.addModule(withoutDegree.name(), withoutDegree.moduleID());
            refreshSearchView();

            List<ModuleSearchResultDTO> withFilter = repository.searchModules("Degreeflt", List.of(degreeId));
            assertEquals(List.of(withDegree.moduleID()),
                    withFilter.stream().map(ModuleSearchResultDTO::moduleID).toList());

            List<ModuleSearchResultDTO> withoutFilter = repository.searchModules("Degreeflt", null);
            assertTrue(withoutFilter.stream().anyMatch(r -> r.moduleID().equals(withoutDegree.moduleID())));

            assertTrue(repository.searchModules("Degreeflt",
                    List.of(UUID.randomUUID().toString())).isEmpty());
        }

        @Test
        void searchModules_noMatch_returnsEmptyList() {
            refreshSearchView();

            assertTrue(repository.searchModules(uniqueWord("Definitelynotfound"), null).isEmpty());
        }

        @Test
        void searchModules_returnsAtMostTenModules() {
            for (int i = 0; i < 12; i++) {
                Module module = newModule(uniqueWord("Zsearchmod"));
                repository.addModule(module.name(), module.moduleID());
            }
            refreshSearchView();

            assertEquals(10, repository.searchModules("Zsearchmod", null).size());
        }
    }

    // ------------------------------------------------------------------
    // Degrees
    // ------------------------------------------------------------------

    @Nested
    class Degrees {

        @Test
        void addAndGetDegree() {
            Degree degree = new Degree(UUID.randomUUID().toString(), unique("Degree"));

            repository.addDegree(degree);

            assertTrue(repository.getDegrees().contains(degree));
        }

        @Test
        void addDegree_duplicateId_isIgnored() {
            String id = UUID.randomUUID().toString();
            repository.addDegree(new Degree(id, unique("Degree")));
            repository.addDegree(new Degree(id, unique("Degree")));

            assertEquals(1, repository.getDegrees().stream().filter(d -> d.degreeID().equals(id)).count());
        }

        @Test
        void deleteDegree_removesTheDegree() {
            Degree degree = new Degree(UUID.randomUUID().toString(), unique("Degree"));
            repository.addDegree(degree);

            repository.deleteDegree(degree.degreeID());

            assertFalse(repository.getDegrees().contains(degree));
        }

        @Test
        void deleteDegree_unknownId_doesNotThrow() {
            assertDoesNotThrow(() -> repository.deleteDegree(UUID.randomUUID().toString()));
        }

        @Test
        void getDegreesAndModules_groupsDegreesPerModule() {
            Module module = newModule(unique("Module"));
            repository.addModule(module.name(), module.moduleID());
            Degree degreeA = new Degree(UUID.randomUUID().toString(), unique("Degree"));
            Degree degreeB = new Degree(UUID.randomUUID().toString(), unique("Degree"));
            repository.addDegree(degreeA);
            repository.addDegree(degreeB);
            repository.addDegreeToModule(module.moduleID(), degreeA.degreeID());
            repository.addDegreeToModule(module.moduleID(), degreeB.degreeID());

            ModuleDegreeDTO dto = repository.getDegreesAndModules().stream()
                    .filter(m -> m.moduleID().equals(module.moduleID()))
                    .findFirst()
                    .orElseThrow();

            assertEquals(2, dto.degrees().size());
            assertTrue(dto.degrees().contains(degreeA));
            assertTrue(dto.degrees().contains(degreeB));
        }

        @Test
        void addDegreeToModule_unknownModuleOrDegree_isNotStored() {
            String moduleId = UUID.randomUUID().toString();
            String degreeId = UUID.randomUUID().toString();
            repository.addDegreeToModule(moduleId, degreeId);
            repository.addDegreeToModule(moduleId, degreeId);

            assertTrue(repository.getDegreesAndModules().stream()
                    .noneMatch(m -> m.moduleID().equals(moduleId)));
        }

        @Test
        void removeDegreeFromModule_removesOnlyTheMapping() {
            Module module = newModule(unique("Module"));
            repository.addModule(module.name(), module.moduleID());
            Degree degreeA = new Degree(UUID.randomUUID().toString(), unique("Degree"));
            Degree degreeB = new Degree(UUID.randomUUID().toString(), unique("Degree"));
            repository.addDegree(degreeA);
            repository.addDegree(degreeB);
            repository.addDegreeToModule(module.moduleID(), degreeA.degreeID());
            repository.addDegreeToModule(module.moduleID(), degreeB.degreeID());

            repository.removeDegreeFromModule(module.moduleID(), degreeA.degreeID());

            ModuleDegreeDTO dto = repository.getDegreesAndModules().stream()
                    .filter(m -> m.moduleID().equals(module.moduleID()))
                    .findFirst()
                    .orElseThrow();
            assertEquals(List.of(degreeB), dto.degrees());
        }

        @Test
        void deleteDegree_cascadesDegreeModuleMappings() {
            Module module = newModule(unique("Module"));
            repository.addModule(module.name(), module.moduleID());
            Degree degree = new Degree(UUID.randomUUID().toString(), unique("Degree"));
            repository.addDegree(degree);
            repository.addDegreeToModule(module.moduleID(), degree.degreeID());

            repository.deleteDegree(degree.degreeID());

            assertTrue(repository.getDegreesAndModules().stream()
                    .flatMap(m -> m.degrees().stream())
                    .noneMatch(d -> d.degreeID().equals(degree.degreeID())));
        }
    }

    // ------------------------------------------------------------------
    // Message of the day (global single-row table, each test resets it)
    // ------------------------------------------------------------------

    @Nested
    class MessageOfTheDay {

        @Test
        void getMotdText_noMotdStored_returnsNull() {
            deleteMotd();

            assertNull(repository.getMotdText());
        }

        @Test
        void getMotdText_notExpired_returnsTheMotd() {
            deleteMotd();
            Instant expires = now().plus(1, ChronoUnit.HOURS);
            repository.updateMotd(new Motd("Hello world", expires));

            assertEquals(new Motd("Hello world", expires), repository.getMotdText());
        }

        @Test
        void getMotdText_expired_returnsNull() {
            deleteMotd();
            repository.updateMotd(new Motd("Stale message", now().minus(1, ChronoUnit.HOURS)));

            assertNull(repository.getMotdText());
        }

        @Test
        void updateMotd_replacesThePreviousMotd() {
            deleteMotd();
            repository.updateMotd(new Motd("First message", now().plus(1, ChronoUnit.HOURS)));
            Instant expires = now().plus(2, ChronoUnit.HOURS);
            repository.updateMotd(new Motd("Second message", expires));

            assertEquals(new Motd("Second message", expires), repository.getMotdText());
        }
    }

    // ------------------------------------------------------------------
    // Downloads
    // ------------------------------------------------------------------

    @Nested
    class Downloads {

        /** Inserts many download rows for an exam in a single statement. */
        private static void insertDownloads(String examId, int count) {
            try (Connection connection = databaseService.getConnection();
                 PreparedStatement statement = connection.prepareStatement(
                         "INSERT INTO downloads (examid) SELECT ? FROM generate_series(1, ?)")) {
                statement.setString(1, examId);
                statement.setInt(2, count);
                statement.executeUpdate();
            } catch (SQLException e) {
                throw new IllegalStateException("Could not insert downloads", e);
            }
        }

        @Test
        void countDownload_and_getTotalDownloads_increaseTheTotalCount() {
            Module module = newModule(unique("Module"));
            repository.addModule(module.name(), module.moduleID());
            Exam exam = newExam(module.moduleID(), 2025, Semester.WINTER, ExamStatus.ACCEPTED);
            repository.addExam(exam);

            int totalBefore = repository.getTotalDownloads();
            repository.countDownload(exam);
            repository.countDownload(exam);

            assertEquals(totalBefore + 2, repository.getTotalDownloads());
        }

        @Test
        void downloadWindows_countDownloadsByAge() {
            Module module = newModule(unique("Module"));
            repository.addModule(module.name(), module.moduleID());
            Exam exam = newExam(module.moduleID(), 2025, Semester.WINTER, ExamStatus.ACCEPTED);
            repository.addExam(exam);

            int weeklyBefore = repository.getWeeklyDownloads();
            int monthlyBefore = repository.getMonthlyDownloads();
            int yearlyBefore = repository.getYearlyDownloads();

            repository.countDownload(exam);                                     // now: in all windows
            insertDownloadAt(exam.examID(), now().minus(8, ChronoUnit.DAYS));   // older than 1 week
            insertDownloadAt(exam.examID(), now().minus(40, ChronoUnit.DAYS));  // older than 1 month
            insertDownloadAt(exam.examID(), now().minus(3 * 365, ChronoUnit.DAYS));  // older than 1 year

            assertEquals(weeklyBefore + 1, repository.getWeeklyDownloads());
            assertEquals(monthlyBefore + 2, repository.getMonthlyDownloads());
            assertEquals(yearlyBefore + 3, repository.getYearlyDownloads());
        }

        @Test
        void getMostDownloadedModules_ranksModulesByRecentDownloadCount() {
            Exam[] exams = new Exam[3];
            int[] counts = {1200, 1150, 1120};
            for (int i = 0; i < 3; i++) {
                Module module = newModule(uniqueWord("Rankmod"));
                repository.addModule(module.name(), module.moduleID());
                exams[i] = newExam(module.moduleID(), 2025, Semester.WINTER, ExamStatus.ACCEPTED);
                repository.addExam(exams[i]);
                insertDownloads(exams[i].examID(), counts[i]);
            }
            refreshSearchView();
            Repository freshRepository = new Repository(databaseService);

            List<ModuleSearchResultDTO> result = freshRepository.getMostDownloadedModules();

            assertEquals(List.of(exams[0].moduleID(), exams[1].moduleID(), exams[2].moduleID()),
                    result.stream().limit(3).map(ModuleSearchResultDTO::moduleID).toList());
        }

        @Test
        void getMostDownloadedModules_limitsToTenModules() {
            Module[] modules = new Module[12];
            for (int i = 0; i < 12; i++) {
                modules[i] = newModule(uniqueWord("Topmod"));
                repository.addModule(modules[i].name(), modules[i].moduleID());
                Exam exam = newExam(modules[i].moduleID(), 2025, Semester.WINTER, ExamStatus.ACCEPTED);
                repository.addExam(exam);
                insertDownloads(exam.examID(), 1000 + 10 * i);
            }
            refreshSearchView();
            Repository freshRepository = new Repository(databaseService);

            List<ModuleSearchResultDTO> result = freshRepository.getMostDownloadedModules();

            assertEquals(10, result.size());
            List<String> idsDescending = IntStream.range(0, 12)
                    .map(i -> 11 - i)
                    .mapToObj(i -> modules[i].moduleID())
                    .toList();
            List<String> present = result.stream().map(ModuleSearchResultDTO::moduleID)
                    .filter(idsDescending::contains)
                    .toList();
            assertEquals(idsDescending.subList(0, present.size()), present);
            assertTrue(present.size() >= 7,
                    "at least 7 of the 12 modules must make it into the top 10, got " + present.size());
        }

        @Test
        void getMostDownloadedModules_ignoresDownloadsOlderThanTwoYears() {
            Module module = newModule(uniqueWord("Oldmod"));
            repository.addModule(module.name(), module.moduleID());
            Exam exam = newExam(module.moduleID(), 2025, Semester.WINTER, ExamStatus.ACCEPTED);
            repository.addExam(exam);
            insertDownloadAt(exam.examID(), now().minus(3 * 365, ChronoUnit.DAYS));
            refreshSearchView();
            Repository freshRepository = new Repository(databaseService);

            assertFalse(freshRepository.getMostDownloadedModules().stream()
                    .anyMatch(r -> r.moduleID().equals(module.moduleID())));
        }

        @Test
        void getTopDownloadedExams_ordersByDownloadCount_andHonorsLimit() {
            Exam[] exams = new Exam[3];
            int[] counts = {15000, 14000, 13000};
            for (int i = 0; i < 3; i++) {
                Module module = newModule(uniqueWord("Topexmod"));
                repository.addModule(module.name(), module.moduleID());
                exams[i] = newExam(module.moduleID(), 2025, Semester.WINTER, ExamStatus.ACCEPTED);
                repository.addExam(exams[i]);
                insertDownloads(exams[i].examID(), counts[i]);
            }

            List<ProfessorExamDTO> result = repository.getTopDownloadedExams(100);

            assertEquals(List.of(exams[0].examID(), exams[1].examID(), exams[2].examID()),
                    result.stream().limit(3).map(d -> d.exam().examID()).toList());
            assertEquals(1, repository.getTopDownloadedExams(1).size());
        }

        @Test
        void getTopDownloadedExams_returnsOnlyAcceptedExams_evenIfRejectedHaveMoreDownloads() {
            Module module = newModule(unique("Module"));
            repository.addModule(module.name(), module.moduleID());
            Exam rejected1 = newExam(module.moduleID(), 2025, Semester.WINTER, ExamStatus.REJECTED);
            Exam rejected2 = newExam(module.moduleID(), 2024, Semester.SUMMER, ExamStatus.REJECTED);
            Exam rejected3 = newExam(module.moduleID(), 2024, Semester.WINTER, ExamStatus.PENDING);
            Exam accepted1 = newExam(module.moduleID(), 2023, Semester.SUMMER, ExamStatus.ACCEPTED);
            Exam accepted2 = newExam(module.moduleID(), 2023, Semester.WINTER, ExamStatus.ACCEPTED);
            for (Exam exam : List.of(rejected1, rejected2, rejected3, accepted1, accepted2)) {
                repository.addExam(exam);
            }
            insertDownloads(rejected1.examID(), 12000);
            insertDownloads(rejected2.examID(), 11000);
            insertDownloads(rejected3.examID(), 10000);
            insertDownloads(accepted1.examID(), 9000);
            insertDownloads(accepted2.examID(), 8000);

            List<ProfessorExamDTO> topTwo = repository.getTopDownloadedExams(2);
            List<ProfessorExamDTO> all = repository.getTopDownloadedExams(100);
            List<String> allExamIds = all.stream().map(d -> d.exam().examID()).toList();

            assertEquals(List.of(accepted1.examID(), accepted2.examID()),
                    topTwo.stream().map(d -> d.exam().examID()).toList());
            assertFalse(allExamIds.contains(rejected1.examID()));
            assertFalse(allExamIds.contains(rejected2.examID()));
            assertFalse(allExamIds.contains(rejected3.examID()));
        }

        @Test
        void getTopDownloadedExams_examWithoutProfessor_hasNullProfessor() {
            Module module = newModule(uniqueWord("Noprefmod"));
            repository.addModule(module.name(), module.moduleID());
            Exam exam = newExam(module.moduleID(), 2025, Semester.WINTER, ExamStatus.ACCEPTED);
            repository.addExam(exam);
            insertDownloads(exam.examID(), 50);

            ProfessorExamDTO dto = repository.getTopDownloadedExams(100).stream()
                    .filter(d -> d.exam().examID().equals(exam.examID()))
                    .findFirst()
                    .orElseThrow();

            assertNull(dto.professor());
        }
    }

    @Nested
    class UserExams {

        @Test
        void addUserExam_and_getAllUserExams_returnsOnlyTheUsersExamsNewestFirst() throws InterruptedException {
            User user = newUser(UUID.randomUUID().toString());
            repository.addOrUpdateUser(user);
            User otherUser = newUser(UUID.randomUUID().toString());
            repository.addOrUpdateUser(otherUser);

            repository.addUserExam(UUID.randomUUID().toString(), otherUser, 5, 5, "other-file");
            String firstId = UUID.randomUUID().toString();
            repository.addUserExam(firstId, user, 1, 2, "first-file");
            Thread.sleep(11);
            String secondId = UUID.randomUUID().toString();
            repository.addUserExam(secondId, user, 3, 4, "second-file");

            List<UserExam> userExams = repository.getAllUserExams(user);

            assertEquals(List.of(secondId, firstId), userExams.stream().map(UserExam::id).toList());
            UserExam second = userExams.get(0);
            assertEquals(user.id(), second.userID());
            assertEquals("second-file", second.fileID());
            assertNotNull(second.creationDate());
        }

        @Test
        void getAllUserExams_unknownUser_returnsEmptyList() {
            assertTrue(repository.getAllUserExams(newUser(UUID.randomUUID().toString())).isEmpty());
        }

        @Test
        void getAllUserExams_nullUser_returnsEmptyList() {
            assertTrue(repository.getAllUserExams(null).isEmpty());
        }

        @Test
        void isUserExamOwner_trueForOwner_falseForOtherUsersAndUnknownIds() {
            User user = newUser(UUID.randomUUID().toString());
            repository.addOrUpdateUser(user);
            User otherUser = newUser(UUID.randomUUID().toString());
            repository.addOrUpdateUser(otherUser);
            String userExamId = UUID.randomUUID().toString();
            repository.addUserExam(userExamId, user, 1, 1, "file");

            assertTrue(repository.isUserExamOwner(user, userExamId));
            assertFalse(repository.isUserExamOwner(otherUser, userExamId));
            assertFalse(repository.isUserExamOwner(user, UUID.randomUUID().toString()));
        }

        @Test
        void isUserExamOwner_nullArguments_returnFalse() {
            User user = newUser(UUID.randomUUID().toString());
            assertFalse(repository.isUserExamOwner(null, "some-id"));
            assertFalse(repository.isUserExamOwner(user, null));
        }

        @Test
        void deleteUserExam_removesTheUserExam() {
            User user = newUser(UUID.randomUUID().toString());
            repository.addOrUpdateUser(user);
            String userExamId = UUID.randomUUID().toString();
            repository.addUserExam(userExamId, user, 1, 1, "file");

            repository.deleteUserExam(userExamId);

            assertFalse(repository.getAllUserExams(user).stream()
                    .anyMatch(e -> e.id().equals(userExamId)));
        }

        @Test
        void deleteUserExam_nullOrBlankId_isNoOp() {
            assertDoesNotThrow(() -> repository.deleteUserExam(null));
            assertDoesNotThrow(() -> repository.deleteUserExam("   "));
        }

        @Test
        void countUserExams_countByAgeIncludingTokens() {
            User user = newUser(UUID.randomUUID().toString());
            repository.addOrUpdateUser(user);

            int weeklyBefore = repository.countWeeklyUserExmas();
            int semesterBefore = repository.countSemesterUserExams();
            int weeklyTokensBefore = repository.countWeeklyUserExmasToken();
            int semesterTokensBefore = repository.countSemesterUserExamsToken();

            insertUserExamAt(UUID.randomUUID().toString(), user.id(), now().minus(91, ChronoUnit.DAYS), // 3 months
                    10, 20, "old-file");
            repository.addUserExam(UUID.randomUUID().toString(), user, 1, 2, "new-file");

            assertEquals(weeklyBefore + 1, repository.countWeeklyUserExmas());
            assertEquals(semesterBefore + 2, repository.countSemesterUserExams());
            assertEquals(weeklyTokensBefore + 3, repository.countWeeklyUserExmasToken());
            assertEquals(semesterTokensBefore + 33, repository.countSemesterUserExamsToken());
        }
    }

    @Nested
    class ApiKeys {

        @Test
        void setAndGetApiKey_roundTrip() {
            User user = newUser(UUID.randomUUID().toString());
            repository.addOrUpdateUser(user);
            String apiKey = "key-" + UUID.randomUUID();

            repository.setApiKeyForUser(user, apiKey);

            assertEquals(apiKey, repository.getApiKeyForUser(user));
        }

        @Test
        void setApiKey_overwritesThePreviousKey() {
            User user = newUser(UUID.randomUUID().toString());
            repository.addOrUpdateUser(user);
            repository.setApiKeyForUser(user, "old-key");
            repository.setApiKeyForUser(user, "new-key");

            assertEquals("new-key", repository.getApiKeyForUser(user));
        }

        @Test
        void deleteApiKey_removesTheKey() {
            User user = newUser(UUID.randomUUID().toString());
            repository.addOrUpdateUser(user);
            repository.setApiKeyForUser(user, "some-key");

            repository.deleteApiKeyForUser(user);

            assertNull(repository.getApiKeyForUser(user));
        }

        @Test
        void getApiKey_unknownUser_returnsNull() {
            assertNull(repository.getApiKeyForUser(newUser(UUID.randomUUID().toString())));
        }

        @Test
        void setApiKey_unknownUser_isSilentlyIgnored() {
            User unknownUser = newUser(UUID.randomUUID().toString());

            assertDoesNotThrow(() -> repository.setApiKeyForUser(unknownUser, "some-key"));
            assertNull(repository.getApiKeyForUser(unknownUser));
        }
    }

    // ------------------------------------------------------------------
    // Admin account cleanup (2-year retention)
    // ------------------------------------------------------------------

    @Nested
    class AdminCleanup {

        private User oldUser(Instant lastLogin) {
            return new User(UUID.randomUUID().toString(), "Old", "User", lastLogin,
                    now().minus(4 * 365, ChronoUnit.DAYS), "old@example.com", Role.USER);
        }

        @Test
        void getAccountsToDelete_returnsOnlyUsersInactiveForTwoYears() {
            User inactive = oldUser(now().minus(3 * 365, ChronoUnit.DAYS));
            User active = oldUser(now().minus(548, ChronoUnit.DAYS)); // 18 months
            repository.addOrUpdateUser(inactive);
            repository.addOrUpdateUser(active);

            List<User> toDelete = repository.getAccountsToDelete();

            assertTrue(toDelete.stream().anyMatch(u -> u.id().equals(inactive.id())));
            assertFalse(toDelete.stream().anyMatch(u -> u.id().equals(active.id())));
        }

        @Test
        void deleteOldAccounts_removesInactiveUser() {
            User inactive = oldUser(now().minus(3 * 365, ChronoUnit.DAYS));
            repository.addOrUpdateUser(inactive);

            repository.deleteOldAccounts(List.of(inactive.id()));

            assertNull(repository.getUser(inactive.id()));
        }

        @Test
        void deleteOldAccounts_userWithUserExams_isNotDeleted() {
            User withUserExam = oldUser(now().minus(3 * 365, ChronoUnit.DAYS));
            User withoutUserExam = oldUser(now().minus(3 * 365, ChronoUnit.DAYS));
            repository.addOrUpdateUser(withUserExam);
            repository.addOrUpdateUser(withoutUserExam);
            repository.addUserExam(UUID.randomUUID().toString(), withUserExam, 1, 1, "file");

            repository.deleteOldAccounts(List.of(withUserExam.id(), withoutUserExam.id()));

            // The foreign key violation aborts the whole statement, so no account is deleted.
            assertNotNull(repository.getUser(withUserExam.id()));
            assertNotNull(repository.getUser(withoutUserExam.id()));
        }

        @Test
        void deleteOldAccounts_succeedsAfterUserExamsAreRemoved() {
            User user = oldUser(now().minus(3 * 365, ChronoUnit.DAYS));
            repository.addOrUpdateUser(user);
            String userExamId = UUID.randomUUID().toString();
            repository.addUserExam(userExamId, user, 1, 1, "file");

            repository.deleteUserExam(userExamId);
            repository.deleteOldAccounts(List.of(user.id()));

            assertNull(repository.getUser(user.id()));
        }
    }
}
