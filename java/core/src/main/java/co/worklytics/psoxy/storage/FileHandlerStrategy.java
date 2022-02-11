package co.worklytics.psoxy.storage;

import co.worklytics.psoxy.storage.FileHandler;

public interface FileHandlerStrategy {
    FileHandler get(String fileName);
}
