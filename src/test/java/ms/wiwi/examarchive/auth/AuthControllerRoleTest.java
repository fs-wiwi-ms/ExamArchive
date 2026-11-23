package ms.wiwi.examarchive.auth;

import ms.wiwi.examarchive.model.Role;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Pure unit tests for the role-decision helpers extracted from {@link AuthController#callback}.
 * These lock down the authorization boundary: which Keycloak affiliation claims grant which role,
 * and when a council member (university email domain) is forced to use their university account.
 */
class AuthControllerRoleTest {

    private static final String USER_AFFILIATION = "student";
    private static final String ADMIN_AFFILIATION = "admin";
    private static final String ADMIN_DOMAIN = "@uni-muenster.de";

    @Test
    void decideRole_adminAffiliation_returnsAdmin() {
        assertEquals(Role.ADMIN, AuthController.decideRole(List.of("admin"), USER_AFFILIATION, ADMIN_AFFILIATION));
    }

    @Test
    void decideRole_studentAffiliation_returnsUser() {
        assertEquals(Role.USER, AuthController.decideRole(List.of("student"), USER_AFFILIATION, ADMIN_AFFILIATION));
    }

    @Test
    void decideRole_adminWinsOverStudent() {
        assertEquals(Role.ADMIN, AuthController.decideRole(List.of("student", "admin"), USER_AFFILIATION, ADMIN_AFFILIATION));
    }

    @Test
    void decideRole_emptyAffiliation_returnsBlocked() {
        assertEquals(Role.BLOCKED, AuthController.decideRole(List.of(), USER_AFFILIATION, ADMIN_AFFILIATION));
    }

    @Test
    void decideRole_nullAffiliation_returnsBlocked() {
        assertEquals(Role.BLOCKED, AuthController.decideRole(null, USER_AFFILIATION, ADMIN_AFFILIATION));
    }

    @Test
    void decideRole_unrecognizedAffiliation_returnsBlocked() {
        assertEquals(Role.BLOCKED, AuthController.decideRole(List.of("invalid"), USER_AFFILIATION, ADMIN_AFFILIATION));
    }

    @Test
    void requiresUniversityAccount_councilDomainAsUser_returnsTrue() {
        assertTrue(AuthController.requiresUniversityAccount("alice@uni-muenster.de", ADMIN_DOMAIN, Role.USER));
    }

    @Test
    void requiresUniversityAccount_councilDomainAsBlocked_returnsTrue() {
        assertTrue(AuthController.requiresUniversityAccount("alice@uni-muenster.de", ADMIN_DOMAIN, Role.BLOCKED));
    }

    @Test
    void requiresUniversityAccount_councilDomainAsAdmin_returnsFalse() {
        assertFalse(AuthController.requiresUniversityAccount("admin@uni-muenster.de", ADMIN_DOMAIN, Role.ADMIN));
    }

    @Test
    void requiresUniversityAccount_otherDomain_returnsFalse() {
        assertFalse(AuthController.requiresUniversityAccount("alice@gmail.com", ADMIN_DOMAIN, Role.USER));
    }

    @Test
    void requiresUniversityAccount_nullEmail_returnsFalse() {
        assertFalse(AuthController.requiresUniversityAccount(null, ADMIN_DOMAIN, Role.USER));
    }
}
