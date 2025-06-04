package co.worklytics.psoxy.aws;

import dagger.assisted.Assisted;
import dagger.assisted.AssistedFactory;

@AssistedFactory
public interface SQSOutputFactory {


    SQSOutput create(@Assisted SQSOutput.Options options);
}
