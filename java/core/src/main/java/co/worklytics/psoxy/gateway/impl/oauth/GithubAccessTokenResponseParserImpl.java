package co.worklytics.psoxy.gateway.impl.oauth;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.google.api.client.http.HttpResponse;
import lombok.Getter;
import lombok.NoArgsConstructor;

import javax.inject.Inject;
import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

@NoArgsConstructor(onConstructor_ = @Inject)
public class GithubAccessTokenResponseParserImpl implements OAuthRefreshTokenSourceAuthStrategy.TokenResponseParser {

    @Inject
    ObjectMapper objectMapper;
    @Inject
    Clock clock;

    @Override
    public String getName() {
        return "GIT_HUB_ACCESS_TOKEN";
    }

    @Override
    public CanonicalOAuthAccessTokenResponseDto parseTokenResponse(HttpResponse response) throws IOException {
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