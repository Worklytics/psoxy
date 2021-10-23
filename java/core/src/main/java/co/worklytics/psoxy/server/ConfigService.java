package co.worklytics.psoxy.server;

import java.util.Optional;

public interface ConfigService {

    interface ConfigProperty {

        String name();
    }

    String getConfigPropertyOrError(ConfigProperty property);

    Optional<String> getConfigPropertyAsOptional(ConfigProperty property);

}
