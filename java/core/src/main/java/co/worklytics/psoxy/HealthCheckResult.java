package co.worklytics.psoxy;

import co.worklytics.psoxy.gateway.ProxyConfigProperty;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import lombok.*;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

@JsonPropertyOrder(alphabetic = true) //for consistent json format across jdks
@Data
@Builder
@NoArgsConstructor //for jackson
@AllArgsConstructor
public class HealthCheckResult {

    String javaSourceCodeVersion;


    //q: terraform module version?? (eg, have terraform deployment set its version number as ENV
    // variable, and then psoxy can read it and report it here)

    public String getVersion() {
        if (bundleFilename == null) {
            return javaSourceCodeVersion;
        } else {
            return Pattern.compile("psoxy-[^-]*-(.*).jar").matcher(bundleFilename)
                .replaceAll("$1");
        }
    }

    public void setVersion(String version) {
        //no-op, for jackson
    }

    String bundleFilename;

    String configuredSource;

    String configuredHost;

    /**
     * from config, if any. (if null, the computed logically)
     */
    String pseudonymImplementation;

    String sourceAuthStrategy;

    String sourceAuthGrantType;

    Boolean nonDefaultSalt;

    Set<String> missingConfigProperties;

    Map<String, Instant> configPropertiesLastModified;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    String rules;

    /**
     * value of PSEUDONYMIZE_APP_IDS config property, if any
     * @see ProxyConfigProperty#PSEUDONYMIZE_APP_IDS
     */
    Boolean pseudonymizeAppIds;

    String callerIp;

    public boolean passed() {
        return getConfiguredSource() != null
            && getNonDefaultSalt()
            && getMissingConfigProperties().isEmpty();
    }

    //unknownProperties + any setter, so robust across versions (eg, proxy server has something that proxy client lacks)
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    @Builder.Default
    Map<String, Object> unknownProperties = new HashMap<>();

    @JsonAnySetter
    public void setUnknownProperty(String key, Object value) {
        unknownProperties.put(key, value);
    }
}
