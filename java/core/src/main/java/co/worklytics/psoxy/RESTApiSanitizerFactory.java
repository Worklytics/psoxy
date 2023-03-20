package co.worklytics.psoxy;

import co.worklytics.psoxy.impl.RESTApiSanitizerImpl;

import co.worklytics.psoxy.rules.RESTRules;
import dagger.assisted.AssistedFactory;

@AssistedFactory
public interface RESTApiSanitizerFactory {

    RESTApiSanitizerImpl create(RESTRules rules, Pseudonymizer pseudonymizer);

}
