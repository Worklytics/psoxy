package co.worklytics.psoxy.gateway.output;

import co.worklytics.psoxy.gateway.ProcessedContent;
import co.worklytics.psoxy.gateway.impl.ApiDataRequestHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OutputToApiDataSideOutputAdapterTest {

    ApiDataOutputUtils apiDataOutputUtils;
    Output wrappedOutput;
    OutputToApiDataSideOutputAdapter adapter;

    @BeforeEach
    void setup() {
        apiDataOutputUtils = mock(ApiDataOutputUtils.class);
        wrappedOutput = mock(Output.class);
        adapter = new OutputToApiDataSideOutputAdapter(wrappedOutput);
        adapter.apiDataOutputUtils = apiDataOutputUtils;
    }

    @Test
    void writeSanitized_stripsUnsanitizedRequestMetadataBeforeWriting() throws Exception {
        ApiDataRequestHandler.ProcessingContext processingContext =
            ApiDataRequestHandler.ProcessingContext.builder()
                .requestReceivedAt(Instant.parse("2024-10-01T10:15:30Z"))
                .requestId("123e4567-e89b-12d3-a456-426614174000")
                .build();

        Map<String, String> metadata = new HashMap<>();
        metadata.put(ApiDataOutputUtils.OutputObjectMetadata.REQUEST_BODY.name(),
            Base64.getEncoder().encodeToString("secret".getBytes(StandardCharsets.UTF_8)));
        metadata.put(ApiDataOutputUtils.OutputObjectMetadata.QUERY_STRING.name(), "q=secret");
        metadata.put("Rules-SHA", "abc123");
        ProcessedContent sanitizedContent = ProcessedContent.builder()
            .content("{}".getBytes(StandardCharsets.UTF_8))
            .metadata(metadata)
            .build();

        ProcessedContent metadataStripped = ProcessedContent.builder()
            .content("{}".getBytes(StandardCharsets.UTF_8))
            .metadata(Map.of("Rules-SHA", "abc123"))
            .build();

        when(apiDataOutputUtils.buildSanitizedOutputKey(processingContext))
            .thenReturn("20241001/123e4567-e89b-12d3-a456-426614174000");
        when(apiDataOutputUtils.withoutUnsanitizedRequestMetadata(sanitizedContent))
            .thenReturn(metadataStripped);

        adapter.writeSanitized(sanitizedContent, processingContext);

        verify(apiDataOutputUtils).withoutUnsanitizedRequestMetadata(sanitizedContent);
        verify(wrappedOutput).write(
            eq("20241001/123e4567-e89b-12d3-a456-426614174000"),
            eq(metadataStripped));
        assertFalse(metadataStripped.getMetadata()
            .containsKey(ApiDataOutputUtils.OutputObjectMetadata.REQUEST_BODY.name()));
    }
}
