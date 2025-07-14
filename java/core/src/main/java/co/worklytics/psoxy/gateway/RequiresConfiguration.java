package co.worklytics.psoxy.gateway;

import java.util.List;
import java.util.Set;

public interface RequiresConfiguration {

    /**
     * @return set of any config properties that are required to be defined
     */
    Set<ConfigService.ConfigProperty> getRequiredConfigProperties();

    Set<ConfigService.ConfigProperty> getAllConfigProperties();

    /**
     * @return list of validation issues, if any, with the current configuration. eg that values well-formed/etc
     */
    default List<String> validateConfigValues() {
        return List.of();
    }
}
