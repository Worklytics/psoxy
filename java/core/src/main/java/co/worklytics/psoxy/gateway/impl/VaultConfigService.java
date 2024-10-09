package co.worklytics.psoxy.gateway.impl;

import co.worklytics.psoxy.gateway.ConfigService;
import co.worklytics.psoxy.gateway.SecretStore;
import com.bettercloud.vault.SslConfig;
import com.bettercloud.vault.Vault;
import com.bettercloud.vault.VaultConfig;
import com.bettercloud.vault.VaultException;
import com.bettercloud.vault.response.AuthResponse;
import com.bettercloud.vault.response.LogicalResponse;
import com.bettercloud.vault.response.LookupResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import dagger.assisted.Assisted;
import dagger.assisted.AssistedInject;
import lombok.Getter;
import lombok.NonNull;
import lombok.SneakyThrows;
import lombok.extern.java.Log;
import org.apache.commons.lang3.StringUtils;

import java.time.Duration;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.logging.Level;

@Log
public class VaultConfigService implements SecretStore {

    //these config values must be provided as ENV_VARs to use vault as secret store
    public enum VaultConfigProperty implements ConfigProperty {
        VAULT_ADDR,
        VAULT_NAMESPACE, // optional; if omitted, won't be set
        VAULT_TOKEN, //makes this pretty f'ing circular ...

        // name of role in Vault (not an AWS IAM role) to auth as, if not simply the function's name
        VAULT_ROLE,

        //TODO: support this, so self-signed certificates on vault certificates can be used with
        // verification
        //SSL certificate to be used to validate SSL connection to vault server
        //base64 encoding of an X.509 certificate in PEM format with UTF-8 encoding
        //VAULT_SSL_CERTIFICATE,
        ;

        @Getter
        private boolean envVarOnly = true;
    }

    //q: vault caters to storing secrets in groups, as a "map" (eg key-value pairs)
    // do we want to follow this paradigm or not? this makes sense by scope (global, vs per-function
    // values), but that distinction not in Java implementation atm.
    public static final String VALUE_FIELD = "value";


    //how long we want vault auth token to be good for
    // as proxy isn't continually running, and usually runs nightly
    // NOTE: if proxy doesn't run AT LEAST this option, and configured with 'periodic' token, it
    // might expire ...
    public static final Duration MIN_AUTH_TTL = Duration.ofHours(36);
    public static final Duration IAM_AUTH_MIN_TTL = Duration.ofHours(2);

    static final Integer VAULT_ENGINE_VERSION = 2;

    @Getter @NonNull
    final String path;

    Vault vault;

    @AssistedInject
    VaultConfigService(Vault vault,
                       @Assisted @NonNull String path) {
        this.vault = vault;
        this.path = path;
    }

    //q: do we even want to support this? possibly just for local dev
    @SneakyThrows
    public static Vault createVaultClientFromEnvVarsToken(EnvVarsConfigService envVarsConfigService) {
        VaultConfig vaultConfig =
            new VaultConfig()
                .engineVersion(VAULT_ENGINE_VERSION) //avoid log complaints about not providing a value
                .sslConfig(new SslConfig())
                .address(envVarsConfigService.getConfigPropertyOrError(VaultConfigService.VaultConfigProperty.VAULT_ADDR))
                .token(envVarsConfigService.getConfigPropertyOrError(VaultConfigService.VaultConfigProperty.VAULT_TOKEN));

        envVarsConfigService.getConfigPropertyAsOptional(VaultConfigService.VaultConfigProperty.VAULT_NAMESPACE)
            .filter(StringUtils::isNotBlank)  //don't bother tossing error here, assume meant no namespace
            .ifPresent(ns -> {
                try {
                    vaultConfig.nameSpace(ns);
                } catch (VaultException e) {
                    throw new Error("Error setting Vault namespace", e);
                }
            });

        return new Vault(vaultConfig.build());
    }


