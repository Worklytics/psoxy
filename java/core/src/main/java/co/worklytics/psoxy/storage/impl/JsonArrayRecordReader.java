package co.worklytics.psoxy.storage.impl;

import java.io.IOException;
import java.io.Reader;
import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonpath.Configuration;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
class JsonArrayRecordReader implements RecordReader {

    final Reader reader;
    final ObjectMapper objectMapper;
    final Configuration jsonConfiguration;

    JsonParser parser;

    @Override
    public Object readRecord() throws IOException {
        if (parser == null) {
            JsonFactory factory = objectMapper.getFactory();
            parser = factory.createParser(reader);
            if (parser.nextToken() != JsonToken.START_ARRAY) {
                // Should we be strict here? Requirement said "strict checking".
                // But if it's already past it?
                // Throw exception as per requirement.
                throw new IllegalArgumentException("Input is not a JSON array");
            }
        }

        while (parser.nextToken() != JsonToken.END_ARRAY && parser.currentToken() != null) {
            // Found a token inside array
            // Read object
            Object node = objectMapper.readValue(parser, Object.class);
            // Convert to Jayway compatible object (Map/List) via JSON string intermediate
            // This sustains compatibility with existing logic which cleanses based on Jayway structures
            return jsonConfiguration.jsonProvider().parse(
                objectMapper.writeValueAsString(node)
            );
        }
        return null; // End of array or stream
    }

    @Override
    public void close() throws IOException {
        if (parser != null) {
            parser.close();
        } else {
            reader.close();
        }
    }
}
