package co.worklytics.psoxy.gateway.impl;

import com.google.auth.oauth2.GoogleCredentials;
import co.worklytics.psoxy.gateway.RequiresConfiguration;

/**
 * How this proxy process authenticates to Google Cloud APIs (IAM Credentials {@code signJwt},
 * etc).
 *
 * <p>Implementations MUST construct that identity explicitly (GCE metadata, AWS WIF, …). Do not
 * fall back to {@link GoogleCredentials#getApplicationDefault()}, which can silently pick up a
 * downloaded key, user ADC, or some other ambient credential.
 */
public interface GoogleCloudProcessIdentity extends RequiresConfiguration {

    /**
     * @return identifier used as {@code PROCESS_IDENTITY_SOURCE} to select this implementation
     */
    String getConfigIdentifier();

    /**
     * Credentials for this process as a GCP principal. Caller must have
     * {@code iam.serviceAccounts.signJwt} on the domain-wide-delegation service account
     * (typically {@code roles/iam.serviceAccountTokenCreator}).
     *
     * <p>Must be thread-safe if cached and reused across concurrent requests.
     */
    GoogleCredentials getCredentials();
}
