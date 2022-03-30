package co.worklytics.psoxy;

import co.worklytics.psoxy.gateway.ConfigService;

public enum AWSConfigProperty implements ConfigService.ConfigProperty {
    IMPORT_BUCKET,
    OUTPUT_BUCKET;
}
