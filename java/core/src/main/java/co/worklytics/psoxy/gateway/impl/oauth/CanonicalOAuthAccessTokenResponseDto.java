package co.worklytics.psoxy.gateway.impl.oauth;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.Map;
import java.util.TreeMap;

/**
 * something like:
 * {
 * "access_token": "BWjcyMzY3ZDhiNmJkNTY",
 * "refresh_token": "Srq2NjM5NzA2OWJjuE7c",
 * "token_type": "bearer",
 * "expires_in": 3600
 * }
 */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
@JsonPropertyOrder(alphabetic = true) //for consistent tests
@NoArgsConstructor //for jackson
@Getter
public class CanonicalOAuthAccessTokenResponseDto {

    String accessToken;
    String refreshToken;
    String tokenType;
    //OAuth 2.0 https://datatracker.ietf.org/doc/html/rfc6749#section-4.4.3
    Integer expiresIn;

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    @Getter(onMethod_ = {@JsonAnyGetter})
    //magically put 'unmapped' properties back onto JSON when serialized
    private Map<String, Object> unmapped = new TreeMap<>();  //treemap, to try to make order deterministic

    @JsonAnySetter
    public void set(String name, Object value) {
        unmapped.put(name, value);
    }
}
