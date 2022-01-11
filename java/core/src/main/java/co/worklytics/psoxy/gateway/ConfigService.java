package co.worklytics.psoxy.gateway;

import java.util.Optional;

public interface ConfigService {

    interface ConfigProperty {

        String name();
    }

    String getConfigPropertyOrError(ConfigProperty property);

    Optional<String> getConfigPropertyAsOptional(ConfigProperty property);

    default boolean isDevelopment() {
        return this.getConfigPropertyAsOptional(ProxyConfigProperty.IS_DEVELOPMENT_MODE)
            .map(Boolean::parseBoolean).orElse(false);
    }

}
