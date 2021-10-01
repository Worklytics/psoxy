package co.worklytics.psoxy;

import lombok.Builder;
import lombok.NonNull;
import org.apache.commons.codec.digest.DigestUtils;

@Builder
public class Pseudonymizer {

    @NonNull
    final String salt;


}
