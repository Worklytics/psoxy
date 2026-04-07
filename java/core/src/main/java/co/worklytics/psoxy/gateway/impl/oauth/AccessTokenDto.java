package co.worklytics.psoxy.gateway.impl.oauth;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.auth.oauth2.AccessToken;
import lombok.Value;

import java.util.Date;
import java.util.Optional;

/**
 * Custom class that maps 1:1 with {@link com.google.auth.oauth2.AccessToken}
 * Used for storage of the token in JSON. Google's is immutable and can't easily configure
 * Jackson to do it.
 */
@Value
public class AccessTokenDto {
    @JsonCreator
    public AccessTokenDto(@JsonProperty("token") String token, @JsonProperty("expirationDate") Long expirationDate) {
        this.token = token;
        this.expirationDate = expirationDate;
    }
    String token;
    Long expirationDate;

    public AccessToken asAccessToken() {
        return new AccessToken(token, Optional.ofNullable(expirationDate).map(Date::new).orElse(null));
    }

    public static AccessTokenDto toAccessTokenDto(AccessToken accessToken) {
        return new AccessTokenDto(accessToken.getTokenValue(), Optional.ofNullable(accessToken.getExpirationTime()).map(Date::getTime).orElse(null));
    }
}
