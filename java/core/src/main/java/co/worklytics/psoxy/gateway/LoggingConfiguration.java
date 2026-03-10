package co.worklytics.psoxy.gateway;

import lombok.Builder;
import lombok.Getter;

/**
 * POJO for configuring logging/monitoring behavior.
 */
@Builder
@Getter
public class LoggingConfiguration {

    public enum LoggingConfigProperty implements ConfigService.ConfigProperty {
        NEW_RELIC_ACCOUNT_ID,
        NEW_RELIC_PRIMARY_APPLICATION_ID,
        NEW_RELIC_TRUSTED_ACCOUNT_KEY,
        NEW_RELIC_LAMBDA_HANDLER;

        @Override
        public SupportedSource getSupportedSource() {
            return SupportedSource.ENV_VAR;
        }
    }

    private final String newRelicAccountId;
    private final String newRelicPrimaryApplicationId;
    private final String newRelicTrustedAccountKey;
    private final String newRelicLambdaHandler;

    public boolean isNewRelicEnabled() {
        return newRelicAccountId != null || newRelicPrimaryApplicationId != null
             || newRelicTrustedAccountKey != null || newRelicLambdaHandler != null;
    }

    public static LoggingConfiguration fromConfigService(ConfigService configService) {
        LoggingConfiguration.LoggingConfigurationBuilder builder = LoggingConfiguration.builder();

        configService.getConfigPropertyAsOptional(LoggingConfigProperty.NEW_RELIC_ACCOUNT_ID).ifPresent(builder::newRelicAccountId);
        configService.getConfigPropertyAsOptional(LoggingConfigProperty.NEW_RELIC_PRIMARY_APPLICATION_ID).ifPresent(builder::newRelicPrimaryApplicationId);
        configService.getConfigPropertyAsOptional(LoggingConfigProperty.NEW_RELIC_TRUSTED_ACCOUNT_KEY).ifPresent(builder::newRelicTrustedAccountKey);
        configService.getConfigPropertyAsOptional(LoggingConfigProperty.NEW_RELIC_LAMBDA_HANDLER).ifPresent(builder::newRelicLambdaHandler);
        
        return builder.build();
    }
}
