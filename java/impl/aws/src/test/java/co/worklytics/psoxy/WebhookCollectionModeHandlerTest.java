package co.worklytics.psoxy;

import com.amazonaws.services.lambda.runtime.events.SQSEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.MapperFeature;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.events.APIGatewayV2HTTPEvent;
import co.worklytics.psoxy.gateway.HttpEventResponse;
import co.worklytics.psoxy.gateway.impl.BatchMergeHandler;
import co.worklytics.psoxy.gateway.impl.InboundWebhookHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class WebhookCollectionModeHandlerTest {

    static final String EXAMPLE_SQS_BATCH = """
{
  "Records": [
    {
      "messageId": "1a2b3c4d-5678-90ab-cdef-EXAMPLE11111",
      "receiptHandle": "AQEB1234...example-receipt-handle",
      "body": "{\\"event\\":\\"user_signup\\",\\"user_id\\":123}",
      "attributes": {
        "ApproximateReceiveCount": "1",
        "SentTimestamp": "1629861373000",
        "SenderId": "AIDAEXAMPLEID",
        "ApproximateFirstReceiveTimestamp": "1629861374000"
      },
      "messageAttributes": {
        "eventType": {
          "stringValue": "user_signup",
          "dataType": "String"
        }
      },
      "md5OfBody": "098f6bcd4621d373cade4e832627b4f6",
      "eventSource": "aws:sqs",
      "eventSourceARN": "arn:aws:sqs:us-east-1:123456789012:example-queue",
      "awsRegion": "us-east-1"
    },
    {
      "messageId": "9z8y7x6w-4321-vuts-rqpo-EXAMPLE22222",
      "receiptHandle": "AQEB5678...example-receipt-handle",
      "body": "{\\"event\\":\\"user_login\\",\\"user_id\\":456}",
      "attributes": {
        "ApproximateReceiveCount": "1",
        "SentTimestamp": "1629861383000",
        "SenderId": "AIDAEXAMPLEID",
        "ApproximateFirstReceiveTimestamp": "1629861384000"
      },
      "messageAttributes": {},
      "md5OfBody": "5d41402abc4b2a76b9719d911017c592",
      "eventSource": "aws:sqs",
      "eventSourceARN": "arn:aws:sqs:us-east-1:123456789012:example-queue",
      "awsRegion": "us-east-1"
    }
  ]
}
""";


    @Test
    void deserializeSqsBatch() {
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.configure(MapperFeature.ACCEPT_CASE_INSENSITIVE_PROPERTIES, true);
        try {
            SQSEvent event = objectMapper.readValue(EXAMPLE_SQS_BATCH, SQSEvent.class);
            assertNotNull(event);
            assertEquals(2, event.getRecords().size());
            assertEquals("1a2b3c4d-5678-90ab-cdef-EXAMPLE11111", event.getRecords().get(0).getMessageId());
            assertEquals("9z8y7x6w-4321-vuts-rqpo-EXAMPLE22222", event.getRecords().get(1).getMessageId());
        } catch (Exception e) {
            fail("Deserialization of SQS batch failed: " + e.getMessage());
        }
    }

    @Test
    void readSQSEvent() {
        new WebhookCollectionModeHandler();

    }
}
