package co.worklytics.psoxy;

import co.worklytics.psoxy.gateway.ConfigService;
import co.worklytics.psoxy.gateway.ProxyConfigProperty;
import co.worklytics.psoxy.impl.SanitizerImpl;

import co.worklytics.psoxy.rules.RuleSet;
import com.avaulta.gateway.pseudonyms.EmailDomainPolicy;
import com.google.common.base.Enums;
import dagger.assisted.AssistedFactory;
import org.apache.commons.lang3.EnumUtils;
import org.apache.commons.lang3.StringUtils;

import java.util.Arrays;
import java.util.stream.Collectors;

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

        builder.rules(rules);

        config.getConfigPropertyAsOptional(ProxyConfigProperty.EMAIL_DOMAIN_POLICY)
            .map(EmailDomainPolicy::parseOrDefault)
            .ifPresent(builder::emailDomainPolicy);

        builder.emailDomainPolicyExceptions(
            config.getConfigPropertyAsOptional(ProxyConfigProperty.EMAIL_DOMAIN_POLICY_EXCEPTIONS)
            .map(s -> Arrays.stream(s.split(","))
                .filter(StringUtils::isNotBlank)
                .map(SanitizerImpl::emailDomainCanonicalization)
                .collect(Collectors.toSet()))
            .orElse(null)
        );

        return builder.build();
    }

}
