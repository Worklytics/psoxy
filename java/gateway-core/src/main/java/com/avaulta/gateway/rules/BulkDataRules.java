package com.avaulta.gateway.rules;


import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

/**
 * rules that define how to sanitize bulk (file) data
 *
 * @see ColumnarRules implementation of this for CSV/TSV files
 *
 *
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.DEDUCTION)
@JsonSubTypes({
    @JsonSubTypes.Type(value = RecordRules.class),
    @JsonSubTypes.Type(value = ColumnarRules.class),
})
public interface BulkDataRules extends RuleSet {

}
