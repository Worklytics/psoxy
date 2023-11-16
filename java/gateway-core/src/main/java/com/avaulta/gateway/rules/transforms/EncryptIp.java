package com.avaulta.gateway.rules.transforms;

import com.fasterxml.jackson.annotation.JsonTypeName;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;


//q: call this encrypt?
// - or is encryption implementation detail of a more generic concept?
// - arg to call it encrypt is that revealing the implementation detail to authors/readers of rules
//   is useful - it avoids ambiguity as to what reversible means

@JsonTypeName("encryptIp")
@SuperBuilder(toBuilder = true)
@NoArgsConstructor //for Jackson
@Getter
public class EncryptIp extends Transform {

    //could have a `pass` list here, but ignore for now; complicates config for customer

    @Override
    public EncryptIp clone() {
        return this.toBuilder().build();
    }
}
