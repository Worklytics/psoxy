package co.worklytics.psoxy.rules;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import lombok.*;
import lombok.extern.java.Log;
import org.apache.commons.lang3.tuple.Pair;

import java.util.ArrayList;
import java.util.List;

@Builder(toBuilder = true)
@Log
@AllArgsConstructor //for builder
@NoArgsConstructor //for Jackson
@Getter
@EqualsAndHashCode
@JsonPropertyOrder(alphabetic = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class CsvRules implements RuleSet {

    private static final long serialVersionUID = 1L;

    @NonNull
    @Singular(value = "columnToPseudonymize")
    List<String> columnsToPseudonymize = new ArrayList<>();

    @NonNull
    @Singular(value = "columnToRedact")
    List<String> columnsToRedact = new ArrayList<>();

    /**
     * scopeId to set for any identifiers parsed from source that aren't email addresses
     *
     * NOTE: can be overridden by config, in case you're connecting to an on-prem / private instance
     * of the source and you don't want it's identifiers to be treated as under the default scope
     */
    @Deprecated
    @Getter
    String defaultScopeIdForSource;
}
