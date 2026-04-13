package ms.wiwi.examarchive.auth;

import com.nimbusds.jwt.JWTClaimsSet;

public record AuthResult(JWTClaimsSet claims, String idToken) {
}
