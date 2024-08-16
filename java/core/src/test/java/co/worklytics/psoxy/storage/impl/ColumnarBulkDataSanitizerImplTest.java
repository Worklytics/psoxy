package co.worklytics.psoxy.storage.impl;

import com.avaulta.gateway.rules.ColumnarRules;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

class ColumnarBulkDataSanitizerImplTest {


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
