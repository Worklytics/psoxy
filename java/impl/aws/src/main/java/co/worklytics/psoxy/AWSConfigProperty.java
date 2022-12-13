package co.worklytics.psoxy;

import co.worklytics.psoxy.gateway.ConfigService;

public enum AWSConfigProperty implements ConfigService.ConfigProperty {

    /**
     * execution role of this lambda instance
     *
     * can we get it from env var? don't see a way:
     * https://docs.aws.amazon.com/lambda/latest/dg/configuration-envvars.html#configuration-envvars-retrieve
     */
    EXECUTION_ROLE,

    IMPORT_BUCKET,
    OUTPUT_BUCKET,
    ;
}
