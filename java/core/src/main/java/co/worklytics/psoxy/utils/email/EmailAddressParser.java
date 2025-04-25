package co.worklytics.psoxy.utils.email;

import javax.inject.Inject;
import java.util.EnumSet;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class EmailAddressParser {

    public EmailAddressParser(EnumSet<EmailAddressCriteria> criteria) {
        this.criteria = criteria;
        this.MAILBOX_PATTERN = Dragons.fromCriteria(criteria).MAILBOX_PATTERN;
    }

    @Inject
    public EmailAddressParser() {
        this(EnumSet.of(
            EmailAddressCriteria.ALLOW_QUOTED_IDENTIFIERS,
            EmailAddressCriteria.ALLOW_PARENS_IN_LOCALPART,
            EmailAddressCriteria.ALLOW_DOMAIN_LITERALS // logic change from < 0.5.2; this will allow emails @ IP address to work; not restrict to domain strings - OK.
        ));
    }

    final EnumSet<EmailAddressCriteria> criteria;
    final Pattern MAILBOX_PATTERN;

    /**
     * Parse an email address into its components. The email address must be valid according to the criteria.
     * @param rawEmail to parse
     * @return an Optional containing the parsed email address if valid, or an empty Optional if invalid
     */
    public Optional<EmailAddress> parse(String rawEmail) {
        return Optional.ofNullable(rawEmail)
            .map(MAILBOX_PATTERN::matcher)
            .filter(Matcher::matches)
            .map(m -> EmailAddressParserRoutines.matcherToStruture(m, criteria, true));
    }

    /**
     * whether email address is valid according to the criteria, which is a pragmatic subset of RFC stuff
     *
     * @see EmailAddressParserRoutines for details on precise criteria / interpretation of the RFC.
     *
     * @param rawEmail to validate
     * @return true if valid, false if not
     */
    public boolean isValid(String rawEmail) {
        return parse(rawEmail).isPresent();
    }
}
