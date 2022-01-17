package co.worklytics.psoxy.gateway.impl;

import co.worklytics.psoxy.gateway.ConfigService;
import co.worklytics.psoxy.gateway.SourceAuthConfigProperty;
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
    private final static String configIdentifier = "gcp_service_account_key";

    @Inject ConfigService config;

    @SneakyThrows
    @Override
    public Credentials getCredentials(Optional<String> userToImpersonate) {

        Set<String> scopes = Arrays.stream(config.getConfigPropertyOrError(SourceAuthConfigProperty.OAUTH_SCOPES).split(","))
            .collect(Collectors.toSet());
        GoogleCredentials credentials;

        Optional<String> key = config.getConfigPropertyAsOptional(SourceAuthConfigProperty.SERVICE_ACCOUNT_KEY);

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
            SourceAuthConfigProperty.SERVICE_ACCOUNT_KEY,
            SourceAuthConfigProperty.OAUTH_SCOPES
        );
    }

}
