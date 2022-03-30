package co.worklytics.psoxy.storage.impl;

import co.worklytics.psoxy.storage.FileHandler;
import co.worklytics.psoxy.storage.FileHandlerFactory;
import lombok.NoArgsConstructor;
import lombok.NonNull;

import javax.inject.Inject;

@NoArgsConstructor(onConstructor_ = @Inject)
public class FileHandlerFactoryImpl implements FileHandlerFactory {

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
