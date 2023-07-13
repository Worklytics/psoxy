package co.worklytics.psoxy.gateway.impl.oauth;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.google.api.client.http.HttpResponse;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

public class GithubRefreshTokenHandlerImpl extends OAuthRefreshTokenSourceAuthStrategy.TokenRefreshHandlerImpl {

    @Override
    protected CanonicalOAuthAccessTokenResponseDto parseTokenResponse(HttpResponse response) throws IOException {
        AccessTokenResponseDto accessTokenResponseDto = objectMapper.readerFor(AccessTokenResponseDto.class)
                .readValue(response.getContent());
        CanonicalOAuthAccessTokenResponseDto dto = new CanonicalOAuthAccessTokenResponseDto();
        dto.accessToken = accessTokenResponseDto.getToken();
        dto.expiresIn = (int) Duration.between(accessTokenResponseDto.getExpiresAt(), clock.instant()).getSeconds();

        return dto;
    }

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    @JsonPropertyOrder(alphabetic = true) //for consistent tests
    @NoArgsConstructor //for jackson
    @Getter
    public static class AccessTokenResponseDto {

        String token;
        List<String> scopes;
        Instant expiresAt;
    }

}