package co.worklytics.psoxy.gateway;

import java.util.List;
import java.util.stream.Collectors;
import lombok.extern.java.Log;

/**
 * Configuration for logging/monitoring behavior, currently New Relic support.
 *
 * <p>New Relic monitoring is enabled when ALL mandatory environment variables are present.
 * If only a subset is set, a warning is logged and New Relic is disabled.
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
    public enum LoggingConfigProperty implements ConfigService.ConfigProperty {
        /** Your New Relic account ID. */
        NEW_RELIC_ACCOUNT_ID,
        /** The New Relic handler wrapper used to find your function's actual handler. */
        NEW_RELIC_LAMBDA_HANDLER,
        /** Your New Relic ingest license key. Overrides Secrets Manager if set. */
        NEW_RELIC_LICENSE_KEY,
        /**
         * Set to {@code true} to enable APM monitoring for your Lambda function.
         * Recommended value: {@code true}.
         */
        NEW_RELIC_APM_LAMBDA_MODE,
        /**
         * Your New Relic account ID or parent account ID (if the account has a parent).
         * Often the same value as NEW_RELIC_ACCOUNT_ID, but required separately for distributed
         * tracing to work across account boundaries.
         */
        NEW_RELIC_TRUSTED_ACCOUNT_KEY;

        @Override
        public SupportedSource getSupportedSource() {
            return SupportedSource.ENV_VAR;
        }
    }

    private final boolean newRelicEnabled;

    private LoggingConfiguration(boolean newRelicEnabled) {
        this.newRelicEnabled = newRelicEnabled;
    }

    /**
     * Returns {@code true} if all mandatory New Relic environment variables are set.
     */
    public boolean isNewRelicEnabled() {
        return newRelicEnabled;
    }

    public static LoggingConfiguration fromConfigService(ConfigService configService) {
        List<String> missing = List.of(LoggingConfigProperty.values()).stream()
            .filter(p -> configService.getConfigPropertyAsOptional(p).isEmpty())
            .map(Enum::name)
            .collect(Collectors.toList());

        boolean anySet = missing.size() < LoggingConfigProperty.values().length;
        boolean allSet = missing.isEmpty();

        if (anySet && !allSet) {
            log.warning("Some NEW_RELIC_* environment variables are set but not all mandatory ones are present. " +
                "New Relic monitoring will be DISABLED. Missing variables: " + missing +
                ". See https://docs.newrelic.com/docs/serverless-function-monitoring/aws-lambda-monitoring/instrument-lambda-function/env-variables-lambda/ for details.");
        }

        return new LoggingConfiguration(allSet);
    }
}
