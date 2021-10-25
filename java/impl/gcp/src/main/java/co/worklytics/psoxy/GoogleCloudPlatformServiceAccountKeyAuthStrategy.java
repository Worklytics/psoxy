package co.worklytics.psoxy;

import co.worklytics.psoxy.gateway.ConfigService;
import co.worklytics.psoxy.gateway.SourceAuthConfigProperty;
import co.worklytics.psoxy.gateway.SourceAuthStrategy;
import co.worklytics.psoxy.gateway.impl.EnvVarsConfigService;
import com.google.auth.Credentials;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.auth.oauth2.ServiceAccountCredentials;
import lombok.Getter;
import lombok.SneakyThrows;

import java.io.ByteArrayInputStream;
import java.util.Arrays;
import java.util.Base64;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

class GoogleCloudPlatformServiceAccountKeyAuthStrategy implements SourceAuthStrategy {

    @Getter
    private final static String configIdentifier = "gcp_service_account_key";

    ConfigService config;

    ConfigService getConfig() {
        if (config == null) {
            /**
             * in GCP cloud function, we should be able to configure everything via env vars; either
             * directly or by binding them to secrets at function deployment:
             *
             * @see "https://cloud.google.com/functions/docs/configuring/env-var"
             * @see "https://cloud.google.com/functions/docs/configuring/secrets"
             */
            config = new EnvVarsConfigService();
        }
        return config;
    }

    @SneakyThrows
    @Override
    public Credentials getCredentials(Optional<String> userToImpersonate) {

        Set<String> scopes = Arrays.stream(getConfig().getConfigPropertyOrError(SourceAuthConfigProperty.OAUTH_SCOPES).split(","))
            .collect(Collectors.toSet());
        GoogleCredentials credentials = GoogleCredentials.getApplicationDefault();

        if (!(credentials instanceof ServiceAccountCredentials)) {
            // only ServiceAccountCredentials (created from an actual service account key) support
            // domain-wide delegation
            // see examples - even when access is 'global', still need to impersonate a user
            // https://developers.google.com/admin-sdk/reports/v1/guides/delegation

            //NOTE: in practice SERVICE_ACCOUNT_KEY need not belong the to same service account
            // running the cloud function; but it could
            String key = getConfig().getConfigPropertyOrError(SourceAuthConfigProperty.SERVICE_ACCOUNT_KEY);
            credentials = ServiceAccountCredentials.fromStream(new ByteArrayInputStream(Base64.getDecoder().decode(key)));
        }

        if (userToImpersonate.isPresent()) {
            //even though GoogleCredentials implements `createDelegated`, it's a no-op if the
            // credential type doesn't support it.
            credentials = credentials.createDelegated(userToImpersonate.get());
        }

        credentials = credentials.createScoped(scopes);

        return credentials;
    }
}
