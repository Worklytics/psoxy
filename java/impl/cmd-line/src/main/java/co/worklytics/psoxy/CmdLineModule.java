package co.worklytics.psoxy;

import co.worklytics.psoxy.gateway.ConfigService;
import co.worklytics.psoxy.gateway.LockService;
import co.worklytics.psoxy.gateway.ProxyConfigProperty;
import co.worklytics.psoxy.gateway.SecretStore;
import co.worklytics.psoxy.gateway.impl.BlindlyOptimisticLockService;
import co.worklytics.psoxy.gateway.impl.EnvVarsConfigService;
import com.avaulta.gateway.tokens.DeterministicTokenizationStrategy;
import com.avaulta.gateway.tokens.ReversibleTokenizationStrategy;
import com.avaulta.gateway.tokens.impl.AESReversibleTokenizationStrategy;
import com.avaulta.gateway.tokens.impl.Sha256DeterministicTokenizationStrategy;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import dagger.Binds;
import dagger.Module;
import dagger.Provides;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import lombok.SneakyThrows;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.Options;

import javax.inject.Named;
import javax.inject.Singleton;
import java.io.File;
import java.util.Arrays;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

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


    @Module
    interface Bindings {

        @Binds
        LockService lockService(BlindlyOptimisticLockService impl);

    }

}
