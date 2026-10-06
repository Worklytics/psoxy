package com.avaulta.gateway.rules.augments;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GenMetadataThinkingLevelsTest {

    @Test
    void resolve_defaultsBlankToMinimal() {
        assertEquals(GenMetadataThinkingLevels.MINIMAL, GenMetadataThinkingLevels.resolve(null));
        assertEquals(GenMetadataThinkingLevels.MINIMAL, GenMetadataThinkingLevels.resolve(""));
        assertEquals(GenMetadataThinkingLevels.MINIMAL, GenMetadataThinkingLevels.resolve("  "));
    }

    @Test
    void resolve_normalizesCase() {
        assertEquals(GenMetadataThinkingLevels.MINIMAL, GenMetadataThinkingLevels.resolve("minimal"));
        assertEquals(GenMetadataThinkingLevels.LOW, GenMetadataThinkingLevels.resolve("Low"));
        assertEquals(GenMetadataThinkingLevels.MEDIUM, GenMetadataThinkingLevels.resolve("MEDIUM"));
        assertEquals(GenMetadataThinkingLevels.HIGH, GenMetadataThinkingLevels.resolve("high"));
    }

    @Test
    void resolve_unknownFallsBackToMinimal() {
        assertEquals(GenMetadataThinkingLevels.MINIMAL, GenMetadataThinkingLevels.resolve("off"));
    }
}
