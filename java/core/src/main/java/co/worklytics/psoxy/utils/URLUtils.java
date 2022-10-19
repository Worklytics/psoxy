package co.worklytics.psoxy.utils;

import lombok.SneakyThrows;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.NameValuePair;
import org.apache.http.client.utils.URLEncodedUtils;

import java.net.URL;
import java.nio.charset.Charset;
import java.util.List;
import java.util.stream.Collectors;

public class URLUtils {

    public static String relativeURL(URL url) {
        // The returned file portion will be the same as getPath(), plus the concatenation of the value of getQuery()
        return url.getFile();
    }

    @SneakyThrows
    public static String relativeURL(String urlAsString) {
        return relativeURL(new URL(urlAsString));
    }

    public static List<String> queryParamNames(URL url) {
        return URLEncodedUtils.parse(url.getQuery(), Charset.defaultCharset()).stream()
            .map(NameValuePair::getName)
            .collect(Collectors.toList());
    }
}
