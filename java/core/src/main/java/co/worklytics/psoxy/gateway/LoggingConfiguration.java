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
        return LoggingConfiguration.builder()
            .newRelicAccountId(configService.getConfigPropertyAsOptional(LoggingConfigProperty.NEW_RELIC_ACCOUNT_ID).orElse(null))
            .newRelicPrimaryApplicationId(configService.getConfigPropertyAsOptional(LoggingConfigProperty.NEW_RELIC_PRIMARY_APPLICATION_ID).orElse(null))
            .newRelicTrustedAccountKey(configService.getConfigPropertyAsOptional(LoggingConfigProperty.NEW_RELIC_TRUSTED_ACCOUNT_KEY).orElse(null))
            .newRelicLambdaHandler(configService.getConfigPropertyAsOptional(LoggingConfigProperty.NEW_RELIC_LAMBDA_HANDLER).orElse(null))
            .build();
    }
}
