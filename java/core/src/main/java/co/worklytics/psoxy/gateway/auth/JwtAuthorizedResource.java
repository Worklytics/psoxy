package co.worklytics.psoxy.gateway.auth;

import com.nimbusds.jwt.SignedJWT;
import co.worklytics.psoxy.gateway.auth.PublicKeyStoreClient.PublicKeyVersionId;
import java.security.interfaces.RSAPublicKey;
import java.util.Map;
import java.util.Optional;

public interface JwtAuthorizedResource {

    /**
     * @return a map of key ids to RSA public keys accepted for JWT authentication.
     */
    Map<PublicKeyVersionId, RSAPublicKey> acceptableAuthKeys();

    /**
     * returns a failure message if invalid, or empty otherwise
     * 
     * @param signedJWT
     * @return optional with the failure, if any
     */
    Optional<String> validate(SignedJWT signedJWT);

    /**
     * @return the issuer of the JWTs that this resource accepts, without trailing /
     */
    String getIssuer();
}
