package co.worklytics.psoxy.storage.impl;

import co.worklytics.psoxy.storage.BulkDataSanitizer;
import co.worklytics.psoxy.storage.BulkDataSanitizerFactory;
import lombok.NoArgsConstructor;
import lombok.NonNull;

import javax.inject.Inject;

@NoArgsConstructor(onConstructor_ = @Inject)
public class BulkDataSanitizerFactoryImpl implements BulkDataSanitizerFactory {

    @Inject
    ColumnarBulkDataSanitizerImpl columnarFileSanitizerImpl;

    @Override
    public BulkDataSanitizer get(@NonNull String fileName) {
        if (fileName.endsWith(".csv")) {
            return columnarFileSanitizerImpl;
        } else {
            throw new IllegalStateException(String.format("Filename %s not supported!", fileName));
        }
    }
}
