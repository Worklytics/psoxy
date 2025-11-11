package co.worklytics.psoxy.gateway;

import java.util.List;

public interface SecretStore extends WritableConfigService {

    /**
     * Get available versions of a configuration property, ordered by version DESC (most recent first).
     * 
     * This allows retrieving previous versions of secrets, which can be useful for cases like
     * OAuth token rotation where a recently-rotated token might not yet be fully propagated.
     * 
     * @param property the configuration property to retrieve versions for
     * @param limit maximum number of versions to return
     * @return list of available versions, ordered by version DESC; empty list if not supported or no versions exist
     */
    List<ConfigService.ConfigValueVersion> getAvailableVersions(ConfigProperty property, int limit);

}