    /**
     * initializes for use
     * @return itself, for chaining
     */
    @SneakyThrows
    public VaultConfigService init() {
        LookupResponse initialAuth = this.vault.auth().lookupSelf();

        log.info("Token: " + (new ObjectMapper()).writeValueAsString(initialAuth));

        if (needsRenewal(initialAuth)) {
            if (!initialAuth.isRenewable()) {
                //appears we don't actually have a 'periodic' token? or it's expired such that can't renew?
                log.warning("Vault token should be renewed, but is not renewable");
            }
            try {
                log.info("Renewing vault token.");
                AuthResponse renewedAuth = this.vault.auth()
                    .renewSelf(MIN_AUTH_TTL.getSeconds() * 2);
                log.info("Renewed Vault token. New TTL: " + renewedAuth.getAuthLeaseDuration() + " seconds");
            } catch (VaultException e) {
                //log, but continue in case remaining one has some validity
                log.log(Level.WARNING, "Failed to renew Vault token. ", e);
            }
        }
        return this;
    }

    @SneakyThrows
    @Override
    public void putConfigProperty(ConfigProperty property, String value) {
        if (property.isEnvVarOnly()) {
            throw new IllegalArgumentException("Can't put env-only config property: " + property);
        }

        vault.logical()
            .write(path(property), Map.of(VALUE_FIELD, value));
    }

    @SneakyThrows
    @Override
    public String getConfigPropertyOrError(ConfigProperty property) {
        if (property.isEnvVarOnly()) {
            throw new IllegalArgumentException("Can't get env-only config property: " + property);
        }

        LogicalResponse response = vault.logical()
            .read(path(property));
        if (response.getRestResponse().getStatus() == 200) {
            return response.getData().get(VALUE_FIELD);
        } else {
            //403 unless explicit ACL policy set
            log.info(property.name() + " " + response.getRestResponse().getStatus() + " " + new String(response.getRestResponse().getBody()));
            throw new NoSuchElementException("Missing config value: " + property.name());
        }
    }

    @SneakyThrows
    @Override
    public Optional<String> getConfigPropertyAsOptional(ConfigProperty property) {
        if (property.isEnvVarOnly()) {
            return Optional.empty();
        }

        LogicalResponse response = vault.logical()
            .read(path(property));

        if (response.getRestResponse().getStatus() == 200) {
            log.info("Config value for " + path(property)  + " from Vault");
            return Optional.ofNullable(response.getData().get(VALUE_FIELD));
        } else {
            //403 unless explicit ACL policy set
            //also 403 if no property?
            log.info(path(property) + " " + response.getRestResponse().getStatus() + " " + new String(response.getRestResponse().getBody()));
            return Optional.empty();
        }
    }

    private String path(@NonNull ConfigProperty configProperty) {
        return getPath() + configProperty.name();
    }

    boolean isIamAuthMethod(String path) {
        return path.startsWith("auth/aws") || path.startsWith("auth/gcp");
    }
    boolean needsRenewal(LookupResponse tokenLookupResponse) {
        if (tokenLookupResponse.getTTL() == 0) {
            // 0 is default value in client; 0 also means 'infinite', per their docs:
            // https://developer.hashicorp.com/vault/docs/concepts/tokens#token-time-to-live-periodic-tokens-and-explicit-max-ttls

            //NOTE: if ttl not infinite, we better have a 'periodic' token; as current impl of Vault
            // takes token from ENV_VAR, which is presumably not writable by the java process
            return false;
        } else if (isIamAuthMethod(tokenLookupResponse.getPath())) {
            return tokenLookupResponse.getTTL() < IAM_AUTH_MIN_TTL.getSeconds();
        } else {
            //NOTE: if ttl not infinite, and not an IAM token, we better have a 'periodic' token;
            // as current impl of Vault takes token from ENV_VAR, which is presumably not writable
            // by the java process
            return tokenLookupResponse.getTTL() < MIN_AUTH_TTL.getSeconds();
        }
    }

}
