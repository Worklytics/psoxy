package co.worklytics.psoxy.gateway;

import lombok.extern.java.Log;

/**
 * Configuration for logging/monitoring behavior, currently New Relic support.
 *
 * <p>New Relic monitoring is enabled when NEW_RELIC_ACCOUNT_ID is present.
 * For additional supported environment variables that control New Relic logging behavior, see the
 * New Relic documentation.
 *
 * @see <a href="https://docs.newrelic.com/docs/serverless-function-monitoring/aws-lambda-monitoring/instrument-lambda-function/env-variables-lambda/">
 *     New Relic: Environment variables for AWS Lambda</a>
 */
@Log
public class LoggingConfiguration {

    /**
     * Mandatory New Relic environment variables for AWS Lambda.
     *
     * @see <a href="https://docs.newrelic.com/docs/serverless-function-monitoring/aws-lambda-monitoring/instrument-lambda-function/env-variables-lambda/#mandatory-environment-variables">
     *     New Relic: Mandatory environment variables</a>
     */
    public enum NewRelicConfigProperties implements ConfigService.ConfigProperty {
        /** Your New Relic account ID. */
        NEW_RELIC_ACCOUNT_ID,
        /** The expected Lambda Handler. */
        NEW_RELIC_LAMBDA_HANDLER;

        @Override
        public SupportedSource getSupportedSource() {
            return SupportedSource.ENV_VAR;
        }
    }

    private final String newRelicAccountId;

    private final String newRelicLambdaHandler;

    private LoggingConfiguration(String newRelicAccountId, String newRelicLambdaHandler) {
        this.newRelicAccountId = newRelicAccountId;
        this.newRelicLambdaHandler = newRelicLambdaHandler;
    }

    /**
     * Returns {@code true} if New Relic monitoring is enabled (i.e. NEW_RELIC_ACCOUNT_ID is set).
     */
    public boolean isNewRelicEnabled() {
        return newRelicAccountId != null && !newRelicAccountId.isBlank();
    }

    /**
     * Optional method to validate the currently running handler against the configuring NEW_RELIC_LAMBDA_HANDLER.
     */
    public void validateNewRelicHandler(Class<?> currentHandlerClass) {
        if (isNewRelicEnabled()) {
            if (newRelicLambdaHandler == null || newRelicLambdaHandler.isBlank()) {
                log.warning("New Relic monitoring is enabled, but NEW_RELIC_LAMBDA_HANDLER is not set. " +
                    "It should be set to " + currentHandlerClass.getName() + " for this proxy deployment.");
            } else if (!newRelicLambdaHandler.equals(currentHandlerClass.getName())) {
                log.warning("New Relic monitoring is enabled, but NEW_RELIC_LAMBDA_HANDLER does not match " +
                    "the currently running handler. It is set to '" + newRelicLambdaHandler + "' but should be set to " +
                    "'" + currentHandlerClass.getName() + "'.");
            }
        }
    }

    public static LoggingConfiguration fromConfigService(ConfigService configService) {
        String accountId = configService.getConfigPropertyAsOptional(NewRelicConfigProperties.NEW_RELIC_ACCOUNT_ID)
            .orElse(null);
        String newRelicLambdaHandler = configService.getConfigPropertyAsOptional(NewRelicConfigProperties.NEW_RELIC_LAMBDA_HANDLER)
            .orElse(null);

        return new LoggingConfiguration(accountId, newRelicLambdaHandler);
    }
}
