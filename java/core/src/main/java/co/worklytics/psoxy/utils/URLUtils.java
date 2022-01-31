package co.worklytics.psoxy.utils;

import lombok.SneakyThrows;
import org.apache.commons.lang3.StringUtils;

import java.net.URL;

public class URLUtils {

    public static String relativeURL(URL url) {
        // The returned file portion will be the same as getPath(), plus the concatenation of the value of getQuery()
        return url.getFile();
    }

    @SneakyThrows
    public static String relativeURL(String urlAsString) {
        return relativeURL(new URL(urlAsString));
    }

}
