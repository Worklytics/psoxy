package co.worklytics.psoxy.storage.impl;

import com.avaulta.gateway.rules.ColumnarRules;
import dagger.assisted.AssistedFactory;

@AssistedFactory
public interface ColumnarBulkDataSanitizerImplFactory {

    ColumnarBulkDataSanitizerImpl create(ColumnarRules columnarRules);
}
