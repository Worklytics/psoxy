package co.worklytics.psoxy;

/**
 * possibly methods for canonicalizing email addresses prior to pseudonymization.
 *
 * general idea is that email address strings that would be routed to the same mailbox would be
 * considered canonically equivalent. However, there is some discrepancy between major email
 * providers, so we offer several options.
 *
 */
public enum EmailCanonicalization {

    /**
     * aligned with the strictest of the major email providers (eg, Microsoft, Yahoo, Apple); in
     * particular where dots (`.`) in local portion of address (prior to `@`) are respected.
     * eg `roger.rabbit@acme.com` != `rogerrabbit@acme.com`
     *
     * default as of 0.4, bc it's the most conservative/standard compliant approach. If a Microsoft
     * customer wants to have behavior where `.` is ignored, this can always be acheived by explicitly
     * adding those variants as aliases in their directory - and this should pose no problem to
     * proxy operation.
     */
    STRICT,

    /**
     * canonicalization that ignores dots (`.`) in local portion of address (prior to `@`), aligned
     * to convention followed by Google (GMail) and some others.
     *
     * `roger.rabbit@acme.com` == `rogerrabbit@acme.com`
     *
     * Likely to become the default in future versions, because very weird that someone would
     * intentionally issue email addresses that differ only in dots.
     *
     */
    IGNORE_DOTS,
    ;

    public static EmailCanonicalization parseConfigPropertyValue(String s) {
        return EmailCanonicalization.valueOf(s.toUpperCase());
    }
}
