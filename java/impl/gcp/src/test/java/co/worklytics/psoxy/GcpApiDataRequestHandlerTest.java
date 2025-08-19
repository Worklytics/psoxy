package co.worklytics.psoxy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.Test;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.SneakyThrows;

public class GcpApiDataRequestHandlerTest {


    @SneakyThrows
    @Test
    public void testPubSubMessageDeserialization() {

        // q: correct?
        // API docs show camelCase: https://cloud.google.com/pubsub/docs/reference/rest/v1/PubsubMessage 
        // but debugging logs indicate snake_case
        String json = """
{
        "message": {
            "data": "eyJwYXJhbXMiOiB7InBhcmFtMSI6ICJ2YWx1ZTEiLCAicGFyYW0yIjogInZhbHVlMiJ9fQ==",
            "attributes": {
                "processingContext": "eyJwYXJhbXMiOiB7InBhcmFtMSI6ICJ2YWx1ZTEiLCAicGFyYW0yIjogInZhbHVlMiJ9fQ=="
            },
            "message_id": "1234567890",
            "publish_time": "2021-01-01T00:00:00Z"
        },
        "subscription": "projects/my-project/subscriptions/my-subscription"
}
        """;

        ObjectMapper objectMapper = new ObjectMapper();
        GcpApiDataRequestHandler.PubSubPushBody pubSubPushBody = objectMapper.readerFor(GcpApiDataRequestHandler.PubSubPushBody.class)
            .readValue(json);

        assertEquals("eyJwYXJhbXMiOiB7InBhcmFtMSI6ICJ2YWx1ZTEiLCAicGFyYW0yIjogInZhbHVlMiJ9fQ==", pubSubPushBody.message.data);
        assertEquals("eyJwYXJhbXMiOiB7InBhcmFtMSI6ICJ2YWx1ZTEiLCAicGFyYW0yIjogInZhbHVlMiJ9fQ==", pubSubPushBody.message.attributes.get("processingContext"));
        assertEquals("1234567890", pubSubPushBody.message.messageId);
        assertEquals("2021-01-01T00:00:00Z", pubSubPushBody.message.publishTime);
        assertEquals("projects/my-project/subscriptions/my-subscription", pubSubPushBody.subscription);
    }
}
