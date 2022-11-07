package co.worklytics.psoxy.rules;

import com.avaulta.gateway.rules.ColumnarRules;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;


@AllArgsConstructor //for builder
//@NoArgsConstructor //for Jackson
@SuperBuilder
public class CsvRules extends ColumnarRules implements RuleSet {

    @Override
    public String getDefaultScopeIdForSource() {
        return null;
    }
}
