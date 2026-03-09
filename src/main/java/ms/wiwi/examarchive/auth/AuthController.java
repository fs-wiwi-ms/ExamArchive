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

    public AuthController(OIDCService oidcService, Repository userRepository) {
        this.oidcService = oidcService;
        this.repository = userRepository;
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
            JWTClaimsSet claims = oidcService.exchangeCode(code, verifier, expectedNonce);

            String eppn = claims.getStringClaim("eppn");
            String firstname = claims.getStringClaim("given_name");
            String lastname = claims.getStringClaim("family_name");
            String email = claims.getStringClaim("email");
            List<String> affiliation = claims.getStringListClaim("affiliation");
            Role role = Role.BLOCKED;
            if(affiliation != null && affiliation.contains("student@uni-muenster.de")){
                role = Role.USER;
            }
            User user = new User(eppn, firstname, lastname, Instant.now(), Instant.now(), email, role);
            logger.info("User {} logged in", user);
            repository.addOrUpdateUser(user);
            ctx.req().changeSessionId();
            ctx.sessionAttribute("userId", user.id());
            ctx.redirect("/exams/search");

        } catch (Exception e) {
            logger.error("Authentication failed during OIDC callback", e);
            ctx.status(500).result("Authentication failed");
        }
    }

    /**
     * Clears the user session.
     */
    public void logout(Context ctx) {
        ctx.req().getSession().invalidate();
        ctx.redirect("/");
    }
}
