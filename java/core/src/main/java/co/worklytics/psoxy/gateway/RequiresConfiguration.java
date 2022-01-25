package co.worklytics.psoxy.gateway;

import java.util.Set;

public interface RequiresConfiguration {

    /**
     * @return set of any config properties that are required to be defined
     */
    Set<ConfigService.ConfigProperty> getRequiredConfigProperties();
}
