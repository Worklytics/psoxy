package co.worklytics.psoxy.gateway.auth;

import com.nimbusds.jwt.SignedJWT;
import lombok.SneakyThrows;

import java.security.interfaces.RSAPublicKey;
import java.util.Collection;
import java.util.Optional;

public interface JwtAuthorizedResource {

    /**
     * @return  a collection of acceptable RSA public keys for JWT authentication.
     */
    Collection<RSAPublicKey> acceptableAuthKeys();

    /**
     * returns a failure message if invalid, or empty otherwise
     * @param signedJWT
     * @return optional with the failure, if any
     */
    @SneakyThrows
    Optional<String> validate(SignedJWT signedJWT);
}
