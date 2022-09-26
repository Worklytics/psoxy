package co.worklytics.psoxy.gateway.impl.oauth;

import com.google.auth.oauth2.AccessToken;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.Date;
import java.util.Optional;

/**
 * Custom class that maps 1:1 with {@link com.google.auth.oauth2.AccessToken}
 * Used for storage of the token in JSON. Google's is immutable and can't easily configure
 * Jackson to do it.
 */
@NoArgsConstructor //for jackson
@AllArgsConstructor
@Getter
public class AccessTokenDto {
    private String token;
    private Long expirationDate;

    public AccessToken asAccessToken() {
        return new AccessToken(token, Optional.ofNullable(expirationDate).map(Date::new).orElse(null));
    }

    public static AccessTokenDto toAccessTokenDto(AccessToken accessToken) {
        return new AccessTokenDto(accessToken.getTokenValue(), Optional.ofNullable(accessToken.getExpirationTime()).map(Date::getTime).orElse(null));
    }
}
