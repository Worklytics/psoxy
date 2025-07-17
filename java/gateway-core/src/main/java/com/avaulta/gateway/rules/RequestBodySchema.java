package com.avaulta.gateway.rules;

import java.util.Map;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * variation on OpenAPI 3.0, a body schema for a requests.
 */
@Builder(toBuilder = true)
@AllArgsConstructor // for builder
@NoArgsConstructor // for Jackson
@Data
public class RequestBodySchema {

    /**
     * whether the request body is required (default false)
     */
    Boolean required;


    /**
     * map of content-type to schema
     * 
     * eg `application/json` -> schema
     * 
     */
    private Map<String, JsonSchema> content;

}
