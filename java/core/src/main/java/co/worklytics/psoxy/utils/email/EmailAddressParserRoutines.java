package co.worklytics.psoxy.utils.email;

/**
 * derived from org.hazlewood.connor.bottema.emailaddress.EmailAddressParser
 *
 * rather than re-write a bunch of that parsing logic, re-use that and wrap with more structured/modern interface (our EmailAddressParser class)
 *
 * particular motivation was avoiding javax / jakarta mail dependencies, which were problematic
 *
 */


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
 */


import lombok.NonNull;

import javax.annotation.Nullable;
import java.util.EnumSet;
import java.util.regex.Matcher;

import static java.util.Objects.requireNonNull;

/**
 * A utility class to parse, clean up, and extract email addresses from messages per RFC2822 syntax. Designed to integrate with Javamail (this class will
 * require that you have a javamail mail.jar in your classpath), but you could easily change the existing methods around to not use Javamail at all. For
 * example, if you're changing the code, see the difference between getInternetAddress and getDomain: the latter doesn't depend on any javamail code. This is
 * all a by-product of what this class was written for, so feel free to modify it to suit your needs.
 * <p>
 * <strong>Regarding the parameter <code>extractCfwsPersonalNames</code>:</strong>
 * <p>
 * This criteria controls the behavior of getInternetAddress and extractHeaderAddresses. If included, it allows the
 * not-totally-kosher-but-happens-in-the-real-world practice of:
 * <p>
 * &lt;bob@example.com&gt; (Bob Smith)
 * <p>
 * In this case, &quot;Bob Smith&quot; is not techinically the personal name, just a comment. If this is included, the methods will convert this into: Bob Smith
 * &lt;bob@example.com&gt;
 * <p>
 * This also happens somewhat more often and appropriately with <code>mailer-daemon@blah.com (Mail Delivery System)</code>
 * <p>
 * If a personal name appears to the left and CFWS appears to the right of an address, the methods will favor the personal name to the left. If the methods need
 * to use the CFWS following the address, they will take the first comment token they find.
 * <p>
 * e.g.:
 * <p>
 * <code>"bob smith" &lt;bob@example.com&gt; (Bobby)</code> yields personal name &quot;bob smith&quot;<br>
 * <code>&lt;bob@example.com&gt; (Bobby)</code> yields personal name &quot;Bobby&quot;<br>
 * <code>bob@example.com (Bobby)</code> yields personal name &quot;Bobby&quot;<br>
 * <code>bob@example.com (Bob) (Smith)</code> yields personal name &quot;Bob&quot;
 */
class EmailAddressParserRoutines {

    private EmailAddressParserRoutines() {}

    /**
     * See {@link #pullFromGroups(Matcher, EnumSet, boolean)}.
     *
     * @param extractCfwsPersonalNames See {@link EmailAddressParser}
     * @return will not return null
     */
    @SuppressWarnings("WeakerAccess")
    @NonNull
    public static EmailAddress matcherToStruture(@NonNull Matcher m,
                                                 @NonNull EnumSet<EmailAddressCriteria> criteria,
                                                 boolean extractCfwsPersonalNames) {
        String current_localpart = null;
        String current_domainpart = null;
        String local_part_da;
        String local_part_qs = null;
        String domain_part_da;
        String domain_part_dl = null;
        String personal_string = null;
        // see the group-ID lists in the grammar comments
        final boolean allowDomainLiterals = criteria.contains(EmailAddressCriteria.ALLOW_DOMAIN_LITERALS);
        if (criteria.contains(EmailAddressCriteria.ALLOW_QUOTED_IDENTIFIERS)) {
            if (allowDomainLiterals) {
                // yes quoted identifiers, yes domain literals
                if (m.group(1) != null) {
                    // name-addr form
                    local_part_da = m.group(5);
                    if (local_part_da == null) {
                        local_part_qs = m.group(6);
                    }
                    domain_part_da = m.group(7);
                    if (domain_part_da == null) {
                        domain_part_dl = m.group(8);
                    }
                    current_localpart = local_part_da == null ? local_part_qs : local_part_da;
                    current_domainpart = domain_part_da == null ? domain_part_dl : domain_part_da;
                    personal_string = m.group(2);
                    if (personal_string == null && extractCfwsPersonalNames) {
                        personal_string = m.group(9);
                        personal_string = removeAnyBounding('(', ')', getFirstComment(personal_string, criteria));
                    }
                } else if (m.group(10) != null) {
                    // addr-spec form
                    local_part_da = m.group(12);
                    if (local_part_da == null) {
                        local_part_qs = m.group(13);
                    }
                    domain_part_da = m.group(14);
                    if (domain_part_da == null) {
                        domain_part_dl = m.group(15);
                    }
                    current_localpart = local_part_da == null ? local_part_qs : local_part_da;
                    current_domainpart = domain_part_da == null ? domain_part_dl : domain_part_da;
                    if (extractCfwsPersonalNames) {
                        personal_string = m.group(16);
                        personal_string = removeAnyBounding('(', ')', getFirstComment(personal_string, criteria));
                    }
                }
            } else {
                // yes quoted identifiers, no domain literals
                if (m.group(1) != null) {
                    // name-addr form
                    local_part_da = m.group(5);
                    if (local_part_da == null) {
                        local_part_qs = m.group(6);
                    }
                    current_localpart = local_part_da == null ? local_part_qs : local_part_da;
                    current_domainpart = m.group(7);
                    personal_string = m.group(2);
                    if (personal_string == null && extractCfwsPersonalNames) {
                        personal_string = m.group(8);
                        personal_string = removeAnyBounding('(', ')', getFirstComment(personal_string, criteria));
                    }
                } else if (m.group(9) != null) {
                    // addr-spec form
                    local_part_da = m.group(11);
                    if (local_part_da == null) {
                        local_part_qs = m.group(12);
                    }
                    current_localpart = local_part_da == null ? local_part_qs : local_part_da;
                    current_domainpart = m.group(13);
                    if (extractCfwsPersonalNames) {
                        personal_string = m.group(14);
                        personal_string = removeAnyBounding('(', ')', getFirstComment(personal_string, criteria));
                    }
                }
            }
        } else {
            // no quoted identifiers, yes|no domain literals
            local_part_da = m.group(3);
            if (local_part_da == null) {
                local_part_qs = m.group(4);
            }
            domain_part_da = m.group(5);
            if (domain_part_da == null && allowDomainLiterals) {
                domain_part_dl = m.group(6);
            }
            current_localpart = local_part_da == null ? local_part_qs : local_part_da;
            current_domainpart = domain_part_da == null ? domain_part_dl : domain_part_da;
            if (extractCfwsPersonalNames) {
                personal_string = m.group((allowDomainLiterals ? 1 : 0) + 6);
                personal_string = removeAnyBounding('(', ')', getFirstComment(personal_string, criteria));
            }
        }
        if (current_localpart != null) {
            current_localpart = current_localpart.trim();
        }
        if (current_domainpart != null) {
            current_domainpart = current_domainpart.trim();
        }
        if (personal_string != null) {
            // trim even though calling cPS which trims, because the latter may return
            // the same thing back without trimming
            personal_string = personal_string.trim();
            personal_string = cleanupPersonalString(personal_string, criteria);
        }
        // remove any unnecessary bounding quotes from the localpart:
        String test_addr = removeAnyBounding('"', '"', current_localpart) +
            "@" + current_domainpart;
        if (Dragons.fromCriteria(criteria).ADDR_SPEC_PATTERN.matcher(test_addr).matches()) {
            current_localpart = removeAnyBounding('"', '"', current_localpart);
        }
        return EmailAddress.builder()
            .personalName(personal_string)
            .localPart(current_localpart)
            .domain(current_domainpart)
            .build();
    }

