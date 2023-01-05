package co.worklytics.psoxy.gateway;

import java.io.Serializable;
import java.util.Optional;

public interface ConfigService {

    interface ConfigProperty extends Serializable {

        String name();

        // shared? (across multiple instances?)
        // local? (per-instance secrets)
    }

    default boolean supportsWriting() {
        return false;
    }

    void putConfigProperty(ConfigProperty property, String value);

    String getConfigPropertyOrError(ConfigProperty property);

    Optional<String> getConfigPropertyAsOptional(ConfigProperty property);

    default boolean isDevelopment() {
        return this.getConfigPropertyAsOptional(ProxyConfigProperty.IS_DEVELOPMENT_MODE)
            .map(Boolean::parseBoolean).orElse(false);
    }
}
