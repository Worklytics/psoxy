package co.worklytics.psoxy.gateway;

import com.google.auth.Credentials;

import java.io.IOException;
import java.util.Optional;
import java.util.Set;

/**
 * encapsulates strategy for how to authenticate with source
 *
 * Options:
 *   - Oauth2 authCode - canonical 3-legged oauth; requires us to implement two user-facing HTTP
 *      endpoints - 1) initiate the flow, 2) callback to receive redirect
 *   - key (eg, Google Service Account key) - isn't this arguably just another form of Oauth2 client
 *     credentials flow, but with a differ
 *   - oauth 2.0 client credentials flow ...
 *        - MSFT supposedly, although can use certificate to build assertion; get accessToken that
 *          way instead of canonical 3-legged oauth
 *        - Slack
 *   - JWT assertion (atlassian) - but construction of such assertions seems source dependent? not
 *      really standard, although many are similar
 *       - isn't this again, client_credentials flow? yetj
 *   -
 *
 * doubts:
 *   - whether stuff is completely re-usable, or if it's more variations on a theme
 *      - eg, google service account key *is* actually Oauth, it's just using the service account
 *        key to build assertions to get accessTokens, rather than
 *      - Microsoft/Atlassian have flows that are similar, but differ a bit in the protocol vs
 *        Google and we have to implement more of it
 *   - encapsulating this way might make it *less* reusable. May need two parts:
 *      - CredentialStrategy - how to obtain/maintain credential
 *      - RequestAuthStrategy - given credential, how to use it to auth the request
 *         - any api that DOESN'T simply append token via Authorization header as a 'Bearer {{token}}'?
 *         - atlassian I guess, bc signing every request with unique JWT? (never obtaining an access
 *           token)
 *
 */
public interface SourceAuthStrategy extends RequiresConfiguration {

    /**
     * @return a string that can uniquely identify this SourceAuthStrategy implementation for
     *         configuration purposes
     */
    String getConfigIdentifier();


    Credentials getCredentials(Optional<String> userToImpersonate);

    /**
     * Whether {@code e} was thrown while authenticating to the data source (token exchange,
     * credential refresh), as opposed to a malformed inbound request.
     *
     * <p>Default is {@code false}: strategies that attach static credentials do not call an
     * identity provider during request construction. Strategies that exchange tokens should
     * override with the exception types/messages their identity provider produces.
     */
    default boolean isSourceAuthFailure(IOException e) {
        return false;
    }

}
