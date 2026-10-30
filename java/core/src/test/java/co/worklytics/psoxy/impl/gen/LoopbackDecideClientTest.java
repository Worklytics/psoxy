package co.worklytics.psoxy.impl.gen;

import com.avaulta.gateway.rules.augments.GenMetadataAugmentException;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class LoopbackDecideClientTest {

    @Test
    void requireLoopback_rejectsNonLoopback() {
        assertThrows(GenMetadataAugmentException.class,
            () -> LoopbackDecideClient.requireLoopback(URI.create("https://127.0.0.1:8000/v1/systemone")));
        assertThrows(GenMetadataAugmentException.class,
            () -> LoopbackDecideClient.requireLoopback(URI.create("http://localhost:8000/v1/systemone")));
        assertThrows(GenMetadataAugmentException.class,
            () -> LoopbackDecideClient.requireLoopback(URI.create("http://example.com/v1/systemone")));
        assertThrows(GenMetadataAugmentException.class,
            () -> LoopbackDecideClient.requireLoopback(URI.create("http://127.0.0.1:8000/v1/decisions")));
        GenMetadataAugmentException ex = assertThrows(GenMetadataAugmentException.class,
            () -> new LoopbackDecideClient((uri, json, timeout) -> {
                throw new AssertionError("poster called");
            }).post(URI.create("http://10.0.0.1:8000/v1/systemone"), "{}", Duration.ofSeconds(1)));
        assertEquals(GenMetadataAugmentException.Code.UNAVAILABLE, ex.getCode());
    }

    @Test
    void post_sendsOnlyTheLoopbackEndpoint() {
        AtomicReference<URI> seen = new AtomicReference<>();
        LoopbackDecideClient client = new LoopbackDecideClient((uri, json, timeout) -> {
            seen.set(uri);
            return "{\"ok\":true}";
        });
        assertEquals("{\"ok\":true}", client.post("{}", Duration.ofSeconds(1)));
        assertEquals(LoopbackDecideClient.ENDPOINT, seen.get());
    }

    @Test
    void post_non200IsEmpty() {
        LoopbackDecideClient client = new LoopbackDecideClient((uri, json, timeout) -> null);
        assertNull(client.post("{}", Duration.ofSeconds(1)));
    }
}
