package co.worklytics.psoxy;

import com.bettercloud.vault.SslConfig;
import com.bettercloud.vault.Vault;
import com.bettercloud.vault.VaultConfig;
import com.bettercloud.vault.response.AuthResponse;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.auth.oauth2.IdTokenCredentials;
import com.google.auth.oauth2.IdTokenProvider;
import lombok.NoArgsConstructor;
import lombok.SneakyThrows;

import javax.inject.Inject;

@NoArgsConstructor(onConstructor = @__(@Inject))
public class VaultGcpIamAuth {


    @SneakyThrows
    Vault createVaultClient(String vaultAddress,
                            String vaultRole,
                            GoogleCredentials googleCredentials) {

        VaultConfig authConfig = new VaultConfig()
            .sslConfig(new SslConfig())
            .address(vaultAddress);

        //service account this Cloud Function runs as must have roles/iam.serviceAccountTokenCreator on itself


        IdTokenCredentials idTokenCredentials =
            IdTokenCredentials.newBuilder()
                .setIdTokenProvider((IdTokenProvider) googleCredentials)
                .setTargetAudience(vaultAddress)
                .build();


        Vault auth = new Vault(authConfig);
        AuthResponse r = auth.auth().loginByGCP(
            vaultRole,
            idTokenCredentials.refreshAccessToken().getTokenValue());

        VaultConfig config = new VaultConfig()
            .sslConfig(new SslConfig())
            .address(vaultAddress)
            .token(r.getAuthClientToken());

        return new Vault(config);
    }




}
