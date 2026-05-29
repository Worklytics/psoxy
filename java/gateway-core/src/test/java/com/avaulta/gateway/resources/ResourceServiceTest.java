package com.avaulta.gateway.resources;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class ResourceServiceTest {

    @Test
    void normalizeObjectKeyPrefix_secretStyleToHierarchy() {
        assertEquals("psoxy-dev-erik/", ResourceService.normalizeObjectKeyPrefix("/psoxy-dev-erik_"));
        assertEquals("psoxy-dev-erik/", ResourceService.normalizeObjectKeyPrefix("psoxy-dev-erik_"));
        assertEquals("psoxy-dev-erik/GCAL/", ResourceService.normalizeObjectKeyPrefix("psoxy-dev-erik/GCAL/"));
        assertEquals("", ResourceService.normalizeObjectKeyPrefix(""));
        assertEquals("", ResourceService.normalizeObjectKeyPrefix(null));
    }
}
