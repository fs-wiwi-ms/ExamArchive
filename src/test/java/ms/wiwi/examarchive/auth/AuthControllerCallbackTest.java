package ms.wiwi.examarchive.auth;

import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.oauth2.sdk.pkce.CodeVerifier;
import com.nimbusds.openid.connect.sdk.Nonce;
import io.javalin.http.Context;
import jakarta.servlet.http.HttpServletRequest;
import ms.wiwi.examarchive.Repository;
import ms.wiwi.examarchive.model.Role;
import ms.wiwi.examarchive.model.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Answers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for the OIDC callback flow in {@link AuthController}, using a mocked Javalin
 * {@link Context} and mocked collaborators (no Keycloak / DB / S3). These cover the session/state
 * validation branches and the role-based redirect/denial decisions.
 *
 * <p>{@code callback()} reads the PKCE verifier, nonce, state and the OIDC query params
 * unconditionally before any branching, so every test stubs all five reads.
 */
@ExtendWith(MockitoExtension.class)
class AuthControllerCallbackTest {

    private static final String USER_AFFILIATION = "student";
    private static final String ADMIN_AFFILIATION = "admin";
    private static final String ADMIN_DOMAIN = "@uni-muenster.de";
    private static final String VALID_VERIFIER = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789-._~";
    private static final String EXPECTED_STATE = "expected-state";

    @Mock(answer = Answers.RETURNS_SELF)
    private Context ctx;
    @Mock
    private OIDCService oidcService;
    @Mock
    private Repository repository;
    @Mock
    private HttpServletRequest req;

    private AuthController controller;

    @BeforeEach
    void setUp() {
        controller = new AuthController(oidcService, repository, USER_AFFILIATION, ADMIN_AFFILIATION, ADMIN_DOMAIN);
    }

    private static AuthResult authResult(String email, List<String> affiliation) {
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .claim("eppn", "eppn-123")
                .claim("given_name", "John")
                .claim("family_name", "Doe")
                .claim("email", email)
                .claim("affiliation", affiliation)
                .build();
        return new AuthResult(claims, "id-token");
    }

    private static User userWithRole(Role role, String email) {
        return new User("eppn-123", "John", "Doe", Instant.now(), Instant.now(), email, role);
    }

    /**
     * Stubs every value {@code callback()} reads up front (session attributes + OIDC query params),
     * because they are all read before the validation branches.
     */
    private void stubOidcReads(String verifier, String nonce, String storedState, String state, String code) {
        when(ctx.sessionAttribute("pkce_verifier")).thenReturn(verifier);
        when(ctx.sessionAttribute("oauth_nonce")).thenReturn(nonce);
        when(ctx.sessionAttribute("oauth_state")).thenReturn(storedState);
        when(ctx.queryParam("state")).thenReturn(state);
        when(ctx.queryParam("code")).thenReturn(code);
    }

    private void stubOidcReadsHappy() {
        stubOidcReads(VALID_VERIFIER, "nonce-123", EXPECTED_STATE, EXPECTED_STATE, "code-123");
    }

    @Test
    void callback_missingVerifier_returns400() {
        stubOidcReads(null, "nonce-123", EXPECTED_STATE, EXPECTED_STATE, "code-123");
        controller.callback(ctx);
        verify(ctx).status(400);
        verify(ctx).result("Missing PKCE Verifier in session");
        verifyNoInteractions(oidcService, repository);
    }

    @Test
    void callback_missingNonce_returns400() {
        stubOidcReads(VALID_VERIFIER, null, EXPECTED_STATE, EXPECTED_STATE, "code-123");
        controller.callback(ctx);

        verify(ctx).status(400);
        verify(ctx).result("Missing Nonce");
        verifyNoInteractions(oidcService, repository);
    }

    @Test
    void callback_mismatchedState_returns400() {
        stubOidcReads(VALID_VERIFIER, "nonce-123", EXPECTED_STATE, "different-state", "code-123");

        controller.callback(ctx);

        verify(ctx).status(400);
        verify(ctx).result("Invalid State");
        verifyNoInteractions(oidcService, repository);
    }

    @Test
    void callback_nullState_returns400() {
        stubOidcReads(VALID_VERIFIER, "nonce-123", EXPECTED_STATE, null, "code-123");

        controller.callback(ctx);

        verify(ctx).status(400);
        verify(ctx).result("Invalid State");
        verifyNoInteractions(oidcService, repository);
    }

    @Test
    void callback_adminUser_redirectsToAdminPanel() throws Exception {
        stubOidcReadsHappy();
        when(oidcService.exchangeCode(eq("code-123"), any(CodeVerifier.class), any(Nonce.class)))
                .thenReturn(authResult("admin@uni-muenster.de", List.of("admin")));
        when(repository.addOrUpdateUser(any(User.class))).thenReturn(userWithRole(Role.ADMIN, "admin@uni-muenster.de"));
        when(ctx.req()).thenReturn(req);

        controller.callback(ctx);

        verify(ctx).redirect("/admin/admin");
        verify(ctx, never()).redirect("/exams/search");
    }

    @Test
    void callback_regularUser_redirectsToExamSearch() throws Exception {
        stubOidcReadsHappy();
        when(oidcService.exchangeCode(eq("code-123"), any(CodeVerifier.class), any(Nonce.class)))
                .thenReturn(authResult("student@gmail.com", List.of("student")));
        when(repository.addOrUpdateUser(any(User.class))).thenReturn(userWithRole(Role.USER, "student@gmail.com"));
        when(ctx.req()).thenReturn(req);

        controller.callback(ctx);

        verify(ctx).redirect("/exams/search");
        verify(ctx, never()).redirect("/admin/admin");
    }

    @Test
    void callback_councilMemberWithoutAdminRights_returns403() throws Exception {
        stubOidcReadsHappy();
        when(oidcService.exchangeCode(eq("code-123"), any(CodeVerifier.class), any(Nonce.class)))
                .thenReturn(authResult("alice@uni-muenster.de", List.of("student")));

        controller.callback(ctx);

        verify(ctx).status(403);
        verify(ctx).result("Access denied. Please login with your university account.");
        verifyNoInteractions(repository);
        verify(ctx, never()).redirect(anyString());
    }

    @Test
    void callback_blockedUser_returns403() throws Exception {
        stubOidcReadsHappy();
        when(oidcService.exchangeCode(eq("code-123"), any(CodeVerifier.class), any(Nonce.class)))
                .thenReturn(authResult("student@gmail.com", List.of()));
        when(repository.addOrUpdateUser(any(User.class))).thenReturn(userWithRole(Role.BLOCKED, "student@gmail.com"));

        controller.callback(ctx);

        verify(ctx).status(403);
        verify(ctx).result("You are not allowed to log in.");
        verify(ctx, never()).redirect(anyString());
    }

    @Test
    void callback_exchangeFailure_returns500() throws Exception {
        stubOidcReadsHappy();
        when(oidcService.exchangeCode(eq("code-123"), any(CodeVerifier.class), any(Nonce.class)))
                .thenThrow(new RuntimeException("token exchange failed"));

        controller.callback(ctx);

        verify(ctx).status(500);
        verify(ctx).result("Authentication failed");
        verifyNoInteractions(repository);
    }
}
