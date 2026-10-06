package co.worklytics.psoxy;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.cloud.storage.BlobId;
import com.google.cloud.storage.BlobInfo;
import com.google.cloud.storage.Storage;
import com.google.cloud.storage.StorageException;
import co.worklytics.psoxy.storage.StorageHandler;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class GcsFileEventHandlerTest {

    @Mock
    StorageHandler storageHandler;

    @Mock
    Storage storage;

    static final BlobId DEST = BlobId.of("out-bucket", "sanitized/file.csv");

    GcsFileEventHandler handler() {
        return new GcsFileEventHandler(storageHandler, () -> storage);
    }

    @Test
    void patch_skipsWhenNoTokenMetadata() {
        handler().patchDestinationMetadata(storage, DEST, Map.of("psoxy-instance-id", "x"));
        verify(storage, never()).update(any(BlobInfo.class));
    }

    @Test
    void patch_swallows403() {
        when(storage.update(any(BlobInfo.class)))
            .thenThrow(new StorageException(403, "Access Denied"));
        Map<String, String> metadata = Map.of(
            StorageHandler.BulkMetaData.GEN_METADATA_CALLS.getMetaDataKey(), "2");
        assertDoesNotThrow(() -> handler().patchDestinationMetadata(storage, DEST, metadata));
        verify(storage).update(any(BlobInfo.class));
    }

    @Test
    void patch_rethrowsNon403() {
        when(storage.update(any(BlobInfo.class)))
            .thenThrow(new StorageException(500, "boom"));
        Map<String, String> metadata = Map.of(
            StorageHandler.BulkMetaData.GEN_METADATA_CALLS.getMetaDataKey(), "1");
        assertThrows(StorageException.class,
            () -> handler().patchDestinationMetadata(storage, DEST, metadata));
    }
}
