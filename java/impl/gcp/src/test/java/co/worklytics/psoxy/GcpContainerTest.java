package co.worklytics.psoxy;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

//. wip; fix this, but due to config validation done in injection, many errors;
// tbh, much of thate should be refactored to do the validation lazily, allowing us to surface a more readable error through the proxy response / logs
@Disabled
class GcpContainerTest {

    @Test
    void fileEventHandle_singletons() {

        GcpContainer gcpContainer = DaggerGcpContainer.create();

        GcsFileEventHandler instance1 = gcpContainer.gcsFileEventHandler();
        GcsFileEventHandler instance2 = gcpContainer.gcsFileEventHandler();

        //expect singletons
        assertSame(instance1, instance2);

    }

    @Test
    void httpRequestHandler_singletons() {

        GcpContainer gcpContainer = DaggerGcpContainer.create();

        HttpRequestHandler instance1 = gcpContainer.httpRequestHandler();
        HttpRequestHandler instance2 = gcpContainer.httpRequestHandler();

        //expect singletons
        assertSame(instance1, instance2);
    }

}
