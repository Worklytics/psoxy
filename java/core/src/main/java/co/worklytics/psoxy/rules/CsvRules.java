package co.worklytics.psoxy.rules;

import com.avaulta.gateway.rules.ColumnarRules;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;


@AllArgsConstructor //for builder
//@NoArgsConstructor //for Jackson
@SuperBuilder(toBuilder = true)
public class CsvRules extends ColumnarRules implements RuleSet {

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    @Override
    public String getDefaultScopeIdForSource() {
        return null;
    }
}
