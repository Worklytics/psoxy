package co.worklytics.psoxy;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.google.cloud.ReadChannel;
import com.google.cloud.storage.Blob;
import com.google.cloud.storage.BlobId;
import com.google.cloud.storage.Storage;
import com.google.cloud.storage.StorageException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class GcsResourceServiceTest {

    @Mock
    Storage storage;

    @Mock
    Blob blob;

    @Mock
    ReadChannel readChannel;

    GcsResourceService service;

    @BeforeEach
    void setUp() {
        service = new GcsResourceService(storage, "my-bucket", "my-prefix");
    }

    @Test
    void getResource_success() throws Exception {
        when(storage.get(BlobId.of("my-bucket", "my-prefix/my-key"))).thenReturn(blob);
        when(blob.reader()).thenReturn(readChannel);
        when(readChannel.read(any(ByteBuffer.class))).thenReturn(-1);

        Optional<InputStream> resultOpt = service.getResource("my-key");

        assertTrue(resultOpt.isPresent());
        try (InputStream is = resultOpt.get()) {
            assertEquals(-1, is.read());
        }
    }

    @Test
    void getResource_notFound_blobNull() {
        when(storage.get(BlobId.of("my-bucket", "my-prefix/my-key"))).thenReturn(null);

        Optional<InputStream> resultOpt = service.getResource("my-key");

        assertTrue(resultOpt.isEmpty());
    }

    @Test
    void getResource_notFound_storageException404() {
        StorageException exception = new StorageException(404, "Not Found");
        when(storage.get(BlobId.of("my-bucket", "my-prefix/my-key"))).thenThrow(exception);

        Optional<InputStream> resultOpt = service.getResource("my-key");

        assertTrue(resultOpt.isEmpty());
    }

    @Test
    void getResource_otherStorageException_rethrows() {
        StorageException exception = new StorageException(403, "Access Denied");
        when(storage.get(BlobId.of("my-bucket", "my-prefix/my-key"))).thenThrow(exception);

        assertThrows(StorageException.class, () -> service.getResource("my-key"));
    }

    @Test
    void getResource_invalidPaths() {
        assertThrows(IllegalArgumentException.class, () -> service.getResource(null));
        assertThrows(IllegalArgumentException.class, () -> service.getResource(""));
        assertThrows(IllegalArgumentException.class, () -> service.getResource("   "));
        assertThrows(IllegalArgumentException.class, () -> service.getResource("/absolute/path"));
        assertThrows(IllegalArgumentException.class, () -> service.getResource("test\0key"));
        assertThrows(IllegalArgumentException.class, () -> service.getResource("../traversal"));
    }
}
