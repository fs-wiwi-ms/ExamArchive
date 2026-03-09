package ms.wiwi.examarchive.auth;

import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.oauth2.sdk.*;
import com.nimbusds.oauth2.sdk.auth.ClientAuthentication;
import com.nimbusds.oauth2.sdk.auth.ClientSecretBasic;
import com.nimbusds.oauth2.sdk.auth.Secret;
import com.nimbusds.oauth2.sdk.id.ClientID;
import com.nimbusds.oauth2.sdk.id.Issuer;
import com.nimbusds.oauth2.sdk.id.State;
import com.nimbusds.oauth2.sdk.pkce.CodeChallengeMethod;
import com.nimbusds.oauth2.sdk.pkce.CodeVerifier;
import com.nimbusds.openid.connect.sdk.*;
import com.nimbusds.openid.connect.sdk.op.OIDCProviderMetadata;

import java.net.URI;

/**
 * Service for handling OIDC flows with Keycloak using the Nimbus SDK, including PKCE.
 */
public class OIDCService {

    private final OIDCProviderMetadata providerMetadata;
    private final ClientID clientID;
    private final Secret clientSecret;
    private final URI redirectUri;

    public OIDCService(String issuerUrl, String clientId, String clientSecret, String redirectUri) throws Exception {
        this.providerMetadata = OIDCProviderMetadata.resolve(new Issuer(issuerUrl));
        this.clientID = new ClientID(clientId);
        this.clientSecret = new Secret(clientSecret);
        this.redirectUri = new URI(redirectUri);
    }

    /**
     * Builds the authorization URL with PKCE parameters and an Identity Provider hint.
     */
    public String getAuthUrl(State state, Nonce nonce, String idpHint, CodeVerifier codeVerifier) {
        AuthenticationRequest.Builder builder = new AuthenticationRequest.Builder(
                new ResponseType(ResponseType.Value.CODE),
                new Scope(OIDCScopeValue.OPENID, OIDCScopeValue.PROFILE, OIDCScopeValue.EMAIL),
                clientID,
                redirectUri
        )
                .endpointURI(providerMetadata.getAuthorizationEndpointURI())
                .state(state)
                .nonce(nonce)
                .codeChallenge(codeVerifier, CodeChallengeMethod.S256);
        if (idpHint != null && !idpHint.isBlank()) {
            builder.customParameter("kc_idp_hint", idpHint);
        }

        return builder.build().toURI().toString();
    }

    /**
     * Exchanges the authorization code for tokens using the PKCE code verifier.
     */
    public JWTClaimsSet exchangeCode(String codeParam, CodeVerifier codeVerifier) throws Exception {
        AuthorizationCode code = new AuthorizationCode(codeParam);
        AuthorizationGrant codeGrant = new AuthorizationCodeGrant(code, redirectUri, codeVerifier);
        ClientAuthentication clientAuth = new ClientSecretBasic(clientID, clientSecret);
        TokenRequest request = new TokenRequest.Builder(
                providerMetadata.getTokenEndpointURI(),
                clientAuth,
                codeGrant
        ).build();
        TokenResponse response = OIDCTokenResponseParser.parse(request.toHTTPRequest().send());
        if (!response.indicatesSuccess()) {
            throw new RuntimeException(response.toErrorResponse().getErrorObject().getDescription());
        }
        OIDCTokenResponse successResponse = (OIDCTokenResponse) response.toSuccessResponse();
        return successResponse.getOIDCTokens().getIDToken().getJWTClaimsSet();
    }
}