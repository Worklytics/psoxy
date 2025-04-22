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
     * Tokenize the domain of the email address.
     */
    TOKENIZE,

    /**
     * Redact the domain of the email address.
     */
    REDACT,
    ;

    public static EmailDomainHandling parseConfigPropertyValue(String s) {
        return EmailDomainHandling.valueOf(s.toUpperCase());
    }

}
