package co.worklytics.psoxy.gateway;

import com.google.common.util.concurrent.Uninterruptibles;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.Value;
import lombok.extern.java.Log;

import java.io.Serializable;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

public interface ConfigService {

    interface ConfigProperty extends Serializable {

        String name();

        // shared? (across multiple instances?)
        // local? (per-instance secrets)

        //sensitive? (eg, value should not be exposed to 3rd parties)

        //secret? (eg, value should be handled as a secret; obscured/acl even internally; avoid in logs, etc)

        /**
         * @return whether cached value for property must be revalidated with origin before re-use
         *  (equivalent to HTTP `Cache-Control: no-cache` semantics as defined by RFC 7234)
         *
         * @see 'https://www.rfc-editor.org/rfc/rfc7234#section-5.2.2'
         */
        default Boolean noCache() {
            return false;
        }
    }

    /**
     * whether implementation supports writing
     *
     * @return whether implementation supports writing
     */
    default boolean supportsWriting() {
        return false;
    }

    /**
     * write value of property in config, if supports it
     *
     * @param property to write value for
     * @param value to write
     */
    void putConfigProperty(ConfigProperty property, String value);

    /**
     * write value of property in config, if supports it
     *
     * @param property to write value for
     * @param value to write
     * @throws WritePropertyRetriesExhaustedException if write fails after designated retries
     */
    default void putConfigProperty(ConfigProperty property, String value, int retries) throws WritePropertyRetriesExhaustedException {
        if (retries <= 0) {
            // use the non-retry version
            throw new IllegalArgumentException("retries must be > 0");
        }
        do {
            try {
                putConfigProperty(property, value);
                return;
            } catch (Exception ignore) {
                // retry - wait slightly
                Uninterruptibles.sleepUninterruptibly(Duration.ofMillis(150));
            }
        } while (--retries > 0);
        throw new WritePropertyRetriesExhaustedException("Failed to write config property " + property);
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

    /**
     * @deprecated use EnvVarsConfigService::isDevelopment
     */
    @Deprecated // use EnvVarsConfigService::isDevelopment
    default boolean isDevelopment() {
        return this.getConfigPropertyAsOptional(ProxyConfigProperty.IS_DEVELOPMENT_MODE)
                .map(Boolean::parseBoolean).orElse(false);
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
}
