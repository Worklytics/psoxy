package com.avaulta.gateway.rules;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.*;
import lombok.extern.java.Log;

import java.util.Map;

@Builder(toBuilder = true)
@Log
@AllArgsConstructor //for builder
@NoArgsConstructor //for Jackson
@Getter
@EqualsAndHashCode
@JsonInclude(JsonInclude.Include.NON_NULL) //NOTE: despite name, also affects YAML encoding
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
     * q: flatten somehow, so no indentation at the top-level?
     */
    Map<String, BulkDataRules> fileRules;

}
