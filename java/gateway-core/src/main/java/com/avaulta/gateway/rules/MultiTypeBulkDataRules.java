package com.avaulta.gateway.rules;

import java.util.Map;

public class MultiTypeBulkDataRules implements RuleSet {

    /**
     * map of file path templates to rules for matching files
     *
     * eg, /export/{week}/data_{shard}.csv -> CsvRules
     *
     * if provided, has the effect of pathRegex = "^/export/[^/]+/data_[^/]+\.csv$"
     *
     * files that trigger proxy instance but match NONE of the templates will not be processed
     *
     *
     * q: support multiple jumps?
     *
     * q: what if file matches multiple path templates? pick lexicographically first?
     *
     */
    Map<String, BulkDataRules> fileRules;

}
