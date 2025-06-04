package com.avaulta.gateway.rules;

import com.avaulta.gateway.pseudonyms.PseudonymEncoder;
import com.avaulta.gateway.rules.transforms.Transform;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;
import lombok.SneakyThrows;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class WebhookCollectionRulesTest {


    ObjectMapper objectMapper = new YAMLMapper();


    final String SIMPLE_YAML = """
---
endpoints:
- jwtClaimsToVerify:
    sub:
      payloadContents:
      - "$.actor.id"
  transforms:
  - !<pseudonymize>
    encoding: "URL_SAFE_TOKEN"
""";

    @SneakyThrows
    @Test
    public void toYaml() {
        WebhookCollectionRules rules = WebhookCollectionRules.builder()
            .endpoint(WebhookCollectionRules.WebhookEndpoint.builder()
                    .jwtClaimsToVerify(Map.of("sub", WebhookCollectionRules.JwtClaimSpec.builder().payloadContent("$.actor.id").build()))
                .transform(Transform.Pseudonymize.ofPaths("$.actor.id").withEncoding(PseudonymEncoder.Implementations.URL_SAFE_TOKEN))
                .build())
            .build();

        assertEquals(SIMPLE_YAML, objectMapper.writeValueAsString(rules));
    }


    final String YAML_EXAMPLE = """
---
jwtClaimsToVerify:
  sub:
    queryParam: "userId"
    payloadContents:
    - "$.user_id"
endpoints:
- jwtClaimsToVerify:
    sub:
      queryParam: "userId"
      payloadContents:
      - "$.user_id"
  transforms:
  - !<pseudonymize>
    jsonPaths:
    - "$.employeeEmail"
    - "$.managerEmail"
    encoding: "URL_SAFE_TOKEN"
        """;

    @SneakyThrows
    @Test
    public void yamlRoundTrip() {

        WebhookCollectionRules fromYaml = objectMapper.readerFor(WebhookCollectionRules.class).readValue(YAML_EXAMPLE);


        String yaml = objectMapper.writeValueAsString(fromYaml);
        assertEquals(YAML_EXAMPLE, yaml);
    }

}
