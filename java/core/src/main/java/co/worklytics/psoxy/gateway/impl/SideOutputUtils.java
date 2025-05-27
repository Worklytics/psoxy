package co.worklytics.psoxy.gateway.impl;

import co.worklytics.psoxy.gateway.*;
import co.worklytics.psoxy.gateway.impl.output.NoSideOutput;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import lombok.SneakyThrows;
import org.apache.commons.codec.digest.DigestUtils;

import javax.inject.Inject;
import javax.inject.Provider;
import javax.inject.Singleton;
import java.io.*;
import java.net.URLDecoder;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;
import java.util.zip.GZIPOutputStream;

/**
 * utility methods for working with side output
 *
 * - eg, to create a canonical key for the response to be stored in the side output
 * - or to build metadata for the side output
 */
@NoArgsConstructor(onConstructor_ = {@Inject})
@Singleton
public class SideOutputUtils {

    @AllArgsConstructor
    enum ObjectMetadataKey {
        PII_SALT_SHA256("PII-Salt-Sha256"),
        PROXY_VERSION("Psoxy-Version"),
        ;

        private final String key;
    }

    @Inject
    HostEnvironment hostEnvironment;
    @Inject
    HealthCheckRequestHandler healthCheckRequestHandler;
    @Inject
    ConfigService configService;
    @Inject
    Provider<NoSideOutput> noSideOutputProvider;

    /**
     * get a canonical key for the response to be stored in the side output
     *
     * @param request
     * @return a string key that should be deterministic for the same/equivalent requests; as well as legal
     *       for use as a key in S3/GCS ...
     */
    public String canonicalResponseKey(HttpEventRequest request) {
        //q: do we need to consider response headers?
        // - eg, could be API responses that are NOT cacheable, so in theory we should append some of the headers that denote this
        //     eg, etag, Expires,

        // note: if this exceeds 1024 bytes, we should probably hash it again

       return request.getHttpMethod()
            + "_"
            + request.getHeader("Host").orElse("")
            + "/"
            + normalizePath(request.getPath())
            + "_"
            + request.getQuery().map(this::hashQueryString).orElse("");
    }

    public  Map<String, String>  buildMetadata(HttpEventRequest request) {
        Map<String, String> metadata = new HashMap<>();

        metadata.put(ObjectMetadataKey.PROXY_VERSION.key, HealthCheckRequestHandler.JAVA_SOURCE_CODE_VERSION);

        // to aid in detecting if pseudonyms are consistent across objects
        // - eg, data can only be linked if/when this value is consistent
        metadata.put(ObjectMetadataKey.PII_SALT_SHA256.key, healthCheckRequestHandler.piiSaltHash());

        // q: proxy instance?

        return metadata;
    }

    /**
     * wrap in output stream that will  have the content encoded as gzip
     *
     * @param content
     * @param contentCharset
     * @return a stream that will gzip the content
     */
    @SneakyThrows
    public InputStream toGzippedStream(String content, Charset contentCharset) {
        final PipedInputStream pipedInputStream = new PipedInputStream(8192);
        final PipedOutputStream pipedOutputStream = new PipedOutputStream(pipedInputStream);

        Thread thread = new Thread(() -> {
            try (OutputStream gzipStream = new GZIPOutputStream(pipedOutputStream)) {
                gzipStream.write(content.getBytes(contentCharset));
            } catch (IOException e) {
                // in side output failure, I think we usually want to blow up
                throw new RuntimeException(e);
            } finally {
                try {
                    pipedOutputStream.close();
                } catch (IOException ignored) {
                }
            }
        });

        thread.setDaemon(true);
        thread.start();

        return pipedInputStream;
    }

    /**
     * create a side output, based on the configuration
     *
     * @param sideOutputFactory to use to create the side output
     * @param <T>               type of side output to create
     * @return a side output, or no side output if not configured
     */
    public <T extends SideOutput> SideOutput createSideOutput(
            SideOutputFactory<T> sideOutputFactory) {

        // read the side output bucket from config
        Optional<String> sideOutputBucket = configService.getConfigPropertyAsOptional(ProxyConfigProperty.SIDE_OUTPUT);
        if (sideOutputBucket.isPresent()) {

            // validate that the side output bucket starts with the expected protocol
            String prefix = hostEnvironment.getSupportedSideOutputUriProtocols()
                .stream()
                .filter(sideOutputBucket.get()::startsWith)
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Side output bucket must start with one of: " + hostEnvironment.getSupportedSideOutputUriProtocols()));

            return sideOutputFactory.create(sideOutputBucket.get().substring(prefix.length()));
        } else {
            return noSideOutputProvider.get();
        }
    }


    /**
     * helper method to interpret config, as to whether there's a side output for the given content format or not
     *
     * @param sideOutput        to use if config calls for it
     * @param sideOutputContent the type of content to be written to the side output
     */
    public SideOutput forContent(Provider<SideOutput> sideOutput, SideOutputContent sideOutputContent) {
        boolean sanitizedSideOutput = configService.getConfigPropertyAsOptional(ProxyConfigProperty.SIDE_OUTPUT_CONTENT).map(SideOutputContent::valueOf)
            .filter(content -> content == sideOutputContent)
            .isPresent();
        if (sanitizedSideOutput) {
            return sideOutput.get();
        } else {
            return noSideOutputProvider.get();
        }
    }



    private String normalizePath(String rawPath) {
        // Remove leading/trailing slashes and URL-decode
        if (rawPath == null || rawPath.isEmpty()) return "";
        return Arrays.stream(rawPath.split("/"))
            .filter(s -> !s.isEmpty())
            .map(part -> URLDecoder.decode(part, StandardCharsets.UTF_8))
            .collect(Collectors.joining("/"));
    }

    private String hashQueryString(String rawQuery) {
        if (rawQuery == null || rawQuery.isEmpty()) return "";

        List<String> sortedParams = Arrays.stream(rawQuery.split("&"))
            .map(s -> s.split("=", 2))
            .map(pair -> {
                String key = URLDecoder.decode(pair[0], StandardCharsets.UTF_8);
                String value = pair.length > 1 ? URLDecoder.decode(pair[1], StandardCharsets.UTF_8) : "";
                return key + "=" + value;
            })
            .sorted()
            .collect(Collectors.toList());

        String canonicalQuery = String.join("&", sortedParams);
        return DigestUtils.md5Hex(canonicalQuery);
    }



}
