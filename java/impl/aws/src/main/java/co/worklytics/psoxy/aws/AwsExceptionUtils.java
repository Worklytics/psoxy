package co.worklytics.psoxy.aws;

import software.amazon.awssdk.awscore.exception.AwsServiceException;

class AwsExceptionUtils {

    static boolean isAccessDenied(AwsServiceException e) {
        if (e.awsErrorDetails() == null) {
            return false;
        }
        String code = e.awsErrorDetails().errorCode();
        return code != null && (code.contains("AccessDenied") || code.contains("Forbidden"));
    }
}
