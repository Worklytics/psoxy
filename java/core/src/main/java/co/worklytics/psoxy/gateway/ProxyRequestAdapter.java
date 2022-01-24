package co.worklytics.psoxy.gateway;

import java.util.List;
import java.util.Optional;

public interface ProxyRequestAdapter<R> {

    /**
     * the path part of the proxied request, without any query. If the target source URI is
     * {@code http://foo.com/bar/baz?this=that}, then this method will return {@code /bar/baz}.
     *
     * @return the path part of the URI for this request.
     */
    String getPath(R request);

    /**
     * The query part of the URI for this request. If the full URI is
     * {@code http://foo.com/bar/baz?this=that}, then this method will return {@code this=that}.
     * If there is no query part, the returned {@code Optional} is empty.
     *
     * @return the query part of the URI, if any.
     */
    Optional<String> getQuery(R request);


    Optional<List<String>> getHeader(R request, String headerName);
}
