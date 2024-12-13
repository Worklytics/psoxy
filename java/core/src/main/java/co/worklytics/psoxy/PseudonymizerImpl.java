package co.worklytics.psoxy;

import com.avaulta.gateway.pseudonyms.Pseudonym;
import com.avaulta.gateway.pseudonyms.impl.UrlSafeTokenPseudonymEncoder;
import com.avaulta.gateway.rules.transforms.Transform;
import com.avaulta.gateway.tokens.DeterministicTokenizationStrategy;
import com.avaulta.gateway.tokens.ReversibleTokenizationStrategy;
import com.google.common.base.Preconditions;
import dagger.assisted.Assisted;
import dagger.assisted.AssistedInject;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.extern.java.Log;
import org.apache.commons.lang3.StringUtils;
import org.hazlewood.connor.bottema.emailaddress.EmailAddressCriteria;
import org.hazlewood.connor.bottema.emailaddress.EmailAddressParser;
import org.hazlewood.connor.bottema.emailaddress.EmailAddressValidator;

import javax.inject.Inject;
import java.util.Base64;
import java.util.Objects;
import java.util.function.Function;

@NoArgsConstructor
@Log
public class PseudonymizerImpl implements Pseudonymizer {

    @Inject
    HashUtils hashUtils;

    @Inject
    ReversibleTokenizationStrategy reversibleTokenizationStrategy;
    @Inject
    DeterministicTokenizationStrategy deterministicTokenizationStrategy;
    @Inject
    UrlSafeTokenPseudonymEncoder urlSafePseudonymEncoder;

    Base64.Encoder encoder = Base64.getUrlEncoder().withoutPadding();

    @Getter
    ConfigurationOptions options;

    @AssistedInject
    public PseudonymizerImpl(@Assisted Pseudonymizer.ConfigurationOptions options) {
        this.options = options;
    }

    @Override
    public PseudonymizedIdentity pseudonymize(Object value) {
        return pseudonymize(value, Transform.Pseudonymize.builder().build());
    }

    String emailCanonicalization(String original) {
        String domain = EmailAddressParser.getDomain(original, EmailAddressCriteria.RECOMMENDED, true);

        //NOTE: lower-case here is NOT stipulated by RFC
        String mailboxLowercase = EmailAddressParser.getLocalPart(original, EmailAddressCriteria.RECOMMENDED, true)
            .toLowerCase();

        //trim off any + and anything after it (sub-address)
        if (mailboxLowercase.contains("+")) {
            mailboxLowercase = mailboxLowercase.substring(0, mailboxLowercase.indexOf("+"));
        }

        if (getOptions().getEmailCanonicalization() == EmailCanonicalization.IGNORE_DOTS) {
            mailboxLowercase = mailboxLowercase.replace(".", "");
        }

        return mailboxLowercase
            + "@"
            + domain.toLowerCase();

    }

    @Override
    public PseudonymizedIdentity pseudonymize(Object value, Transform.PseudonymizationTransform transformOptions) {
        if (value == null) {
            return null;
        }

        Preconditions.checkArgument(value instanceof String || value instanceof Number,
            "Value must be some basic type (eg JSON leaf, not node)");

        // Base case; empty/blank string
        if (value instanceof String && StringUtils.isBlank((String)value)) {
            return null;
        }

        PseudonymizedIdentity.PseudonymizedIdentityBuilder builder = PseudonymizedIdentity.builder();

        //q: this auto-detect a good idea? Or invert control and let caller specify with a header
        // or something??
        //NOTE: use of EmailAddressValidator/Parser here is probably overly permissive, as there
        // are many cases where we expect simple emails (eg, alice@worklytics.co), not all the
        // possible variants with personal names / etc that may be allowed in email header values

        Function<String, String> canonicalization;
        String domain = null;
        if (duckTypesAsEmails(value)) {
            canonicalization = this::emailCanonicalization;
            domain = EmailAddressParser.getDomain((String) value, EmailAddressCriteria.RECOMMENDED, true);
            builder.domain(domain);
            //q: do something with the personal name??
            // NO --> it is not going to be reliable (except for From, will fill with whatever
            // sender has for the person in their Contacts), and in enterprise use-cases we
            // shouldn't need it for matching
        } else {
            canonicalization = Function.identity();
        }

        byte[] hashWithDefaultImpl =
            deterministicTokenizationStrategy.getToken(value.toString(), canonicalization);

        // encoded hash will be filled based on customer's config; may NOT simply be encoding of
        // hashWithDefaultImpl
        String encodedHash = encoder.encodeToString(hashWithDefaultImpl);

        builder.hash(encodedHash);

        if (transformOptions.getIncludeReversible()) {
            builder.reversible(urlSafePseudonymEncoder.encode(
                Pseudonym.builder()
                    .hash(hashWithDefaultImpl)
                    .reversible(reversibleTokenizationStrategy.getReversibleToken(value.toString(), canonicalization))
                    .domain(domain)
                    .build()));
        }

        if (transformOptions.getIncludeOriginal()) {
            builder.original(Objects.toString(value));
        }

        return builder.build();
    }

    boolean duckTypesAsEmails(Object value) {
        return value instanceof String && EmailAddressValidator.isValid((String) value);
    }
}
