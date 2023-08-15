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
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.NonNull;
import lombok.SneakyThrows;
import lombok.extern.java.Log;
import org.apache.commons.lang3.StringUtils;

import javax.inject.Inject;
import java.io.ByteArrayInputStream;
import java.util.*;
import java.util.concurrent.ExecutionException;
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


    transient Set<String> scopes;
    transient GoogleCredentials credentials;
    transient LoadingCache<String, GoogleCredentials> credentialsCache = CacheBuilder.newBuilder()
        .concurrencyLevel(1)
        .maximumSize(50) // as we usually shard per Google Workspace user account, this should be ~ number of active shards
        .recordStats()
        .build(CacheLoader.from(this::buildImpersonatedCredentials));


    @SneakyThrows
    @Override
    public Credentials getCredentials(Optional<String> userToImpersonate) {
        if (userToImpersonate.isEmpty()) {
            return this.getBaseCredentials();
        } else {
            try {
                return credentialsCache.get(userToImpersonate.get());
            } catch (ExecutionException e) {  // can't do whole thing in one-line bc of checked exception
                throw e.getCause();
            }
        }
    }

    @VisibleForTesting
    GoogleCredentials buildImpersonatedCredentials(@NonNull String accountToImpersonate) {

        if (!(getBaseCredentials() instanceof ServiceAccountCredentials)) {
            // only ServiceAccountCredentials (created from an actual service account key) support
            // domain-wide delegation
            // see examples - even when access is 'global', still need to impersonate a user
            // https://developers.google.com/admin-sdk/reports/v1/guides/delegation
            log.warning("Trying to impersonate user with credentials that don't support it");
        }

        //even though GoogleCredentials implements `createDelegated`, it's a no-op if the
        // credential type doesn't support it.
        GoogleCredentials impersonated = getBaseCredentials().createDelegated(accountToImpersonate);
        impersonated = impersonated.createScoped(getScopes());
        return impersonated;
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

    @SneakyThrows
    synchronized GoogleCredentials getBaseCredentials() {
        if (credentials == null) {
            Optional<String> key = config.getConfigPropertyAsOptional(ConfigProperty.SERVICE_ACCOUNT_KEY);

            GoogleCredentials provisional;
            if (key.isPresent()) {
                ByteArrayInputStream boas = key.map(this::toStream).orElseThrow();
                provisional = ServiceAccountCredentials.fromStream(boas, httpTransportFactory);
            } else {
                provisional = GoogleCredentials.getApplicationDefault();
            }
            credentials = provisional.createScoped(getScopes());
        }
        return credentials;
    }

   private  synchronized Set<String> getScopes() {
        if (scopes == null) {
            scopes = Arrays.stream(config.getConfigPropertyOrError(ConfigProperty.OAUTH_SCOPES).split(" "))
                .collect(Collectors.toSet());
        }
        return scopes;
    }

    @VisibleForTesting
    ByteArrayInputStream toStream(String base64encodedKey) {
        return new ByteArrayInputStream(Base64.getDecoder().decode(
            //strip whitespace around base64-encoded string; have seen these with artifacts from
            // copy-paste of the SA key into cloud consoles
            StringUtils.strip(base64encodedKey))
        );
    }

}
