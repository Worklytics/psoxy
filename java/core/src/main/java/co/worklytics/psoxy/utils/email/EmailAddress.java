package co.worklytics.psoxy.utils.email;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class EmailAddress {
    public String personalName;
    public String localPart;
    public String domain;
}
