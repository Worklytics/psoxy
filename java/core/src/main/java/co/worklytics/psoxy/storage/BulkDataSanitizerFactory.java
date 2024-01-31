package co.worklytics.psoxy.storage;

import com.avaulta.gateway.rules.BulkDataRules;

public interface BulkDataSanitizerFactory {


    /**
     * @return the specific implementation of BulkDataSanitizer based on the file type inferred from
     * the file name.
     */
    BulkDataSanitizer get(BulkDataRules rules);
}
