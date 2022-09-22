package com.avaulta.gateway.pseudonyms;

import org.apache.commons.lang3.NotImplementedException;

public enum EmailDomainPolicy {

    /**
     * default policy to handle email domains when creating pseudonyms: preserve them as they aren't
     * PII. (so we'll hash/encrypt ONLY the mailbox portion of the email address, which may be PII)
     */
    PRESERVE,

    /**
     * NOT IMPLEMENTED YET!!!
     *
     * TODO: implement this
     *
     * emails that are pseudonymized will have their domain portion encrypted
     *
     * use case: domain is not PII, so there's a more likely use case for wanting to use it in the
     * future; and unlike internal email addresses or something, domains on emails are likely not
     * to be from a well-known list from which someone with the salt could build a lookup table.
     */
    ENCRYPT,

    /**
     * emails that are pseudonymized (hashed) will also have their domain pseudonymized (hashed)
     * eg, default behavior of "alice@acme.com" is to be pseudonymized is:
     *  alice@acme.com --> p~2bd806c97f0e00af1a1fc3328fa763a9269723c8db8fac4f93af71db186d6e90@acme.com
     * but with this policy, will be:
     *  alice@acme.com --> p~2bd806c97f0e00af1a1fc3328fa763a9269723c8db8fac4f93af71db186d6e90@de083c85bb85e370845a988ff97661e7b19bb20dcce0d13584e524bf957279fa
     **/
    HASH,

    REDACT,
    ;

    public static EmailDomainPolicy parseOrDefault(String value) {
        EmailDomainPolicy policy;
        try {
            policy = EmailDomainPolicy.valueOf(value);
        } catch (IllegalArgumentException e) {
            policy = EmailDomainPolicy.PRESERVE;
        }

        if (policy == ENCRYPT) {
            throw new NotImplementedException();
        }

        return policy;
    }
}
