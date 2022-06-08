package co.worklytics.psoxy.rules.zoom;

import co.worklytics.psoxy.rules.Transform;

/**
 * library of transforms used to support Zoom stuff
 */
public class ZoomTransforms {

    public static final Transform.RedactRegexMatches SANITIZE_JOIN_URL = Transform.RedactRegexMatches.builder()
        .redaction("(?i)pwd=[^&]*")
        .build();

}
