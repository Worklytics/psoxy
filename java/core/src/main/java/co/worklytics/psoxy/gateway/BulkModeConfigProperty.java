package co.worklytics.psoxy.gateway;

/**
 * configuration properties used to control Proxy behavior in Bulk Mode
 *
 */
public enum BulkModeConfigProperty implements ConfigService.ConfigProperty {

    OUTPUT_BUCKET,

    /**
     * additional transforms to apply to each input file
     * @see co.worklytics.psoxy.storage.StorageHandler.ObjectTransform
     */
    ADDITIONAL_TRANSFORMS,

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
    ;

}
