package co.worklytics.psoxy.utils.email;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class EmailAddress {

    String personalName;
    String localPart;
    String domain;

    public String asFormattedString() {
        if (personalName != null && !personalName.isEmpty()) {
            return String.format("\"%s\" <%s@%s>", personalName, localPart, domain);
        } else {
            return String.format("%s@%s", localPart, domain);
        }
    }
}
