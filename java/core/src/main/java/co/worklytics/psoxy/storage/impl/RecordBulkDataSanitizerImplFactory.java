package co.worklytics.psoxy.storage.impl;

import com.avaulta.gateway.rules.RecordRules;
import dagger.assisted.AssistedFactory;

@AssistedFactory
public interface RecordBulkDataSanitizerImplFactory {

    RecordBulkDataSanitizerImpl create(RecordRules rules);
}
