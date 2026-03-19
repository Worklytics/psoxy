package com.avaulta.gateway.rules.transforms;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

/**
 * canonicalize IP address and hash result.
 *
 * NOTE: this is explicitly NOT format-preserving; we want it to be clear that the data
 * has been sanitized, so we use standard tokenization (e.g. "t~..." strings).
 *
 * Format-preserving encryption/hashing (e.g. to IPv6 addresses) was considered but rejected
 * because it would make sanitized data appear to contain valid, unsanitized IP addresses, which
 * could be confusing for compliance/auditing.
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
