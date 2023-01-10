package co.worklytics.psoxy.gateway.impl;

import co.worklytics.psoxy.gateway.ConfigService;
import co.worklytics.psoxy.gateway.SourceAuthStrategy;
import com.google.auth.Credentials;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.auth.oauth2.ServiceAccountCredentials;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.java.Log;

import javax.inject.Inject;
import java.io.ByteArrayInputStream;
import java.util.*;
import java.util.stream.Collectors;


@Log
@NoArgsConstructor(onConstructor_ = @Inject)
public class GoogleCloudPlatformServiceAccountKeyAuthStrategy implements SourceAuthStrategy {

    @Getter
    private final String configIdentifier = "gcp_service_account_key";

    /**
     * config properties that control how Psoxy authenticates against host
     */
    public enum ConfigProperty implements ConfigService.ConfigProperty {
        OAUTH_SCOPES,
        //this should ACTUALLY be stored in secret manager, and then exposed as env var to the
        // cloud function
        // see "https://cloud.google.com/functions/docs/configuring/secrets#gcloud"
        SERVICE_ACCOUNT_KEY,
    }

    @Inject ConfigService config;

    @SneakyThrows
    @Override
    public Credentials getCredentials(Optional<String> userToImpersonate) {

        Set<String> scopes = Arrays.stream(config.getConfigPropertyOrError(ConfigProperty.OAUTH_SCOPES).split(","))
            .collect(Collectors.toSet());
        GoogleCredentials credentials;

        Optional<String> key = config.getConfigPropertyAsOptional(ConfigProperty.SERVICE_ACCOUNT_KEY);

        if (key.isPresent()) {
            credentials = ServiceAccountCredentials.fromStream(new ByteArrayInputStream(Base64.getDecoder().decode(key.get())));
        } else {
            credentials = GoogleCredentials.getApplicationDefault();
        }

        if (userToImpersonate.isPresent()) {
            if (!(credentials instanceof ServiceAccountCredentials)) {
                // only ServiceAccountCredentials (created from an actual service account key) support
                // domain-wide delegation
                // see examples - even when access is 'global', still need to impersonate a user
                // https://developers.google.com/admin-sdk/reports/v1/guides/delegation
                log.warning("Trying to impersonate user with credentials that don't support it");
            }

            //even though GoogleCredentials implements `createDelegated`, it's a no-op if the
            // credential type doesn't support it.
            credentials = credentials.createDelegated(userToImpersonate.get());
        }

        credentials = credentials.createScoped(scopes);

        return credentials;
    }

    @Override
    public Set<ConfigService.ConfigProperty> getRequiredConfigProperties() {
        return Set.of(
            ConfigProperty.SERVICE_ACCOUNT_KEY,
            ConfigProperty.OAUTH_SCOPES
        );
    }


}
