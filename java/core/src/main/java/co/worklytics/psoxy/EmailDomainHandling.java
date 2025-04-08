package co.worklytics.psoxy;

public enum EmailDomainHandling {

    /**
     * Preserve the domain of the email address.
     */
    PRESERVE,

    /**
     * Encrypt the domain of the email address.
     */
    ENCRYPT,

    /**
     * Hash the domain of the email address.
     *
     * q: better as 'TOKENIZE'? consistent with our `t~` prefix
     */
    HASH,

    /**
     * Redact the domain of the email address.
     */
    REDACT,
    ;

    public static EmailDomainHandling parseConfigPropertyValue(String s) {
        return EmailDomainHandling.valueOf(s.toUpperCase());
    }

}
