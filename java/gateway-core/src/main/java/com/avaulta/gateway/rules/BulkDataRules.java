package com.avaulta.gateway.rules;

import com.avaulta.gateway.rules.transforms.EncryptIp;
import com.avaulta.gateway.rules.transforms.HashIp;
import com.avaulta.gateway.rules.transforms.Transform;
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
    @JsonSubTypes.Type(value = RecordRules.class, name = "record"),
    @JsonSubTypes.Type(value = ColumnarRules.class, name = "columnar"),
})
public interface BulkDataRules extends RuleSet {

}
