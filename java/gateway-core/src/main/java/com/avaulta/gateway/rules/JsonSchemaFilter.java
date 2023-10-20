package com.avaulta.gateway.rules;

import com.fasterxml.jackson.annotation.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.With;
import lombok.experimental.SuperBuilder;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

@With
@SuperBuilder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor // for builder
@Data
@JsonPropertyOrder({"$schema"})
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties({
    "title",
    "required", // not relevant to 'filter' use case
    "additionalProperties", // not currently supported
    "$schema", // not helpful in filter use-case, although maybe should include in future
})
public class JsonSchemaFilter {

    //dropped for now
    //@JsonProperty("$schema")
    //String schema;

    String type;

    @JsonProperty("$ref")
    String ref;

    //only applicable if type==String
    String format;

    Map<String, JsonSchemaFilter> properties;

    //only applicable if type==array
    JsonSchemaFilter items;

    // @JsonProperties("$defs") on property, lombok getter/setter don't seem to do the right thing
    // get java.lang.IllegalArgumentException: Unrecognized field "definitions" (class com.avaulta.gateway.rules.SchemaRuleUtils$JsonSchema)
    //        //  when calling objectMapper.convertValue(((JsonNode) schema), JsonSchema.class);??
    // perhaps it's a problem with the library we use to build the JsonNode schema??

    //TODO: this is not used except from the 'root' schema; is that correct??
    Map<String, JsonSchemaFilter> definitions;

    public Map<String, JsonSchemaFilter> getDefinitions() {
        // sorted map, so serialization of definitions is deterministic
        if (!(definitions instanceof TreeMap) && definitions != null) {
            definitions = new TreeMap<>(definitions);
        }
        return definitions;
    }

    // part of JSON schema standard, but how to support for filters?
    //  what if something validates against multiple of these, but filtering by the valid ones
    //  yields different result??
    // use case would be polymorphism, such as a groupMembers array can can contain
    // objects of type Group or User, to provide hierarchical groups
    // --> take whichever schema produces the "largest" result (eg, longest as a string??)
    //List<CompoundJsonSchema> anyOf;

    // part of JSON schema standard, but how to support for filters?
    //  what if something validates against multiple of these, but filtering by the valid ones
    //  yields different result??
    // ultimately, don't see a use case anyways
    List<JsonSchemaFilter> oneOf;

    // part of JSON schema standard
    // it's clear how we would implement this as a filter (chain them), but not why
    // this would ever be a good use case
    //List<CompoundJsonSchema> allOf;

    //part of JSON schema standard, but not a proxy-filtering use case this
    // -- omitting the property produces same result
    // -- no reason you'd ever want to all objects that DON'T match a schema, right?
    // -- only use case I think of is to explicitly note what properties we know are in
    //   source schema, so intend for filter to remove (rather than filter removing them by
    //   omission)
    //CompoundJsonSchema not;

    @JsonAlias("if")
    JsonSchemaFilterUtils.ConditionJsonSchema _if;

    @JsonAlias("else")
    JsonSchemaFilterUtils.ConditionJsonSchema _else;

    @JsonAlias("then")
    JsonSchemaFilterUtils.ThenJsonSchema _then;

    @JsonAlias("const")
    private String constant;

    @JsonIgnore
    public boolean isRef() {
        return ref != null;
    }

    @JsonIgnore
    public boolean isString() {
        return Objects.equals(type, "string");
    }

    @JsonIgnore
    public boolean isNumber() {
        return Objects.equals(type, "number");
    }

    @JsonIgnore
    public boolean isInteger() {
        return Objects.equals(type, "integer");
    }

    @JsonIgnore
    public boolean isObject() {
        return Objects.equals(type, "object") || (type == null && properties != null);
    }

    @JsonIgnore
    public boolean isArray() {
        return Objects.equals(type, "array") || (type == null && items != null);
    }

    @JsonIgnore
    public boolean isBoolean() {
        return Objects.equals(type, "boolean");
    }

    @JsonIgnore
    public boolean isNull() {
        return Objects.equals(type, "null");
    }

    @JsonIgnore
    public boolean hasType() {
        return this.type != null;
    }

    @JsonIgnore
    public boolean isComplex() {
        return isObject() || isArray();
    }

    @JsonIgnore
    public boolean hasIf() {
        return this._if != null;
    }

    @JsonIgnore
    public boolean hasElse() {
        return this._else != null;
    }

    @JsonIgnore
    public boolean hasThen() {
        return this._then != null;
    }

    @JsonIgnore
    public boolean hasConstant() {
        return this.constant != null;
    }

    @JsonIgnore
    public boolean hashOneOf() {
        return this.oneOf != null && !this.oneOf.isEmpty();
    }
}
