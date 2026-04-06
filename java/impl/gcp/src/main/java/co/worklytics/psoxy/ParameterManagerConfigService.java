package co.worklytics.psoxy;

import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.logging.Level;
import javax.inject.Inject;
import org.apache.commons.lang3.StringUtils;
import com.google.cloud.parametermanager.v1.ParameterManagerClient;
import com.google.cloud.parametermanager.v1.ParameterVersionName;
import co.worklytics.psoxy.gateway.ConfigService;
import co.worklytics.psoxy.gateway.impl.EnvVarsConfigService;
import dagger.assisted.Assisted;
import dagger.assisted.AssistedInject;
import lombok.NonNull;
import lombok.extern.java.Log;

/**
 * Read-only ConfigService implementation backed by GCP Parameter Manager.
 *
 * Used for non-secret configuration values (e.g., RULES, SERVICE_URL).
 * Secrets remain in Secret Manager via {@link SecretManagerSecretStore}.
 *
 * Parameters are addressed using hierarchical paths, e.g.:
 *   projects/{project}/locations/global/parameters/{namespace}RULES/versions/latest
 *
 * where namespace is something like "psoxy/gmail/" (uses "/" as separator).
 */
@Log
public class ParameterManagerConfigService implements ConfigService {

    @Inject
    EnvVarsConfigService envVarsConfigService;

    /**
     * Namespace prefix for parameter IDs (hierarchical, using '/' separator).
     * E.g., "psoxy/gmail/" — prepended to property names to form full parameter IDs.
     */
    final String namespace;

    /**
     * GCP project ID.
     */
    @NonNull
    final String projectId;

    @AssistedInject
    public ParameterManagerConfigService(@Assisted("projectId") @NonNull String projectId,
                                         @Assisted("namespace") @NonNull String namespace) {
        this.projectId = projectId;
        this.namespace = namespace;
    }

    @Override
    public String getConfigPropertyOrError(ConfigProperty property) {
        return getConfigPropertyAsOptional(property)
                .orElseThrow(() -> new NoSuchElementException("Proxy misconfigured; no value for " + property));
    }

    @Override
    public Optional<String> getConfigPropertyAsOptional(ConfigProperty property) {
        if (property.isEnvVarOnly()) {
            return Optional.empty();
        }

        String paramId = parameterName(property);

        try (ParameterManagerClient client = ParameterManagerClient.create()) {
            ParameterVersionName versionName =
                    ParameterVersionName.of(projectId, "global", paramId, "latest");

            var response = client.getParameterVersion(versionName);
            String value = response.getPayload().getData().toStringUtf8();

            if (StringUtils.isBlank(value)) {
                return Optional.empty();
            }
            return Optional.of(value);
        } catch (com.google.api.gax.rpc.NotFoundException e) {
            if (envVarsConfigService.isDevelopment()) {
                log.log(Level.INFO, "Could not find parameter " + paramId + " in Parameter Manager", e);
            }
            return Optional.empty();
        } catch (com.google.api.gax.rpc.PermissionDeniedException e) {
            if (envVarsConfigService.isDevelopment()) {
                log.log(Level.INFO, "PermissionDeniedException getting parameter " + paramId, e);
            }
            return Optional.empty();
        } catch (Exception e) {
            if (envVarsConfigService.isDevelopment()) {
                log.log(Level.INFO, "Exception getting parameter " + paramId + " from Parameter Manager", e);
            }
            return Optional.empty();
        }
    }

    private String parameterName(ConfigProperty property) {
        if (StringUtils.isBlank(this.namespace)) {
            return property.name();
        } else {
            return this.namespace + property.name();
        }
    }
}
