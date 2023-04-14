package co.worklytics.psoxy;

import co.worklytics.psoxy.gateway.ConfigService;
import co.worklytics.psoxy.gateway.impl.EnvVarsConfigService;
import com.google.cloud.secretmanager.v1.*;
import com.google.protobuf.ByteString;
import dagger.assisted.Assisted;
import dagger.assisted.AssistedInject;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.java.Log;
import org.apache.commons.lang3.StringUtils;

import javax.inject.Inject;
import java.io.IOException;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.logging.Level;

@Log
public class SecretManagerConfigService implements ConfigService {

    @Inject EnvVarsConfigService envVarsConfigService;

    /**
     * Namespace to use; it could be empty for accessing all the secrets or with some value will be used
     * for being the prefix of the key to use. Ex of a key using a namespace: someNamespace_myKey
     */
    final String namespace;

    /**
     * GCP projectId to use; it could be the name of the project (my-project) or its id (1234)
     */
    @NonNull
    final String projectId;

    @AssistedInject
    public SecretManagerConfigService(@Assisted("projectId") @NonNull String projectId,
                                      @Assisted("namespace") @NonNull String namespace) {
        this.projectId = projectId;
        this.namespace = namespace;
    }

    @Override
    public boolean supportsWriting() {
        return true;
    }

    @Override
    public void putConfigProperty(ConfigProperty property, String value) {
        String key = parameterName(property);
        try {
            try (SecretManagerServiceClient client = SecretManagerServiceClient.create()) {
                SecretPayload payload =
                        SecretPayload.newBuilder()
                                .setData(ByteString.copyFrom(value.getBytes()))
                                .build();

                // Add the secret version.
                SecretVersion version = client.addSecretVersion(key, payload);

                log.info(String.format("Property: %s, stored version %s", key, version.getName()));
            }
        } catch (IOException e) {
            log.log(Level.SEVERE, "Could not store property " + key, e);
        }
    }

    @Override
    public String getConfigPropertyOrError(ConfigProperty property) {
        return getConfigPropertyAsOptional(property)
                .orElseThrow(() -> new NoSuchElementException("Proxy misconfigured; no value for " + property));
    }

    @SneakyThrows
    @Override
    public Optional<String> getConfigPropertyAsOptional(ConfigProperty property) {
        String paramName = parameterName(property);
        try (SecretManagerServiceClient client = SecretManagerServiceClient.create()) {
            SecretVersionName secretVersionName = SecretVersionName.of(projectId, paramName, "latest");

            // Access the secret version.
            AccessSecretVersionResponse response = client.accessSecretVersion(secretVersionName);

            return Optional.of(response.getPayload().getData().toStringUtf8());
        } catch (Exception ignored) {
            if (envVarsConfigService.isDevelopment()) {
                log.log(Level.INFO, "Could not find secret " + paramName + " in Secret Manager", ignored);
            }
            // If secret is not found, it will return an exception
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
