package co.worklytics.psoxy;

import co.worklytics.psoxy.gateway.ConfigService;
import co.worklytics.psoxy.gateway.ProxyConfigProperty;
import co.worklytics.psoxy.gateway.SecretStore;
import co.worklytics.test.MockModules;
import com.avaulta.gateway.pseudonyms.PseudonymImplementation;
import org.apache.commons.lang3.StringUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PseudonymizerImplFactoryTest {

    ConfigService configService;
    SecretStore secretStore;
    PseudonymizerImplFactory factory;
    @BeforeEach
    public void setup() {
        configService = MockModules.provideMock(ConfigService.class);
        secretStore = MockModules.provideMock(SecretStore.class);
        when(secretStore.getConfigPropertyAsOptional(ProxyConfigProperty.PSOXY_SALT)).thenReturn(Optional.of("salt"));

        //interested in API case, when this is not set (expect legacy pseudonyms requested w header)
        when(configService.getConfigPropertyAsOptional(ProxyConfigProperty.PSEUDONYM_IMPLEMENTATION))
            .thenReturn(Optional.empty());

        factory = new Example();
    }

    static class Example implements PseudonymizerImplFactory {

        @Override
        public PseudonymizerImpl create(Pseudonymizer.ConfigurationOptions configurationOptions) {
            return null;
        }
    }

}
