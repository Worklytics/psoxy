package co.worklytics.psoxy.storage;

public interface FileHandlerFactory {
    /**
     * Return the specific implementation of FileHandler based on the filename
     */
    FileHandler get(String fileName);
}
