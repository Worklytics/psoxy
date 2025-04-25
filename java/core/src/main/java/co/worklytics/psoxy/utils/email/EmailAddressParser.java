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

    public Optional<EmailAddress> parse(String rawEmail) {
        return Optional.ofNullable(rawEmail)
            .map(MAILBOX_PATTERN::matcher)
            .filter(Matcher::matches)
            .map(m -> EmailAddressParserRoutines.matcherToStruture(m, criteria, true));
    }
}
