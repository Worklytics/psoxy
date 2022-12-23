package co.worklytics.psoxy;

import co.worklytics.psoxy.gateway.ConfigService;
import co.worklytics.psoxy.gateway.ProxyConfigProperty;
import co.worklytics.psoxy.impl.SanitizerImpl;
import co.worklytics.psoxy.rules.RuleSet;
import com.google.common.base.Splitter;
import dagger.assisted.AssistedFactory;

import java.util.HashSet;
import java.util.Set;

@AssistedFactory
public interface SanitizerFactory {

    SanitizerImpl create(Sanitizer.ConfigurationOptions configurationOptions);

    //q: right place? mapping config+rules --> Sanitizer.Options isn't implementation-specific
    default Sanitizer.ConfigurationOptions buildOptions(ConfigService config, RuleSet rules) {
        Sanitizer.ConfigurationOptions.ConfigurationOptionsBuilder builder = Sanitizer.ConfigurationOptions.builder()
            .pseudonymizationSalt(config.getConfigPropertyAsOptional(ProxyConfigProperty.PSOXY_SALT)
                .orElseThrow(() -> new Error("Must configure value for SALT to generate pseudonyms")))
            .defaultScopeId(config.getConfigPropertyAsOptional(ProxyConfigProperty.IDENTIFIER_SCOPE_ID)
                .orElse(rules.getDefaultScopeIdForSource()));

        String customerDomainsConfig = config.getConfigPropertyAsOptional(ProxyConfigProperty.CUSTOMER_DOMAINS)
            .orElse("");
        Set<String> customerDomains = new HashSet<>(Splitter.on(",")
            .trimResults()
            .omitEmptyStrings()
            .splitToList(customerDomainsConfig));
        builder.customerDomains(customerDomains);

        config.getConfigPropertyAsOptional(ProxyConfigProperty.IGNORED_DOTS_ON_EMAILS)
            .map(Boolean::parseBoolean)
            .ifPresent(builder::ignoreDotsOnCustomerDomains);

        builder.rules(rules);

        return builder.build();
    }

}
