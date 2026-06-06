package co.worklytics.psoxy.aws;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import org.apache.commons.lang3.JavaVersion;
import org.apache.commons.lang3.SystemUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockMakers;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.http.AbortableInputStream;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.S3Exception;

class S3ResourceServiceTest {

    public static boolean isAtLeastJava17() {
        return SystemUtils.isJavaVersionAtLeast(JavaVersion.JAVA_17);
    }

    S3Client client;

    @BeforeEach
    public void setup() {
        if (isAtLeastJava17()) {
            client = mock(S3Client.class, withSettings().mockMaker(MockMakers.SUBCLASS));
        } else {
            client = mock(S3Client.class);
        }
    }

    @Test
    void getResource_success() throws Exception {
        byte[] content = "hello world".getBytes(StandardCharsets.UTF_8);
        GetObjectResponse response = GetObjectResponse.builder().contentLength((long) content.length).build();
        ResponseInputStream<GetObjectResponse> responseInputStream = new ResponseInputStream<>(
            response,
            AbortableInputStream.create(new ByteArrayInputStream(content))
        );

        when(client.getObject(any(GetObjectRequest.class))).thenReturn(responseInputStream);

        S3ResourceService service = new S3ResourceService(client, "my-bucket", "my-prefix");
        Optional<InputStream> resultOpt = service.getResource("my-key");

        assertTrue(resultOpt.isPresent());
        try (InputStream is = resultOpt.get()) {
            String readContent = new String(is.readAllBytes(), StandardCharsets.UTF_8);
            assertEquals("hello world", readContent);
        }
    }

    @Test
    void getResource_notFound_noSuchKey() {
        when(client.getObject(any(GetObjectRequest.class)))
            .thenThrow(NoSuchKeyException.builder().message("No such key").build());

        S3ResourceService service = new S3ResourceService(client, "my-bucket", "my-prefix");
        Optional<InputStream> resultOpt = service.getResource("my-key");

        assertTrue(resultOpt.isEmpty());
    }

    @Test
    void getResource_notFound_s3Exception404() {
        S3Exception exception = (S3Exception) S3Exception.builder()
            .statusCode(404)
            .message("Not Found")
            .build();
        when(client.getObject(any(GetObjectRequest.class)))
            .thenThrow(exception);

        S3ResourceService service = new S3ResourceService(client, "my-bucket", "my-prefix");
        Optional<InputStream> resultOpt = service.getResource("my-key");

        assertTrue(resultOpt.isEmpty());
    }

    @Test
    void getResource_normalizesSecretStylePrefix() throws Exception {
        byte[] content = "hello".getBytes(StandardCharsets.UTF_8);
        GetObjectResponse response = GetObjectResponse.builder().contentLength((long) content.length).build();
        ResponseInputStream<GetObjectResponse> responseInputStream = new ResponseInputStream<>(
            response,
            AbortableInputStream.create(new ByteArrayInputStream(content))
        );
        when(client.getObject(any(GetObjectRequest.class))).thenReturn(responseInputStream);

        S3ResourceService service = new S3ResourceService(client, "my-bucket", "/psoxy-dev-erik_");
        Optional<InputStream> resultOpt = service.getResource("rules.yaml");

        assertTrue(resultOpt.isPresent());
        verify(client).getObject(GetObjectRequest.builder()
            .bucket("my-bucket")
            .key("psoxy-dev-erik/rules.yaml")
            .build());
    }

    @Test
    void getResource_accessDenied_rethrows() {
        S3Exception exception = (S3Exception) S3Exception.builder()
            .statusCode(403)
            .message("Access Denied")
            .build();
        when(client.getObject(any(GetObjectRequest.class)))
            .thenThrow(exception);

        S3ResourceService service = new S3ResourceService(client, "my-bucket", "my-prefix");

        assertThrows(S3Exception.class, () -> service.getResource("my-key"));
    }

    @Test
    void getResource_otherS3Exception_rethrows() {
        S3Exception exception = (S3Exception) S3Exception.builder()
            .statusCode(500)
            .message("Internal Error")
            .build();
        when(client.getObject(any(GetObjectRequest.class)))
            .thenThrow(exception);

        S3ResourceService service = new S3ResourceService(client, "my-bucket", "my-prefix");

        assertThrows(S3Exception.class, () -> service.getResource("my-key"));
    }

    @Test
    void getResource_invalidPaths() {
        S3ResourceService service = new S3ResourceService(client, "my-bucket", "my-prefix");
        assertThrows(IllegalArgumentException.class, () -> service.getResource(null));
        assertThrows(IllegalArgumentException.class, () -> service.getResource(""));
        assertThrows(IllegalArgumentException.class, () -> service.getResource("   "));
        assertThrows(IllegalArgumentException.class, () -> service.getResource("/absolute/path"));
        assertThrows(IllegalArgumentException.class, () -> service.getResource("test\0key"));
        assertThrows(IllegalArgumentException.class, () -> service.getResource("../traversal"));
    }
}
