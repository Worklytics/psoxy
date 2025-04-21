package co.worklytics.psoxy;

import co.worklytics.psoxy.gateway.ConfigService;
import co.worklytics.psoxy.gateway.LockService;
import co.worklytics.psoxy.gateway.ProxyConfigProperty;
import co.worklytics.psoxy.gateway.SecretStore;
import co.worklytics.psoxy.gateway.impl.BlindlyOptimisticLockService;

import co.worklytics.psoxy.gateway.impl.GoogleCloudPlatformServiceAccountKeyAuthStrategy;
import com.avaulta.gateway.tokens.impl.Sha256DeterministicTokenizationStrategy;
import com.google.cloud.secretmanager.v1.AccessSecretVersionResponse;
import com.google.cloud.secretmanager.v1.SecretManagerServiceClient;
import dagger.Binds;
import dagger.Module;
import dagger.Provides;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import javax.inject.Singleton;
import java.io.IOException;

@NoArgsConstructor
@AllArgsConstructor
@Module(
   includes = CmdLineModule.Bindings.class
)
public class CmdLineModule {


    String[] args;


    @Provides @Singleton
    ConfigService configService(CommandLineConfigServiceFactory factory) {
        return factory.create(args);
    }

    @Provides @Singleton
    SecretStore secretStore(CommandLineConfigServiceFactory factory) {
        return factory.create(args);
    }

    @Provides @Singleton
    Sha256DeterministicTokenizationStrategy tokenizationStrategy(CommandLineConfigServiceFactory factory) {
        CommandLineConfigService configService = factory.create(args);
        return new Sha256DeterministicTokenizationStrategy(configService.getConfigPropertyAsOptional(ProxyConfigProperty.PSOXY_SALT)
            .orElseGet(() -> {
                try (SecretManagerServiceClient client = SecretManagerServiceClient.create()) {
                    AccessSecretVersionResponse secretVersionResponse =
                        client.accessSecretVersion(configService.getCliConfig().getPseudonymizationSaltSecret().getIdentifier());
                    return secretVersionResponse.getPayload().getData().toStringUtf8();
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }));
    }

    @Module
    interface Bindings {

        @Binds
        LockService lockService(BlindlyOptimisticLockService impl);

    }

}
