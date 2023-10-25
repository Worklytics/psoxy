package co.worklytics.psoxy;

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

    @Builder.Default
    String version = "rc-v0.4.41";

    //q: terraform module version?? (eg, have terraform deployment set its version number as ENV
    // variable, and then psoxy can read it and report it here)

    public String getVersion() {
        if (bundleFilename == null) {
            return version;
        } else {
            return Pattern.compile("psoxy-[^-]*-(.*).jar").matcher(bundleFilename)
                .replaceAll("$1");
        }
    }

    /**
     * @return the value that's directly in the java source code; in practice, this should match
     * the value parsed from the bundle filename; but some people build JAR themselves, and seem to
     * use different file names OR keep setting `BUNDLE_FILENAME` arbitrarily to some example value
     */
    public String getJavaSourceVersion() {
        return this.version;
    }

    public void setJavaSourceVersion(String version) {
        //no-op, in case trying to parse JSON from old deployment version
    }

    public void setVersion(String version) {
        //no-op, in case trying to parse JSON from old deployment version
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
