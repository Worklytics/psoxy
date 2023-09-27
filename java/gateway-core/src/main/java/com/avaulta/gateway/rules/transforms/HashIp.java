package com.avaulta.gateway.rules.transforms;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

/**
 * canonicalize IP address and hash result.
 *
 * "format-preserving", although everything will end up IPv6 (128-bit) even if original was IPv4
 *
 */
@SuperBuilder(toBuilder = true)
@NoArgsConstructor //for Jackson
@Getter
public class HashIp extends Transform {

    //could have a `pass` list here, but ignore for now; complicates config for customer

    @Override
    public HashIp clone() {
        return this.toBuilder().build();
    }
}
