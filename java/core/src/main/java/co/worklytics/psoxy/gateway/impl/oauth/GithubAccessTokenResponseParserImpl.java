package co.worklytics.psoxy.gateway.impl.oauth;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
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
        dto.expiresIn = (int) Duration.between(Instant.parse(accessTokenResponseDto.getExpiresAt()), clock.instant()).getSeconds();
        // From https://docs.github.com/en/enterprise-server@3.8/apps/creating-github-apps/authenticating-with-a-github-app/generating-an-installation-access-token-for-a-github-app#generating-an-installation-access-token
        // "Note: In most cases, you can use Authorization: Bearer or Authorization: token to pass a token. However, if you are passing a JSON web token (JWT), you must use Authorization: Bearer."
        dto.tokenType = "Bearer";

        return dto;
    }

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonPropertyOrder(alphabetic = true) //for consistent tests
    @NoArgsConstructor //for jackson
    @Getter
    public static class AccessTokenResponseDto {

        String token;
        String expiresAt;
        // Other properties not included in serialization:
        // - permissions
        // - repository_selection
    }

}