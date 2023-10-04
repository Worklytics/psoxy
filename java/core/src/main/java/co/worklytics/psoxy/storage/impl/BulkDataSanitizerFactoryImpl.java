package co.worklytics.psoxy.storage.impl;

import co.worklytics.psoxy.storage.BulkDataSanitizer;
import co.worklytics.psoxy.storage.BulkDataSanitizerFactory;
import com.avaulta.gateway.rules.BulkDataRules;
import com.avaulta.gateway.rules.ColumnarRules;
import com.avaulta.gateway.rules.RecordRules;
import lombok.NoArgsConstructor;
import lombok.NonNull;

import javax.inject.Inject;

@NoArgsConstructor(onConstructor_ = @Inject)
public class BulkDataSanitizerFactoryImpl implements BulkDataSanitizerFactory {

    @Inject
    ColumnarBulkDataSanitizerImpl columnarFileSanitizerImpl;
    @Inject
    RecordBulkDataSanitizerImplFactory recordBulkDataSanitizerImplFactory;


    @Override
    public BulkDataSanitizer get(@NonNull BulkDataRules rules) {
        if (rules instanceof ColumnarRules) {
            return columnarFileSanitizerImpl;
        } else if (rules instanceof RecordRules) {
            return recordBulkDataSanitizerImplFactory.create((RecordRules) rules);
        } else {
            throw new IllegalArgumentException("Unsupported rules type: " + rules.getClass().getName());
        }
    }
}
