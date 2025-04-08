# Email Handling

As of 0.5.2, the following configuration parameters are supported to control how the proxy handles emails.

Any value handled by a `pseudonymize` transform of the proxy that appears to be an email address is subject to special handling, including
parsing out the domain portion and computing a hash of the email address based on a *canonicalized* version of the email address (rather
than hashing the raw byte string).

This is to 1) allow domains to pass through by default, as these are not considered PII (do not uniquely identify a natural person) - subject
to overrides described below, and 2) ensure that email addresses that are effectively the same result in equivalent hashes, so `Alice@acme.com`
and `alice@acme.com` match to the same account.   In the case of (2), this is a question of the semantics of the `acme.com` mail system. All
major email providers ignore casing in email addresses - but there are some other differences, which we provide configuration values below
to assist with.

## `EMAIL_CANONICALIZATION`

This parameter controls how an email address seen by the proxy is canonicalized prior to hashing. Many email systems are case-insensitive, such that
`jane.doe@acme.com` and `Jane.Doe@acme.com` both deliver email to the same mailbox; additionally, many ignore the `.` chars in the mailbox portion;
and some support aliases with `+` suffix, ignoring the `+` and everything after it (eg, `jane.doe+spam@acme.com` will still be delivered to that same
mailbox).  However, these conventions are NOT universal nor fully standardized. As such, we expose a configuration parameter to override it as you choose.

  - `STRICT` - default as of 0.5; considers `.` as respected, which is how Microsoft/Yahoo/Apple behave
  - `IGNORE_DOTS` - recommended; ignores `.` in the local (mailbox) portion of the email address, but not the domain portion.

## `EMAIL_DOMAIN_HANDLING` **alpha**

By default, the proxy preserves the domain portion of email addresses. With this configuration variable, you can override this behavior in two ways:

  - `ENCRYPT` - if you set this parameter to `ENCRYPT`, the proxy will hash as well as encrypt the domain portion of email addresses, just
    like in the encrypted pseudonym case. This provides both a persistent, irreverible form of the domain (the hash) and a reversible form (the encrypted one).
    This perserves the abilty to track collaboration with a distinct external organization over time, without identifying that organization UNLESS you allow users
     to decrypt the encrypted form of the domain.  Destroying/rotating the encryption key can remove teh abiltity to decrypt, without altering the hash, so
     communication history will be preserved.
  - `REDACT` - email domains will be dropped entirely
  - `HASH` - email domains will be hashed, but not encrypted. This is the default behavior.

