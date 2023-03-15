package co.worklytics.psoxy;

import co.worklytics.psoxy.gateway.ConfigService;

public enum AWSConfigProperty implements ConfigService.ConfigProperty {
    OUTPUT_BUCKET,

    ADDITIONAL_TRANSFORMS,
    ;
}
