package co.worklytics.psoxy.gateway;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import javax.inject.Inject;
import org.apache.commons.lang3.StringUtils;
import co.worklytics.psoxy.ControlHeader;
import lombok.NoArgsConstructor;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.java.Log;

/**
 * Applies optional {@link ControlHeader#TARGET_PATH} / {@link ControlHeader#TARGET_QUERY} overrides
 * to an inbound API-mode request.
 *
 * <p>Headers are untrusted client input and are validated before use. Valid values replace
 * {@link HttpEventRequest#getPath()} / {@link HttpEventRequest#getQuery()} for rule matching and
 * upstream URL construction — used exactly as provided (no encode/decode round-trip).
 *
 * <p>NOTE: conflicts with inbound path-prefix trimming ({@code REQUEST_PATH_PREFIX_TO_TRIM}): when
 * TargetPath is present, use that string exactly and do not strip configured path prefixes from it.
 */
@Log
@NoArgsConstructor(onConstructor_ = @Inject)
public class TargetOverrideRequestResolver {

    /**
     * Max length for TargetPath / TargetQuery header values. Zoom and typical SaaS API paths are
     * tiny; 4 KB leaves headroom while bounding header-injection / DoS surface.
     */
    public static final int MAX_HEADER_VALUE_LENGTH = 4096;

    /**
     * @param request inbound request
     * @return same request, or a wrapper whose path/query come from validated control headers
     * @throws IllegalArgumentException if a Target* header is present but invalid
     */
    public HttpEventRequest applyOverrides(HttpEventRequest request) {
        Optional<String> pathOverride = request.getHeader(ControlHeader.TARGET_PATH.getHttpHeader())
            .map(StringUtils::trim)
            .map(this::validateTargetPath);

        Optional<String> queryOverride = request.getHeader(ControlHeader.TARGET_QUERY.getHttpHeader())
            .map(StringUtils::trim)
            .map(this::normalizeAndValidateTargetQuery);

        if (pathOverride.isEmpty() && queryOverride.isEmpty()) {
            return request;
        }

        String effectivePath = pathOverride.orElseGet(request::getPath);
        Optional<String> effectiveQuery = queryOverride.isPresent()
            ? queryOverride
            : request.getQuery();

        if (pathOverride.isPresent()) {
            log.info(String.format(
                "Processing path %s from %s header (request path was %s)",
                effectivePath,
                ControlHeader.TARGET_PATH.getHttpHeader(),
                request.getPath()));
        }
        if (queryOverride.isPresent()) {
            log.info(String.format(
                "Processing query '%s' from %s header (request query was '%s')",
                effectiveQuery.orElse(""),
                ControlHeader.TARGET_QUERY.getHttpHeader(),
                request.getQuery().orElse("")));
        }

        return new HttpEventRequestWithPathQueryOverrides(request, effectivePath, effectiveQuery);
    }

    String validateTargetPath(String path) {
        if (StringUtils.isBlank(path)) {
            throw new IllegalArgumentException(
                ControlHeader.TARGET_PATH.getHttpHeader() + " must not be empty");
        }
        // Path must not include query/fragment delimiters — those belong in TargetQuery / nowhere.
        validateUntrustedHeaderValue(ControlHeader.TARGET_PATH.getHttpHeader(), path, false);
        if (!path.startsWith("/")) {
            throw new IllegalArgumentException(
                ControlHeader.TARGET_PATH.getHttpHeader() + " must start with '/'");
        }
        return path;
    }

    /**
     * Strips a single leading {@code ?} if present (callers sometimes include it), then validates.
     */
    String normalizeAndValidateTargetQuery(String query) {
        String normalized = StringUtils.removeStart(query, "?");
        // empty string is a valid override meaning "no query"
        if (StringUtils.isNotEmpty(normalized)) {
            // Query may contain literal '?' only if embedded oddly; after stripping one leading '?',
            // further '?' is unusual but not a fragment — still disallow '#' and whitespace.
            validateUntrustedHeaderValue(ControlHeader.TARGET_QUERY.getHttpHeader(), normalized, true);
        }
        return normalized;
    }

    /**
     * @param allowQuestionMark whether {@code ?} is permitted (false for TargetPath; true for
     *        TargetQuery after a leading {@code ?} has been stripped)
     */
    void validateUntrustedHeaderValue(String headerName, String value, boolean allowQuestionMark) {
        if (value.length() > MAX_HEADER_VALUE_LENGTH) {
            throw new IllegalArgumentException(
                headerName + " exceeds max length of " + MAX_HEADER_VALUE_LENGTH);
        }
        if (value.indexOf('\r') >= 0 || value.indexOf('\n') >= 0) {
            throw new IllegalArgumentException(
                headerName + " must not contain CR/LF");
        }
        if (value.indexOf(' ') >= 0 || value.indexOf('\t') >= 0) {
            throw new IllegalArgumentException(
                headerName + " must not contain whitespace");
        }
        if (value.indexOf('#') >= 0) {
            throw new IllegalArgumentException(
                headerName + " must not contain '#'");
        }
        if (!allowQuestionMark && value.indexOf('?') >= 0) {
            throw new IllegalArgumentException(
                headerName + " must not contain '?'");
        }
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c < 0x20 || c > 0x7E) {
                throw new IllegalArgumentException(
                    headerName + " must be printable ASCII only");
            }
        }
    }

    /**
     * Delegates to the original request except for path and query, which may be overridden.
     */
    @RequiredArgsConstructor
    static final class HttpEventRequestWithPathQueryOverrides implements HttpEventRequest {

        @NonNull
        private final HttpEventRequest delegate;
        @NonNull
        private final String path;
        @NonNull
        private final Optional<String> query;

        @Override
        public String getPath() {
            return path;
        }

        @Override
        public Optional<String> getQuery() {
            return query;
        }

        @Override
        public Optional<String> getHeader(String headerName) {
            return delegate.getHeader(headerName);
        }

        @Override
        public Optional<List<String>> getMultiValueHeader(String headerName) {
            return delegate.getMultiValueHeader(headerName);
        }

        @Override
        public Map<String, List<String>> getHeaders() {
            return delegate.getHeaders();
        }

        @Override
        public String getHttpMethod() {
            return delegate.getHttpMethod();
        }

        @Override
        public byte[] getBody() {
            return delegate.getBody();
        }

        @Override
        public String prettyPrint() {
            return delegate.prettyPrint();
        }

        @Override
        public Optional<String> getClientIp() {
            return delegate.getClientIp();
        }

        @Override
        public Optional<Boolean> isHttps() {
            return delegate.isHttps();
        }

        @Override
        public Object getUnderlyingRepresentation() {
            return delegate.getUnderlyingRepresentation();
        }
    }
}
