package co.worklytics.psoxy.gateway;

import java.util.Optional;
import java.util.Set;
import javax.inject.Inject;
import lombok.AllArgsConstructor;

/**
 * Asks every bound {@link ConnectivityFailures} parser.
 *
 * <p>A match with a platform client message wins over a token alone, and a token wins over a match
 * that only knows the call failed. Stateless aside from the injected parser set, which is fixed
 * for the life of the container. Safe to call from any thread.
 */
@AllArgsConstructor(onConstructor_ = @Inject)
public class ConnectivityFailureMatcher {

    private final Set<ConnectivityFailures> parsers;

    public Optional<DependencyConnectivityFailure> match(Throwable throwable) {
        DependencyConnectivityFailure best = null;
        for (ConnectivityFailures parser : parsers) {
            Optional<DependencyConnectivityFailure> found = parser.match(throwable);
            if (found.isEmpty()) {
                continue;
            }
            if (best == null || specificity(found.get()) > specificity(best)) {
                best = found.get();
            }
        }
        return Optional.ofNullable(best);
    }

    private int specificity(DependencyConnectivityFailure failure) {
        int score = 0;
        if (failure.getClientMessage() != null) {
            score += 2;
        }
        if (failure.getService() != null) {
            score += 1;
        }
        return score;
    }
}
