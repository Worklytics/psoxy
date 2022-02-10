package co.worklytics.psoxy.impl;

import co.worklytics.psoxy.FileHandler;
import co.worklytics.psoxy.FileHandlerStrategy;
import lombok.NoArgsConstructor;
import lombok.NonNull;

import javax.inject.Inject;

@NoArgsConstructor(onConstructor_ = @Inject)
public class FileHandlerStrategyImpl implements FileHandlerStrategy {

    @Inject
    CSVFileHandler csvFileHandler;

    @Override
    public FileHandler get(@NonNull String fileName) {
        if (fileName.endsWith(".csv")) {
            return csvFileHandler;
        } else {
            throw new IllegalStateException(String.format("Filename %s not supported!", fileName));
        }
    }
}
