package co.worklytics.psoxy.gateway.output;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.*;

class OutputLocationImplTest {

    @CsvSource({
        "https://sqs.us-east-1.amazonaws.com/874171213677/llm-portal-sanitized-webhooks-to-batch,sqs",
        "s3://my-bucket/prefix/,s3",
        "gs://my-bucket/prefix/,gcs",
    })
    @ParameterizedTest
    void of(String uri, String expectedKind) {

        OutputLocationImpl location = OutputLocationImpl.of(uri);
        assertEquals(expectedKind, location.getKind().name().toLowerCase());
        assertEquals(uri, location.getUri());
    }
}
