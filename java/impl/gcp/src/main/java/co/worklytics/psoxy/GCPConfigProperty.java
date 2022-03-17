package co.worklytics.psoxy;

import co.worklytics.psoxy.gateway.ConfigService;

public enum GCPConfigProperty implements ConfigService.ConfigProperty {
    IMPORT_BUCKET,
    OUTPUT_BUCKET;
}
