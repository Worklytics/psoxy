package co.worklytics.psoxy.utils;

import lombok.SneakyThrows;
import org.apache.commons.lang3.StringUtils;

import java.net.URL;

public class URLUtils {

    public static String relativeURL(URL url) {
        StringBuilder relativeUrl = new StringBuilder();
        relativeUrl.append(url.getPath());
        if (StringUtils.isNotBlank(url.getQuery())) {
            relativeUrl.append("?");
            relativeUrl.append(url.getQuery());
        }
        return relativeUrl.toString();
    }

    @SneakyThrows
    public static String relativeURL(String urlAsString) {
        return relativeURL(new URL(urlAsString));
    }

}
