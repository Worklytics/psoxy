package co.worklytics.psoxy;

public interface FileHandlerStrategy {
    FileHandler get(String fileName);
}
