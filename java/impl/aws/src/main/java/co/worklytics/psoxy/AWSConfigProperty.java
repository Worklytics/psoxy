package co.worklytics.psoxy;

import co.worklytics.psoxy.gateway.ConfigService;

public enum AWSConfigProperty implements ConfigService.ConfigProperty {
    BOM_ENCODED, // mark if file is encoded with Byte Order Mark (BOM)
    IMPORT_BUCKET,
    OUTPUT_BUCKET;
}
