package co.worklytics.psoxy.storage.impl;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.io.StringWriter;
import java.io.Writer;
import java.util.Set;
import org.junit.jupiter.api.Test;
import com.avaulta.gateway.rules.ColumnarRules;
import co.worklytics.psoxy.Pseudonymizer;
import co.worklytics.psoxy.gateway.StorageEventRequest;

class ColumnarBulkDataSanitizerImplTest {

    @Test
    void emptyDelimiterDoesNotCrash() throws IOException {
        ColumnarRules rules = mock(ColumnarRules.class);
        when(rules.getDelimiter()).thenReturn("");
        ColumnarBulkDataSanitizerImpl sanitizer = new ColumnarBulkDataSanitizerImpl(rules);
        
        Reader reader = new StringReader("col1,col2\nval1,val2\n");
        Writer writer = new StringWriter();
        Pseudonymizer pseudonymizer = mock(Pseudonymizer.class);
        
        sanitizer.sanitize(
            StorageEventRequest.builder()
                .sourceBucketName("src")
                .sourceObjectPath("src")
                .destinationBucketName("dest")
                .destinationObjectPath("dest")
                .build(), 
            reader, writer, pseudonymizer);
        // Should not throw IllegalArgumentException from CSVFormat.Builder
    }
    @Test
    void determineMissingColumnsToPseudonymize() {

        ColumnarBulkDataSanitizerImpl columnarBulkDataSanitizer = new ColumnarBulkDataSanitizerImpl(mock(ColumnarRules.class));

        assertEmpty(columnarBulkDataSanitizer.determineMissingColumnsToPseudonymize(Set.of("employee_id"), Set.of("employee_id")));
        assertEmpty(columnarBulkDataSanitizer.determineMissingColumnsToPseudonymize(Set.of("employee_id"), Set.of("employee_id", "employee_name")));
        assertEmpty(columnarBulkDataSanitizer.determineMissingColumnsToPseudonymize(Set.of("employee_id"), Set.of("employee_name", "employee_id")));

        assertEmpty(columnarBulkDataSanitizer.determineMissingColumnsToPseudonymize(Set.of("EMPLOYEE_ID"), Set.of("employee_id")));
        assertEmpty(columnarBulkDataSanitizer.determineMissingColumnsToPseudonymize(Set.of("employee_Id"), Set.of("employee_id", "employee_name")));
        assertEmpty(columnarBulkDataSanitizer.determineMissingColumnsToPseudonymize(Set.of("employee_id"), Set.of("employee_name", "EMPLOYEE_ID")));

    }

    void assertEmpty(Set<String> list) {
        assertTrue(list.isEmpty(), "Not empty list");
    }
}
