package co.worklytics.psoxy.gateway;

import java.io.Serializable;
import java.util.Optional;

public interface ConfigService {

    interface ConfigProperty extends Serializable {

        String name();

        // shared? (across multiple instances?)
        // local? (per-instance secrets)

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

    @Deprecated // use EnvVarsConfigService::isDevelopment
    default boolean isDevelopment() {
        return this.getConfigPropertyAsOptional(ProxyConfigProperty.IS_DEVELOPMENT_MODE)
            .map(Boolean::parseBoolean).orElse(false);
    }
}
