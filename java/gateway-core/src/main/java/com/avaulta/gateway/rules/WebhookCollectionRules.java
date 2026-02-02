package com.avaulta.gateway.rules;

import java.io.Serial;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import com.avaulta.gateway.rules.transforms.Transform;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.Singular;
import lombok.With;

@Builder
@JsonPropertyOrder({"jwtClaimsToVerify", "endpoints"})  //deterministic serialization order
@AllArgsConstructor //for builder
@NoArgsConstructor //for Jackson
@Data
public class WebhookCollectionRules implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * Map of JWT claims to verify for all endpoints.
     *
     * all claims must match or 401 Unauthorized is returned.
     *
     * eg, if set, must be Authorization header including a JWT. any claims specified here MUST be present in the JWT
     * and it's value MUST match the request according to the spec
     */
    @JsonInclude(value = JsonInclude.Include.NON_EMPTY)
    Map<String, JwtClaimSpec> jwtClaimsToVerify;

    @Singular
    List<WebhookEndpoint> endpoints;


    @JsonPropertyOrder(alphabetic = true)
    @Builder(toBuilder = true)
    @With
    @AllArgsConstructor //for builder

    @Getter
    public static class WebhookEndpoint implements Serializable {

        @Serial
        private static final long serialVersionUID = 1L;


        // omit for now; expect SINGLE endpoint per collector
//        /**
//         * if provided, path of incoming request must match this path template.
//         *
//         *
//         */
//        @JsonInclude(value = JsonInclude.Include.NON_NULL)
//        String pathTemplate;

        /**
         * map of claim field --> verification spec
         *
         * all claims must match or 401 Unauthorized is returned.
         */
        @JsonInclude(value = JsonInclude.Include.NON_EMPTY)
        Map<String, JwtClaimSpec> jwtClaimsToVerify;

        //TODO: schemaFilter for request payload?  avoids risk of unexpected fields included in request payload

        /**
         * a list of transforms to apply to the request payload
         */
        @Setter
        @JsonInclude(value = JsonInclude.Include.NON_EMPTY)
        @Singular
        List<Transform> transforms;

        /**
         * No-args constructor.
         * 1) Needed for Jackson deserialization.
         * 2) Explicit instantiation of @Singular fields required to avoid Lombok warnings about ignored default values.
         */
        public WebhookEndpoint() {
            this.transforms = new ArrayList<>();
        }
    }

    @Data
    @Builder(toBuilder = true)
    @AllArgsConstructor
    @NoArgsConstructor
    public static class JwtClaimSpec implements Serializable {

        @Serial
        private static final long serialVersionUID = 1L;

        // no immediate use for this
//        /**
//         * if present, value for the path parameter MUST match the claim value in the JWT
//         */
//        @JsonInclude(JsonInclude.Include.NON_NULL)
//        String pathParam;

        /**
         * claim value must be equivalent to value of the query parameter, if provided
         */
        @JsonInclude(JsonInclude.Include.NON_NULL)
        String queryParam;

        /**
         * a list of JSON paths to values in the request payload. The value at each path location, if present, MUST match the claim value.
         *
         * q: do we need OPTIONAL vs REQUIRED?
         *   - eg, values that if they are present in request payload, must match the claim value VS values that MUST be present and match.
         *
         */
        @Singular
        @JsonInclude(JsonInclude.Include.NON_EMPTY)
        List<String> payloadContents;
    }
}
