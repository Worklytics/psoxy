package co.worklytics.psoxy.gateway.impl;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.Base64;
import java.util.Optional;
import java.util.Set;
import java.util.logging.Level;
import java.util.stream.Collectors;
import javax.inject.Inject;
import org.apache.commons.lang3.StringUtils;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.auth.Credentials;
import com.google.auth.http.HttpTransportFactory;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.auth.oauth2.ServiceAccountCredentials;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import co.worklytics.psoxy.gateway.ConfigService;
import co.worklytics.psoxy.gateway.SecretStore;
import co.worklytics.psoxy.gateway.SourceAuthStrategy;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.NonNull;
import lombok.SneakyThrows;
import lombok.extern.java.Log;


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
    @Inject SecretStore secretStore;
    @Inject HttpTransportFactory httpTransportFactory;
    @Inject ObjectMapper objectMapper;


    /**
     * parsed from {@link ConfigProperty#OAUTH_SCOPES}; kept in useful for just to avoid repeated
     * split on ' ' and allocation of Set<>.
     */
    Set<String> scopes;

    /**
     * base credentials, without any impersonation; in practice, should always be
     * ServiceAccountCredentials (created from a service account key)
     */
    transient GoogleCredentials baseCredentials;

    transient LoadingCache<String, GoogleCredentials> credentialsCache = CacheBuilder.newBuilder()
        .concurrencyLevel(1)
        .maximumSize(50) // as we usually shard per Google Workspace user account, this should be ~ number of active shards
        .recordStats()
        .build(CacheLoader.from(this::buildImpersonatedCredentials));


    @Override
    public Credentials getCredentials(Optional<String> userToImpersonate) {
        return userToImpersonate.map(credentialsCache::getUnchecked)
            .orElseGet(this::getBaseCredentials);
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
        if (baseCredentials == null) {
            Optional<String> key = secretStore.getConfigPropertyAsOptional(ConfigProperty.SERVICE_ACCOUNT_KEY);

            GoogleCredentials provisional;

            if (key.isPresent()) {
                try (ByteArrayInputStream boas = key.map(this::toStream).orElseThrow()) {
                    provisional = ServiceAccountCredentials.fromStream(boas, httpTransportFactory);
                    log.info("Base credentials pulled from stream of SERVICE_ACCOUNT_KEY");
                }
            } else {
                provisional = GoogleCredentials.getApplicationDefault();
            }
            baseCredentials = provisional.createScoped(getScopes());
        }
        return baseCredentials;
    }


    @VisibleForTesting
    GoogleCredentials buildImpersonatedCredentials(@NonNull String accountToImpersonate) {

        GoogleCredentials baseCredentials = getBaseCredentials();
        if (!(baseCredentials instanceof ServiceAccountCredentials)) {
            // only ServiceAccountCredentials (created from an actual service account key) support
            // domain-wide delegation
            // see examples - even when access is 'global', still need to impersonate a user
            // https://developers.google.com/admin-sdk/reports/v1/guides/delegation
            log.warning("Trying to impersonate user with credentials that don't support it");
        }

        //even though GoogleCredentials implements `createDelegated`, it's a no-op if the
        // credential type doesn't support it.
        // similarly, createScoped() is no-op if not supported by GoogleCredentials implementation
        // but if they are ops that would mutate underlying credential, they do invoke toBuilder()
        // again and return a clone of the credential with the change - so safe; we're not mutating
        // the same base credentials instance via multiple pointers or anything
        return baseCredentials
            .createDelegated(accountToImpersonate)
            .createScoped(getScopes());
    }

    private synchronized Set<String> getScopes() {
        if (scopes == null) {
            scopes = Arrays.stream(config.getConfigPropertyOrError(ConfigProperty.OAUTH_SCOPES).split(" "))
                .collect(Collectors.toSet());
        }
        return scopes;
    }

    @VisibleForTesting
    ByteArrayInputStream toStream(String key) {
        String trimmedKey = StringUtils.strip(key);
        
        // Check if the key is plain JSON (starts with '{')
        if (trimmedKey.startsWith("{")) {
            // Plain JSON format - convert directly to bytes using UTF-8
            log.finest("Service account key detected as plain JSON format");

            try {
                @SuppressWarnings("unused") // we're just checking if it's valid JSON, not using the result
                JsonNode jsonNode = objectMapper.readTree(trimmedKey);
            } catch (IOException e) {
                log.log(Level.WARNING, "GCP Service Account Key retrieved from config appears to be JSON, but parsing failed. Will passed it to Google's library for parsing, but most likely that will fail", e);
            }
            return new ByteArrayInputStream(trimmedKey.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        } else {
            // Assume base64-encoded format
            log.info("Service account key detected as base64-encoded format");
            try {
                return new ByteArrayInputStream(Base64.getDecoder().decode(trimmedKey));
            } catch (IllegalArgumentException e) {
                // re-throw variant, but add information to aid debugging 
                throw new IllegalArgumentException("GCP Service Account Key retrieved from config does not appear to be be JSON, or base64-encoded JSON", e);
            }
        }
    }

}
