package co.worklytics.psoxy.impl;

import static org.junit.jupiter.api.Assertions.*;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import co.worklytics.psoxy.gateway.impl.LocalFileResourceService;
import co.worklytics.psoxy.gateway.impl.NoOpResourceService;

class OpenNlpModelResourceProviderTest {

    @Test
    void openModel_prefersInstanceResourceOverShared(@TempDir Path tempDir) throws Exception {
        Path opennlpDir = tempDir.resolve("opennlp");
        Files.createDirectories(opennlpDir);
        Files.writeString(opennlpDir.resolve("en-sent.bin"), "instance-model");

        LocalFileResourceService instanceService = new LocalFileResourceService(tempDir.toString());
        OpenNlpModelResourceProvider provider = new OpenNlpModelResourceProvider(
            instanceService,
            new NoOpResourceService());

        try (InputStream is = provider.openModel("en-sent.bin").orElseThrow()) {
            assertEquals("instance-model", new String(is.readAllBytes()));
        }
    }

    @Test
    void openModel_fallsBackToSharedRemote(@TempDir Path instanceDir, @TempDir Path sharedDir) throws Exception {
        Path sharedOpennlp = sharedDir.resolve("opennlp");
        Files.createDirectories(sharedOpennlp);
        Files.writeString(sharedOpennlp.resolve("en-chunker.bin"), "shared-model");

        OpenNlpModelResourceProvider provider = new OpenNlpModelResourceProvider(
            new LocalFileResourceService(instanceDir.toString()),
            new LocalFileResourceService(sharedDir.toString()));

        try (InputStream is = provider.openModel("en-chunker.bin").orElseThrow()) {
            assertEquals("shared-model", new String(is.readAllBytes()));
        }
    }

    @Test
    void openModel_returnsEmptyWhenNotFound() {
        OpenNlpModelResourceProvider provider = new OpenNlpModelResourceProvider(
            new NoOpResourceService(),
            new NoOpResourceService());

        Optional<InputStream> result = provider.openModel("en-sent.bin");
        assertTrue(result.isEmpty());
    }

    @Test
    void open_rejectsPathsOutsideOpennlpPrefix() {
        OpenNlpModelResourceProvider provider = new OpenNlpModelResourceProvider(
            new NoOpResourceService(),
            new NoOpResourceService());

        assertThrows(IllegalArgumentException.class, () -> provider.open("rules.yaml"));
    }
}
