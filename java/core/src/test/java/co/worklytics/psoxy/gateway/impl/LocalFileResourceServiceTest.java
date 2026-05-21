package co.worklytics.psoxy.gateway.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class LocalFileResourceServiceTest {

    @TempDir
    Path tempDir;

    LocalFileResourceService service;
    Path testFile;
    Path subDirFile;

    @BeforeEach
    void setUp() throws Exception {
        service = new LocalFileResourceService(tempDir.toString());

        // Create a test resource file
        testFile = tempDir.resolve("test-resource.txt");
        Files.writeString(testFile, "hello world", StandardCharsets.UTF_8);

        // Create a subdirectory and a resource in it
        Path subDir = tempDir.resolve("subdir");
        Files.createDirectory(subDir);
        subDirFile = subDir.resolve("sub-resource.txt");
        Files.writeString(subDirFile, "hello from subdir", StandardCharsets.UTF_8);
    }

    @Test
    void testGetResource_ValidPath() throws Exception {
        Optional<InputStream> isOpt = service.getResource("test-resource.txt");
        assertTrue(isOpt.isPresent());
        try (InputStream is = isOpt.get()) {
            String content = new String(is.readAllBytes(), StandardCharsets.UTF_8);
            assertEquals("hello world", content);
        }
    }

    @Test
    void testGetResource_ValidSubdirPath() throws Exception {
        Optional<InputStream> isOpt = service.getResource("subdir/sub-resource.txt");
        assertTrue(isOpt.isPresent());
        try (InputStream is = isOpt.get()) {
            String content = new String(is.readAllBytes(), StandardCharsets.UTF_8);
            assertEquals("hello from subdir", content);
        }
    }

    @Test
    void testExists_ValidPath() {
        assertTrue(service.exists("test-resource.txt"));
        assertTrue(service.exists("subdir/sub-resource.txt"));
        assertFalse(service.exists("non-existent.txt"));
    }

    @Test
    void testGetResource_AbsolutePathsRejected() {
        // Try accessing an absolute path (even if it's the testFile path)
        Optional<InputStream> isOpt = service.getResource(testFile.toAbsolutePath().toString());
        assertFalse(isOpt.isPresent());

        // Test with a generic Unix absolute path
        assertFalse(service.getResource("/etc/passwd").isPresent());
    }

    @Test
    void testExists_AbsolutePathsRejected() {
        assertFalse(service.exists(testFile.toAbsolutePath().toString()));
        assertFalse(service.exists("/etc/passwd"));
    }

    @Test
    void testGetResource_PathTraversalRejected() {
        // Attempt traversal out of base dir
        assertFalse(service.getResource("../passwd").isPresent());
        assertFalse(service.getResource("subdir/../../passwd").isPresent());
        assertFalse(service.getResource("subdir/..").isPresent());
    }

    @Test
    void testExists_PathTraversalRejected() {
        assertFalse(service.exists("../passwd"));
        assertFalse(service.exists("subdir/../../passwd"));
    }

    @Test
    void testGetResource_WeirdPathsRejected() {
        assertFalse(service.getResource(null).isPresent());
        assertFalse(service.getResource("").isPresent());
        assertFalse(service.getResource("   ").isPresent());
        assertFalse(service.getResource("test\0resource.txt").isPresent());
        assertFalse(service.getResource("./test-resource.txt").isPresent());
        assertFalse(service.getResource("subdir/../test-resource.txt").isPresent());
    }
}
