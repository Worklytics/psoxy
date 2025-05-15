package co.worklytics.psoxy.gateway;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * configuration properties used to control Proxy behavior in Bulk Mode
 *
 */
@NoArgsConstructor
@AllArgsConstructor
public enum BulkModeConfigProperty implements ConfigService.ConfigProperty {

    OUTPUT_BUCKET,

    /**
     * additional transforms to apply to each input file
     * @see co.worklytics.psoxy.storage.StorageHandler.ObjectTransform
     */
    ADDITIONAL_TRANSFORMS(SupportedSource.ENV_VAR_OR_REMOTE),

    /**
     * if provided, this path segment will be removed from keys of input object to produce
     * keys of corresponding output objects.
     *
     * NOTE: objects with keys that do not start with this segment will still be processed; this is
     * not any kind of filter.
     */
    INPUT_BASE_PATH,

    /**
     * if provided, this path segment will be prepended to keys of output objects, after removing
     * any {@link #INPUT_BASE_PATH} segment
     */
    OUTPUT_BASE_PATH,


    /**
     * **alpha feature** for testing if varying this improves performance at all; may be removed if
     * impact is negligible
     *
     * size of buffer used to read/write data from/to cloud storage (GCS, S3)
     *
     * default: 65536 (64KB)
     */
    BUFFER_SIZE,


    /**
     * whether output files produced by proxy instance should be compressed, regardless of whether
     * input files were compressed.
     *
     * default; true
     */
    COMPRESS_OUTPUT_ALWAYS,
    ;

    @Getter(onMethod_ = @Override)
    private SupportedSource supportedSource = SupportedSource.ENV_VAR;
}
