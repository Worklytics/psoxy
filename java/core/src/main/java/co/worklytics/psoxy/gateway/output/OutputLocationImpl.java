package co.worklytics.psoxy.gateway.output;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Value;

import java.util.Arrays;

@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Value
public class OutputLocationImpl implements OutputLocation {

    LocationKind kind;

    String uri;


    public static OutputLocationImpl of(String uri) {

        if (uri == null || uri.isBlank()) {
            throw new IllegalArgumentException("Output location URI must not be blank");
        }

        return Arrays.stream(LocationKind.values())
            .filter(kind -> uri.startsWith(kind.getUriPrefix()))
            .findAny()
            .map(kind -> new OutputLocationImpl(kind, uri))
            .orElseThrow(() -> new IllegalArgumentException("Output location URI does not match any known kind: " + uri));
    }
}
