package co.worklytics.psoxy;

import co.worklytics.psoxy.gateway.ConfigService;
import co.worklytics.psoxy.gateway.SourceAuthConfigProperty;
import co.worklytics.psoxy.gateway.SourceAuthStrategy;
import co.worklytics.psoxy.gateway.impl.EnvVarsConfigService;
import com.google.auth.Credentials;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.auth.oauth2.ServiceAccountCredentials;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.SneakyThrows;

import javax.inject.Inject;
import java.io.ByteArrayInputStream;
import java.util.*;
import java.util.stream.Collectors;

@NoArgsConstructor(onConstructor = @__({@Inject}))
class GoogleCloudPlatformServiceAccountKeyAuthStrategy implements SourceAuthStrategy {

    @Getter
    private final String configIdentifier = "gcp_service_account_key";

    @Getter
    @Inject ConfigService config;

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

    @Override
    public Set<ConfigService.ConfigProperty> getRequiredConfigProperties() {
        return Set.of(
            SourceAuthConfigProperty.SERVICE_ACCOUNT_KEY,
            SourceAuthConfigProperty.OAUTH_SCOPES
        );
    }

}
