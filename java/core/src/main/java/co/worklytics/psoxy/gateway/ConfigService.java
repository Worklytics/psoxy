package co.worklytics.psoxy.gateway;

import java.io.Serializable;
import java.time.Instant;
import java.util.Optional;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.Value;

public interface ConfigService {

    interface ConfigProperty extends Serializable {

        String name();



        // sensitive? (eg, value should not be exposed to 3rd parties)
        // secret? (eg, value should be handled as a secret; obscured/acl even internally; avoid in logs, etc)

        /**
         * @return whether cached value for property must be revalidated with origin before re-use
         *  (equivalent to HTTP `Cache-Control: no-cache` semantics as defined by RFC 7234)
         *
         * @see 'https://www.rfc-editor.org/rfc/rfc7234#section-5.2.2'
         */
        default Boolean noCache() {
            return false;
        }

        /**
         * @return whether this property is limited to being set via environment variables only
         */
        default boolean isEnvVarOnly() {
            return getSupportedSource() == SupportedSource.ENV_VAR;
        }

        default SupportedSource getSupportedSource() {
            return SupportedSource.ENV_VAR_OR_REMOTE;
        }

        enum SupportedSource {

            /**
             * config value MUST be set via environment variable; remote source is NOT supported
             */
            ENV_VAR,

            /**
             * config value MUST be set in the remote source; env var is NOT Supported
             */
            REMOTE,

            /**
             * config value may be set either via environment variable or remote config, with former taking precedence
             */
            ENV_VAR_OR_REMOTE,
        }
    }

    /**
     * get property as defined in this ConfigService
     *
     * @throws java.util.NoSuchElementException if property is not defined
     * @param property to retrieve value for
     * @return value
     */
    String getConfigPropertyOrError(ConfigProperty property);

    /**
     * get property as defined in this ConfigService, wrapped in Optional
     *
     * @see Optional
     *
     * @param property to retrieve value for
     * @return filled Optional if defined; empty otherwise
     */
    Optional<String> getConfigPropertyAsOptional(ConfigProperty property);


    default Optional<ConfigValueWithMetadata> getConfigPropertyWithMetadata(ConfigProperty configProperty) {
        return getConfigPropertyAsOptional(configProperty)
            .map(value -> ConfigValueWithMetadata.builder().value(value).build());
    }


    @Builder
    @Value
    class ConfigValueWithMetadata implements Serializable {

        String value;


        @Getter(value = AccessLevel.NONE)
        Instant lastModifiedDate;

        /**
         * @return time value last written, if known/available
         */
        public Optional<Instant> getLastModifiedDate() {
            return Optional.ofNullable(lastModifiedDate);
        }
    }

    /**
     * Represents a versioned configuration value with metadata
     */
    @Builder
    @Value
    class ConfigValueVersion implements Serializable {
        /**
         * The actual value of the configuration
         */
        String value;

        /**
         * When this version was last modified/created
         */
        Instant lastModifiedDate;

        /**
         * Version identifier (typically numeric, but represented as String for flexibility)
         */
        String version;
    }
}
