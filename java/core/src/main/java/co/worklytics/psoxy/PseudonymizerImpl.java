package co.worklytics.psoxy;

import com.avaulta.gateway.pseudonyms.Pseudonym;
import com.avaulta.gateway.pseudonyms.PseudonymImplementation;
import com.avaulta.gateway.pseudonyms.impl.UrlSafeTokenPseudonymEncoder;
import com.avaulta.gateway.rules.transforms.Transform;
import com.avaulta.gateway.tokens.DeterministicTokenizationStrategy;
import com.avaulta.gateway.tokens.ReversibleTokenizationStrategy;
import com.google.common.base.Preconditions;
import dagger.assisted.Assisted;
import dagger.assisted.AssistedInject;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.NonNull;
import lombok.extern.java.Log;
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
        return  EmailAddressParser.getLocalPart(original, EmailAddressCriteria.RECOMMENDED, true)
            .toLowerCase()
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

        PseudonymizedIdentity.PseudonymizedIdentityBuilder builder = PseudonymizedIdentity.builder();

        String scope;
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
            scope = PseudonymizedIdentity.EMAIL_SCOPE;
            //q: do something with the personal name??
            // NO --> it is not going to be reliable (except for From, will fill with whatever
            // sender has for the person in their Contacts), and in enterprise use-cases we
            // shouldn't need it for matching
        } else {
            canonicalization = Function.identity();
            scope = getOptions().getDefaultScopeId();
        }

        builder.scope(scope);
        if (getOptions().getPseudonymImplementation() == PseudonymImplementation.LEGACY) {
            builder.hash(hashUtils.hash(canonicalization.apply(value.toString()),
                getOptions().getPseudonymizationSalt(), asLegacyScope(scope)));
        } else if (getOptions().getPseudonymImplementation() == PseudonymImplementation.DEFAULT) {
            builder.hash(encoder.encodeToString(deterministicTokenizationStrategy.getToken(value.toString(), canonicalization)));
        } else {
            throw new RuntimeException("Unsupported pseudonym implementation: " + getOptions().getPseudonymImplementation());
        }

        if (transformOptions.getIncludeReversible()) {
            builder.reversible(urlSafePseudonymEncoder.encode(
                Pseudonym.builder()
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

    //converts 'scope' to legacy value (eg, equivalents to original Worklytics scheme, where no scope
    // meant 'email'
    private String asLegacyScope(@NonNull String scope) {
        return scope.equals(PseudonymizedIdentity.EMAIL_SCOPE) ? "" : scope;
    }
}
