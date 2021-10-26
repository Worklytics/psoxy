package co.worklytics.psoxy;

import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.Set;

@Getter
@NoArgsConstructor //for Jackson
public class Config {

    String defaultScopeId;

    Set<String> columnsToPseudonymize;

    String pseudonymizationSalt;
}
