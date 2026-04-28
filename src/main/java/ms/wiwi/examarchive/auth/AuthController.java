package ms.wiwi.examarchive.auth;

import com.nimbusds.oauth2.sdk.id.State;
import com.nimbusds.oauth2.sdk.pkce.CodeVerifier;
import com.nimbusds.openid.connect.sdk.Nonce;
import io.javalin.http.Context;
import ms.wiwi.examarchive.Repository;

import com.nimbusds.jwt.JWTClaimsSet;
import ms.wiwi.examarchive.model.Role;
import ms.wiwi.examarchive.model.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.List;

/**
 * Controller for handling authentication routes.
 */
public class AuthController {

    private final OIDCService oidcService;
    private final Repository repository;
    private final Logger logger = LoggerFactory.getLogger(AuthController.class);
    private final String userAffiliation;
    private final String adminAffiliation;
    private final String adminEmailDomain;

    public AuthController(OIDCService oidcService, Repository userRepository, String userAffiliation, String adminAffiliation, String adminEmailDomain) {
        this.oidcService = oidcService;
        this.repository = userRepository;
        this.userAffiliation = userAffiliation;
        this.adminAffiliation = adminAffiliation;
        this.adminEmailDomain = adminEmailDomain;
    }

    /**
     * Redirects the user to the Keycloak IdP login bypass URL.
     */
    public void login(Context ctx) {
        String type = ctx.pathParam("type");
        String idpHint = "admin".equalsIgnoreCase(type) ? "microsoft" : "unims";

        CodeVerifier codeVerifier = new CodeVerifier();
        State state = new State();
        Nonce nonce = new Nonce();
        ctx.sessionAttribute("oauth_state", state.getValue());
        ctx.sessionAttribute("pkce_verifier", codeVerifier.getValue());
        ctx.sessionAttribute("oauth_nonce", nonce.getValue());
        String authUrl = oidcService.getAuthUrl(state, nonce, idpHint, codeVerifier);
        if(ctx.header("HX-Request") != null){
            ctx.header("HX-Redirect", authUrl);
            ctx.status(401);
            return;
        }
        ctx.redirect(authUrl);
    }

    /**
     * Handles the OIDC callback from Keycloak, exchanges the code, and creates the session.
     */
    public void callback(Context ctx) {
        String state = ctx.queryParam("state");
        String code = ctx.queryParam("code");
        String storedNonceVal = ctx.sessionAttribute("oauth_nonce");
        String storedState = ctx.sessionAttribute("oauth_state");
        String verifierStr = ctx.sessionAttribute("pkce_verifier");
        ctx.sessionAttribute("oauth_state", null);
        ctx.sessionAttribute("pkce_verifier", null);
        ctx.sessionAttribute("oauth_nonce", null);

        if (verifierStr == null) {
            ctx.status(400).result("Missing PKCE Verifier in session");
            return;
        }

        if (storedNonceVal == null) {
            ctx.status(400).result("Missing Nonce");
            return;
        }

        if (state == null || !state.equals(storedState)) {
            ctx.status(400).result("Invalid State");
            return;
        }

        try {
            Nonce expectedNonce = new Nonce(storedNonceVal);
            CodeVerifier verifier = new CodeVerifier(verifierStr);
            AuthResult authResult = oidcService.exchangeCode(code, verifier, expectedNonce);
            JWTClaimsSet claims = authResult.claims();

            String eppn = claims.getStringClaim("eppn");
            String firstname = claims.getStringClaim("given_name");
            String lastname = claims.getStringClaim("family_name");
            String email = claims.getStringClaim("email");
            List<String> affiliation = claims.getStringListClaim("affiliation");

            boolean isStudent = affiliation != null && checkAffiliation(affiliation, userAffiliation);
            boolean isAdmin = affiliation != null && checkAffiliation(affiliation, adminAffiliation);

            Role role = Role.BLOCKED;
            if (isAdmin) {
                role = Role.ADMIN;
            } else if (isStudent) {
                role = Role.USER;
            }
            if (email != null && email.endsWith(adminEmailDomain) && role != Role.ADMIN) {
                ctx.status(403).result("Access denied. Please login with your university account.");
                logger.info("Council member {} tried to log in without admin rights", email);
                return;
            }
            User user = new User(eppn, firstname, lastname, Instant.now(), Instant.now(), email, role);
            logger.info("Evaluating login for User {}", user.email());
            user = repository.addOrUpdateUser(user);
            if (user.role() == Role.BLOCKED) {
                ctx.status(403).result("You are not allowed to log in.");
                logger.warn("User {} tried to log in but is permanently blocked", email);
                return;
            }
            ctx.req().changeSessionId();
            ctx.sessionAttribute("userId", user.id());
            ctx.sessionAttribute("user", user);
            ctx.sessionAttribute("idToken", authResult.idToken());
            if (user.role() == Role.ADMIN) {
                ctx.redirect("/admin/admin");
                return;
            }
            ctx.redirect("/exams/search");

        } catch (Exception e) {
            logger.error("Authentication failed during OIDC callback", e);
            ctx.status(500).result("Authentication failed");
        }
    }

    /**
     * Checks if a user has a certain affiliation.
     * @param affiliation Affiliation list
     * @param allowedAffiliation Affiliation to check for
     * @return True if the user has the affiliation, false otherwise
     */
    private boolean checkAffiliation(List<String> affiliation, String allowedAffiliation){
        for(String a : affiliation){
            if(a.equals(allowedAffiliation)){
                return true;
            }
        }
        return false;
    }

    /**
     * Initiates the logout process by invalidating the session and redirecting to the Keycloak logout URL.
     */
    public void logout(Context ctx) {
        String idToken = ctx.sessionAttribute("idToken");
        ctx.req().getSession().invalidate();
        if(idToken != null){
            String logoutUrl = oidcService.getLogoutUrl(idToken);
            ctx.redirect(logoutUrl);
            return;
        }
        ctx.redirect("/");
    }
}
