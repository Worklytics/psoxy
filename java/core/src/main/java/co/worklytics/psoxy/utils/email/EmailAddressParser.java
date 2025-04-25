package co.worklytics.psoxy.utils.email;

import org.apache.commons.lang3.StringUtils;

import javax.inject.Inject;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


public class EmailAddressParser {


    public EmailAddressParser(EnumSet<EmailAddressCriteria> criteria) {
        this.criteria = criteria;

        // prob dumb level of indirection, but minimizes coupling to 'Dragons' class
        Dragons dragon = Dragons.fromCriteria(criteria);
        this.MAILBOX_PATTERN = dragon.MAILBOX_PATTERN;
        this.MAILBOX_LIST_PATTERN = dragon.MAILBOX_LIST_PATTERN;
        this.ADDRESS_PATTERN = dragon.ADDRESS_PATTERN;
        this.GROUP_PREFIX_PATTERN = dragon.GROUP_PREFIX_PATTERN;

    }

    @Inject
    public EmailAddressParser() {
        this(EnumSet.of(
            EmailAddressCriteria.ALLOW_QUOTED_IDENTIFIERS,
            EmailAddressCriteria.ALLOW_PARENS_IN_LOCALPART
        ));
    }

    final EnumSet<EmailAddressCriteria> criteria;
    final Pattern MAILBOX_PATTERN;
    final Pattern MAILBOX_LIST_PATTERN;
    final Pattern ADDRESS_PATTERN;
    final Pattern GROUP_PREFIX_PATTERN;

    /**
     * Parse an email address into its components. The email address must be valid according to the criteria.
     * @param rawEmail to parse
     * @return an Optional containing the parsed email address if valid, or an empty Optional if invalid
     */
    public Optional<EmailAddress> parse(String rawEmail) {
        return Optional.ofNullable(rawEmail)
            .map(MAILBOX_PATTERN::matcher)
            .filter(Matcher::matches)
            .map(m -> EmailAddressParserRoutines.matcherToStructure(m, criteria, true));
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

    /**
     * Copyright (C) 2016 Benny Bottema and Casey Connor (benny@bennybottema.com and ahoy@caseyconnor.org)
     *
     * Licensed under the Apache License, Version 2.0 (the "License");
     * you may not use this file except in compliance with the License.
     * You may obtain a copy of the License at
     *
     *         http://www.apache.org/licenses/LICENSE-2.0
     *
     * Unless required by applicable law or agreed to in writing, software
     * distributed under the License is distributed on an "AS IS" BASIS,
     * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
     * See the License for the specific language governing permissions and
     * limitations under the License.
     *
     * Tells us if a header line is valid, i.e. a 2822 address-list (which could only have one address in it, or might have more.) Applicable to To, Cc, Bcc,
     * Reply-To, Resent-To, Resent-Cc, and Resent-Bcc headers <b>only</b>.
     * <p>
     * This method seems quick enough so far, but I'm not totally convinced it couldn't be slow given a complicated near-miss string. You may just want to call
     * extractHeaderAddresses() instead, unless you must confirm that the format is perfect. I think that in 99.9999% of real-world cases this method will work
     * fine and quickly enough. Let me know what your testing reveals.
     *
     * @see #isValidMailboxList(String, EnumSet)
     */
    public boolean isValidAddressList(String value) {
        // creating the actual ADDRESS_LIST_PATTERN string proved too large for java, but
        // fortunately we can use this alternative FSM to check. Since the address pattern
        // is ugreedy, it will match all CFWS up to the comma which we can then require easily.
        final Matcher m = ADDRESS_PATTERN.matcher(value);
        final int max = value.length();
        while (m.lookingAt()) {
            if (m.end() == max) {
                return true;
            } else if (value.charAt(m.end()) == ',') {
                m.region(m.end() + 1, max);
            } else {
                return false;
            }
        }
        return false;
    }

    /*
     * Copyright (C) 2016 Benny Bottema and Casey Connor (benny@bennybottema.com and ahoy@caseyconnor.org)
     *
     * Licensed under the Apache License, Version 2.0 (the "License");
     * you may not use this file except in compliance with the License.
     * You may obtain a copy of the License at
     *
     *         http://www.apache.org/licenses/LICENSE-2.0
     *
     * Unless required by applicable law or agreed to in writing, software
     * distributed under the License is distributed on an "AS IS" BASIS,
     * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
     * See the License for the specific language governing permissions and
     * limitations under the License.
     *
     */
    public List<EmailAddress> parseEmailAddressesFromHeader(String value) {
        if (StringUtils.isBlank(value)) {
            return new ArrayList<>();
        }
        // optimize: separate method or boolean to indicate if group should be worried about at all
        final Matcher m =MAILBOX_PATTERN.matcher(value);
        final Matcher gp = GROUP_PREFIX_PATTERN.matcher(value);
        final ArrayList<EmailAddress> result = new ArrayList<>(1);
        final int max = value.length();
        boolean group_start = false;
        boolean group_end = false;
        int next_comma_index;
        int next_semicolon_index;
        int just_after_group_end = -1;
        // skip past any group prefixes, gobble addresses as usual in a list but
        // skip past the terminating semicolon
        while (true) {
            if (group_end) {
                next_comma_index = value.indexOf(',', just_after_group_end);
                if (next_comma_index < 0) {
                    break;
                }
                if (next_comma_index >= max - 1) {
                    break;
                }
                gp.region(next_comma_index + 1, max);
                m.region(next_comma_index + 1, max);
                group_end = false;
            }
            if (value.charAt(m.regionStart()) == ';') {
                group_start = false;
                m.region(m.regionStart() + 1, max);
                // could say >= max - 1 or even max - 3 or something, but just to be
                // proper:
                if (m.regionStart() >= max) {
                    break;
                }
                gp.region(m.regionStart(), max);
                group_end = true;
                just_after_group_end = m.regionStart();
            }
            if (m.lookingAt()) {
                group_start = false;
                // must test m.end() == max first with early exit
                if (m.end() == max || value.charAt(m.end()) == ',' ||
                    (group_end = value.charAt(m.end()) == ';')) {
                    EmailAddress cur_addr  = EmailAddressParserRoutines.matcherToStructure(m, criteria, true);
                    if (cur_addr != null) {
                        result.add(cur_addr);
                    }
                    if (m.end() < max - 1) {
                        if (!group_end) {
                            // skip the comma
                            gp.region(m.end() + 1, max);
                            m.region(m.end() + 1, max);
                        } else {
                            just_after_group_end = m.end() + 1;
                        }
                    } else {
                        break;
                    }
                } else {
                    break;
                }
            } else if (gp.lookingAt()) {
                if (gp.end() < max) {
                    // the colon is included in the gp match, so nothing to skip
                    m.region(gp.end(), max);
                    gp.region(gp.end(), max);
                    group_start = true;
                } else {
                    break;
                }
            } else if (group_start) {
                next_semicolon_index = value.indexOf(';', m.regionStart());
                if (next_semicolon_index < 0) {
                    break;
                } else if (next_semicolon_index >= max - 1) {
                    break;
                }
                m.region(next_semicolon_index + 1, max);
                gp.region(next_semicolon_index + 1, max);
                group_start = false;
                group_end = true;
                just_after_group_end = m.regionStart();
            } else if (!group_end) {
                break;
            }
        }
        return result;
    }
}
