package co.worklytics.psoxy.impl.gen;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EmbeddedDecideRequestTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final EmbeddedDecideRequest request = new EmbeddedDecideRequest(objectMapper);

    @Test
    void toJson_preservesClassOrderAndNullDescriptions() throws Exception {
        String json = request.toJson(
            "Classify the input into exactly one class.",
            List.of("Email Drafting", "Research and Ideation", "Uncategorized", "Excluded"),
            "{\"body\":\"draft the renewal\"}");
        JsonNode root = objectMapper.readTree(json);
        assertEquals("{\"body\":\"draft the renewal\"}", root.get("state").asText());
        JsonNode question = root.get("questions").get("label");
        assertEquals("choice", question.get("type").asText());
        assertEquals("Classify the input into exactly one class.", question.get("instructions").asText());
        List<String> names = new ArrayList<>();
        question.get("criteria").fieldNames().forEachRemaining(names::add);
        assertEquals(
            List.of("Email Drafting", "Research and Ideation", "Uncategorized", "Excluded"),
            names);
        for (String name : names) {
            assertTrue(question.get("criteria").get(name).isNull());
        }
        assertTrue(root.get("questions").size() == 1);
    }

    @Test
    void toJson_encodesSessionAudienceAsOneChoice() throws Exception {
        String json = request.toJson(
            "Who is the AI session output for? S self, T own team, B broad internal, "
                + "L leadership or board, C customer, P prospect, V partner or vendor, U public.",
            List.of("S", "T", "B", "L", "C", "P", "V", "U"),
            "[size: 70 chars, 1 turn] Remind the team the timesheets are due Friday");
        JsonNode criteria = objectMapper.readTree(json)
            .get("questions").get("label").get("criteria");
        List<String> names = new ArrayList<>();
        criteria.fieldNames().forEachRemaining(names::add);
        assertEquals(List.of("S", "T", "B", "L", "C", "P", "V", "U"), names);
        assertTrue(criteria.get("C").isNull());
    }
}
