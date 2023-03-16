package co.worklytics.psoxy.storage;

public interface BulkDataSanitizerFactory {


    /**
     * @return the specific implementation of BulkDataSanitizer based on the file type inferred from
     * the file name.
     */
    BulkDataSanitizer get(String fileName);
}
