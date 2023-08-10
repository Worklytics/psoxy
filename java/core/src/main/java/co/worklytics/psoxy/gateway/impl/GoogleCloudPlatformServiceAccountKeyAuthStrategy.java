package co.worklytics.psoxy.gateway.impl;

import co.worklytics.psoxy.gateway.ConfigService;
import co.worklytics.psoxy.gateway.SourceAuthStrategy;
import com.google.api.client.http.HttpRequestFactory;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.testing.http.MockHttpTransport;
import com.google.auth.Credentials;
import com.google.auth.http.HttpTransportFactory;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.auth.oauth2.ServiceAccountCredentials;
import com.google.common.annotations.VisibleForTesting;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.java.Log;
import org.apache.commons.lang3.StringUtils;

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
    @Inject HttpTransportFactory httpTransportFactory;

    @SneakyThrows
    @Override
    public Credentials getCredentials(Optional<String> userToImpersonate) {

        Set<String> scopes = Arrays.stream(config.getConfigPropertyOrError(ConfigProperty.OAUTH_SCOPES).split(" "))
            .collect(Collectors.toSet());
        GoogleCredentials credentials;

        Optional<String> key = config.getConfigPropertyAsOptional(ConfigProperty.SERVICE_ACCOUNT_KEY);

        if (key.isPresent()) {
            ByteArrayInputStream boas = key.map(this::toStream).orElseThrow();
            credentials = ServiceAccountCredentials.fromStream(boas, httpTransportFactory);
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

    @VisibleForTesting
    ByteArrayInputStream toStream(String base64encodedKey) {
        return new ByteArrayInputStream(Base64.getDecoder().decode(
            //strip whitespace around base64-encoded string; have seen these with artifacts from
            // copy-paste of the SA key into cloud consoles
            StringUtils.strip(base64encodedKey))
        );
    }

    @Override
    public Set<ConfigService.ConfigProperty> getRequiredConfigProperties() {
        return Set.of(
            ConfigProperty.SERVICE_ACCOUNT_KEY,
            ConfigProperty.OAUTH_SCOPES
        );
    }

    @Override
    public Set<ConfigService.ConfigProperty> getAllConfigProperties() {
        return Set.of(ConfigProperty.values());
    }


}
