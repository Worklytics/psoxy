package co.worklytics.psoxy.storage.impl;

import co.worklytics.psoxy.utils.ProcessingBuffer;
import com.avaulta.gateway.rules.ColumnarRules;
import lombok.SneakyThrows;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.BufferedReader;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

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




    @ParameterizedTest
    @MethodSource("headerLinesAndEndings")
    void firstLine(String headerLine, String expectedEndOfLine) {
        ColumnarBulkDataSanitizerImpl columnarBulkDataSanitizer = new ColumnarBulkDataSanitizerImpl(mock(ColumnarRules.class));
        BufferedReader reader = new BufferedReader(new StringReader(headerLine));
        ColumnarBulkDataSanitizerImpl.ParsedFirstLine parsedFirstLine = columnarBulkDataSanitizer.parseFirstLine(reader);
        assertEquals(expectedEndOfLine, parsedFirstLine.getEndOfLine());
    }

    static Stream<Arguments> headerLinesAndEndings() {
        return Stream.of(
            Arguments.of("employee_id,employee_name\n", "\n"),
            Arguments.of("employee_id,employee_name \n", "\n"),
            Arguments.of("employee_id,employee_name\r\n", "\r\n"),
            Arguments.of("employee_id,employee_name \r\n", "\r\n"),
            Arguments.of("employee_id,employee_name\r", "\r"),
            Arguments.of("employee_id,employee_name \r", "\r")
        );
    }

    @SneakyThrows
    @Test
    void processingSkipsMalformedRows() {
        ColumnarBulkDataSanitizerImpl columnarBulkDataSanitizer = new ColumnarBulkDataSanitizerImpl(ColumnarRules.builder()
            .columnToPseudonymize("employee_id")
            .build());

        String MALFORMED_CSV = "employee_id,employee_name\n" +
            "12345,John Doe\n" +
            "malformed_row_without_comma,\"missing_employee_name\n" +
            "67890,Jane Doe\n";


        CSVFormat csvFormat = CSVFormat.DEFAULT.builder()
            .setHeader("employee_id", "employee_name")
            .setSkipHeaderRecord(false)
            .build();

        List<ColumnarBulkDataSanitizerImpl.ProcessedRecord> processedRecords = new ArrayList<>();
        BufferedReader reader = new BufferedReader(new StringReader(MALFORMED_CSV));
        reader.readLine(); // skip header

        columnarBulkDataSanitizer.processRecords(List.of("employee_id,employee_name"),
            csvFormat,
            Collections.emptyMap(),
            reader,
            new ProcessingBuffer<>(1, r -> processedRecords.addAll(r)) {}
        );

        assertEquals(2, processedRecords.size());

    }


    void assertEmpty(Set<String> list) {
        assertTrue(list.isEmpty(), "Not empty list");
    }
}
