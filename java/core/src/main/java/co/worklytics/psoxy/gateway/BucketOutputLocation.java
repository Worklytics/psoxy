package co.worklytics.psoxy.gateway;

import co.worklytics.psoxy.gateway.impl.output.OutputUtils;
import co.worklytics.psoxy.gateway.output.OutputLocation;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Value;
import org.apache.commons.lang3.StringUtils;

@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Value
public class BucketOutputLocation implements OutputLocation {

    String kind;

    String bucket;

    String pathPrefix; // really

    public String getUri() {
        return String.format("%s://%s/%s", kind, bucket, pathPrefix);
    }

    public static BucketOutputLocation from(String uri) {
        if (StringUtils.isBlank(uri)) {
            throw new IllegalArgumentException("Output location must not be blank");
        }

        String[] parts = uri.split("://", 2);
        if (parts.length != 2) {
            throw new IllegalArgumentException("Output location must be in the format 'protocol://bucket/path'");
        }

        String kind = parts[0];
        String bucketAndPath = parts[1];

        String[] bucketParts = bucketAndPath.split("/", 2);
        String bucket = bucketParts[0];
        String pathPrefix = bucketParts.length > 1 ? bucketParts[1] : "";

        return BucketOutputLocation.builder()
            .kind(kind)
            .bucket(bucket)
            .pathPrefix(OutputUtils.formatObjectPathPrefix(pathPrefix))
            .build();
    }

}
