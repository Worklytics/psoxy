
package com.avaulta.gateway.rules;

import java.util.Set;
import com.avaulta.gateway.pseudonyms.impl.UrlSafeTokenPseudonymEncoder;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.networknt.schema.ExecutionContext;
import com.networknt.schema.Format;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.ValidationMessage;
import com.networknt.schema.SpecVersion.VersionFlag;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import com.networknt.schema.JsonMetaSchema;

@RequiredArgsConstructor
public class JsonSchemaValidationUtils {


    final ObjectMapper objectMapper;
    final JsonMetaSchema metaSchema = JsonMetaSchema.builder(JsonMetaSchema.getV202012())
            .format(new PseudonymFormat()).build();
    final JsonSchemaFactory jsonSchemaFactory = JsonSchemaFactory.getInstance(VersionFlag.V202012,
            builder -> builder.metaSchema(metaSchema));

    @SneakyThrows
    public boolean validateJsonBySchema(String jsonString,
            com.avaulta.gateway.rules.JsonSchema schema) {
        JsonNode deserialized = objectMapper.readTree(jsonString);

        // TODO: cache this??? in practice, each instance is only going to have 3-4 of them. so why
        // the object conversion EVERY time?
        JsonNode schemaNode = objectMapper.valueToTree(schema);
        JsonSchema jsonSchema = jsonSchemaFactory.getSchema(schemaNode);

        Set<ValidationMessage> validationMessages = jsonSchema.validate(deserialized);
        return validationMessages.isEmpty();
    }
}


class PseudonymFormat implements Format {

    @Override
    public boolean matches(ExecutionContext executionContext, String value) {
        return UrlSafeTokenPseudonymEncoder.REVERSIBLE_PSEUDONYM_PATTERN.matcher(value).matches();
    }

    @Override
    public String getName() {
        return "pseudonym";
    }
}

