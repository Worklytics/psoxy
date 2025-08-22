package co.worklytics.psoxy;

import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.auth.http.HttpTransportFactory;
import com.google.common.cache.LoadingCache;
import dagger.Lazy;
import lombok.RequiredArgsConstructor;

import javax.inject.Inject;
import java.util.Collections;

/**
 * TODO: could probably be replaced, if we can determine intended `audience` outside of request context, which we should.
 * worst case, should pass it as env var if nothing else.
 * currently, WebhookCollectorModeConfigProperty.AUTH_ISSUER is that value in practice, but exploiting that these are the same
 *
 *
 */
@RequiredArgsConstructor(onConstructor = @__(@Inject))
public class GoogleIdTokenVerifierFactory {


    final HttpTransportFactory httpTransportFactory; // NOTE: same one we use for API calls, which it doesn't necessarily need to be
    final Lazy<JsonFactory> jsonFactoryProvider;
    final GcpEnvironment gcpEnvironment;

    // can cache these, as they are immutable and thread-safe
    LoadingCache<String, GoogleIdTokenVerifier> verifierCache = com.google.common.cache.CacheBuilder.newBuilder()
        .build(new com.google.common.cache.CacheLoader<>() {
            @Override
            public GoogleIdTokenVerifier load(String audience) {
                return buildVerifier(audience);
            }
        });


    public GoogleIdTokenVerifier getVerifierForAudience(String audience) {
        return verifierCache.getUnchecked(audience);
    }

    private GoogleIdTokenVerifier buildVerifier(String audience) {
        return new GoogleIdTokenVerifier.Builder(httpTransportFactory.create(), jsonFactoryProvider.get())
            .setIssuer(gcpEnvironment.getInternalServiceAuthIssuer())
            .setAudience(Collections.singleton(audience))
            .build();
    }
}
