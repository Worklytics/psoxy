package com.avaulta.gateway.rules;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.*;
import lombok.extern.java.Log;

import java.util.Map;

/**
 * rules that define how to sanitize bulk (file) data from a connector that is a mix of multiple
 * file types (schemas)
 *
 * **BETA** - may change in future versions, in particular:
 *   - solve how ambiguous matches are resolved (eg, file paths that match to MULTIPLE rules - which
 *     are applied)
 *   - solve hierarchical rules (eg, nested MultiTypeBulkDataRules, so root directory can be spec'd
 *     once rather than for every file type); current solution in practice will only match ONE
 *     level of nesting
 *
 */
@Builder(toBuilder = true)
@Log
@AllArgsConstructor //for builder
@NoArgsConstructor //for Jackson
@Getter
@EqualsAndHashCode
@JsonInclude(JsonInclude.Include.NON_NULL) //NOTE: despite name, also affects YAML encoding
public class MultiTypeBulkDataRules implements BulkDataRules {

    /**
     * map of file path templates to rules for matching files, where "path template" has the same
     * interpretation as in OpenAPI 3.0.0
     * see: https://swagger.io/specification/ , section "Path Templating"
     *
     * @see PathTemplateUtils for more details on interpretation
     *
     *
     * eg, /export/{week}/data_{shard}.csv -> ColumnarRules
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
