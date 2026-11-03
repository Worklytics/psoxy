package co.worklytics.psoxy.gateway;

import co.worklytics.psoxy.EmailCanonicalization;
import co.worklytics.psoxy.EmailDomainHandling;
import com.avaulta.gateway.pseudonyms.PseudonymImplementation;
import java.util.Optional;
import lombok.Builder;
import lombok.Value;

/**
 * Proxy-wide configuration loaded once at startup (see {@link #fromConfigService}).
 */
@Value
@Builder
public class ProxyConfig {

    public static ProxyConfig fromConfigService(ConfigService configService) {
        ProxyConfigBuilder builder = ProxyConfig.builder();

        configService.getConfigPropertyAsOptional(ProxyConfigProperty.USER_AGENT)
            .ifPresent(builder::userAgent);
        configService.getConfigPropertyAsOptional(ProxyConfigProperty.RULES)
            .ifPresent(builder::rules);
        configService.getConfigPropertyAsOptional(ProxyConfigProperty.CUSTOM_RULES_SHA)
            .ifPresent(builder::customRulesSha);
        configService.getConfigPropertyAsOptional(ProxyConfigProperty.IS_DEVELOPMENT_MODE)
            .map(Boolean::parseBoolean)
            .ifPresent(builder::developmentMode);
        configService.getConfigPropertyAsOptional(ProxyConfigProperty.SIDE_OUTPUT_ORIGINAL)
            .ifPresent(builder::sideOutputOriginal);
        configService.getConfigPropertyAsOptional(ProxyConfigProperty.SIDE_OUTPUT_SANITIZED)
            .ifPresent(builder::sideOutputSanitized);
        configService.getConfigPropertyAsOptional(ProxyConfigProperty.PSEUDONYM_IMPLEMENTATION)
            .map(PseudonymImplementation::parseConfigPropertyValue)
            .ifPresent(builder::pseudonymImplementation);
        configService.getConfigPropertyAsOptional(ProxyConfigProperty.EMAIL_CANONICALIZATION)
            .map(EmailCanonicalization::parseConfigPropertyValue)
            .ifPresent(builder::emailCanonicalization);
        configService.getConfigPropertyAsOptional(ProxyConfigProperty.EMAIL_DOMAIN_HANDLING)
            .map(EmailDomainHandling::parseConfigPropertyValue)
            .ifPresent(builder::emailDomainHandling);
        configService.getConfigPropertyAsOptional(ProxyConfigProperty.SOURCE)
            .ifPresent(builder::source);
        configService.getConfigPropertyAsOptional(ProxyConfigProperty.PSEUDONYMIZE_APP_IDS)
            .map(Boolean::parseBoolean)
            .ifPresent(builder::pseudonymizeAppIds);
        configService.getConfigPropertyAsOptional(ProxyConfigProperty.BUNDLE_FILENAME)
            .ifPresent(builder::bundleFilename);

        return builder.build();
    }

    String userAgent;
    String rules;
    String customRulesSha;
    @Builder.Default
    boolean developmentMode = false;
    String sideOutputOriginal;
    String sideOutputSanitized;
    PseudonymImplementation pseudonymImplementation;
    EmailCanonicalization emailCanonicalization;
    EmailDomainHandling emailDomainHandling;
    String source;
    @Builder.Default
    boolean pseudonymizeAppIds = false;
    String bundleFilename;

    public Optional<String> getUserAgent() {
        return Optional.ofNullable(userAgent);
    }

    public Optional<String> getRules() {
        return Optional.ofNullable(rules);
    }

    public Optional<String> getCustomRulesSha() {
        return Optional.ofNullable(customRulesSha);
    }

    public boolean isDevelopmentMode() {
        return developmentMode;
    }

    public Optional<String> getSideOutputOriginal() {
        return Optional.ofNullable(sideOutputOriginal);
    }

    public Optional<String> getSideOutputSanitized() {
        return Optional.ofNullable(sideOutputSanitized);
    }

    public Optional<PseudonymImplementation> getPseudonymImplementation() {
        return Optional.ofNullable(pseudonymImplementation);
    }

    public Optional<EmailCanonicalization> getEmailCanonicalization() {
        return Optional.ofNullable(emailCanonicalization);
    }

    public Optional<EmailDomainHandling> getEmailDomainHandling() {
        return Optional.ofNullable(emailDomainHandling);
    }

    public Optional<String> getSource() {
        return Optional.ofNullable(source);
    }

    public boolean isPseudonymizeAppIds() {
        return pseudonymizeAppIds;
    }

    public Optional<String> getBundleFilename() {
        return Optional.ofNullable(bundleFilename);
    }
}