    /**
     * Given a string, extract the first matched comment token as defined in 2822, trimmed; return null on all errors or non-findings
     * <p>
     * This is probably not super-useful. Included just in case.
     * <p>
     * Note for future improvement: if COMMENT_PATTERN could handle nested comments, then this should be able to as well, but if this method were to be used to
     * find the CFWS personal name (see boolean option) then such a nested comment would probably not be the one you were looking for?
     */
    @SuppressWarnings("WeakerAccess")
    @Nullable
    public static String getFirstComment(@Nullable String text, @NonNull EnumSet<EmailAddressCriteria> criteria) {
        if (text == null) {
            return null; // important
        }
        Matcher m = Dragons.fromCriteria(criteria).COMMENT_PATTERN.matcher(text);
        if (!m.find()) {
            return null;
        }
        return m.group().trim(); // trim important
    }

    /**
     * Given a string, if the string is a quoted string (without CFWS around it, although it will be trimmed) then remove the bounding quotations and then
     * unescape it. Useful when passing simple named address personal names into InternetAddress since InternetAddress always quotes the entire phrase token
     * into one mass; in this simple (and common) case, we can strip off the quotes and de-escape, and passing to javamail will result in a cleaner quote-free
     * result (if there are no embedded escaped characters) or the proper one-level-quoting result (if there are embedded escaped characters). If the string is
     * anything else, this just returns it unadulterated.
     */
    @SuppressWarnings("WeakerAccess")
    @Nullable
    public static String cleanupPersonalString(@Nullable String string, @NonNull EnumSet<EmailAddressCriteria> criteria) {
        if (string == null) {
            return null;
        }
        String text = string.trim();
        final Dragons dragons = Dragons.fromCriteria(criteria);
        Matcher m = dragons.QUOTED_STRING_WO_CFWS_PATTERN.matcher(text);
        if (!m.matches()) {
            return text;
        }
        text = requireNonNull(removeAnyBounding('"', '"', m.group()));
        text = dragons.ESCAPED_BSLASH_PATTERN.matcher(text).replaceAll("\\\\");
        text = dragons.ESCAPED_QUOTE_PATTERN.matcher(text).replaceAll("\"");
        return text.trim();
    }

    /**
     * If the string starts and ends with s and e, remove them, otherwise return the string as it was passed in.
     */
    @SuppressWarnings("WeakerAccess")
    @Nullable
    public static String removeAnyBounding(char s, char e, @Nullable String str) {
        boolean valueStartsEndsWithSAndE = str != null && str.length() >= 2 && str.startsWith(String.valueOf(s)) && str.endsWith(String.valueOf(e));
        return valueStartsEndsWithSAndE ? str.substring(1, str.length() - 1) : str;
    }
}
