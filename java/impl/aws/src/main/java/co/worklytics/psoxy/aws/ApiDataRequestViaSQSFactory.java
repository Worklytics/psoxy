package co.worklytics.psoxy.aws;

import dagger.assisted.Assisted;
import dagger.assisted.AssistedFactory;

@AssistedFactory
public interface ApiDataRequestViaSQSFactory {


    ApiDataRequestViaSQS create(@Assisted String queueUrl);
}
