package co.worklytics.psoxy.aws;

import co.worklytics.psoxy.gateway.impl.VaultConfigService;
import com.amazonaws.auth.AWSCredentials;
import dagger.assisted.AssistedFactory;


@AssistedFactory
public interface VaultAwsIamAuthFactory {

    VaultAwsIamAuth create(String awsRegion,
                           AWSCredentials awsCredentials);
}
