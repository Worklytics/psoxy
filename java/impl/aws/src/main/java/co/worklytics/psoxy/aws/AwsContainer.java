package co.worklytics.psoxy.aws;

import co.worklytics.psoxy.*;
import co.worklytics.psoxy.gateway.impl.CommonRequestHandler;
import dagger.Component;

import javax.inject.Singleton;

@Singleton
@Component(modules = {
    AwsModule.class,
    ConfigRulesModule.class,
    PsoxyModule.class,
    FunctionRuntimeModule.class,
    SourceAuthModule.class, //move to include of PsoxyModule??
})
public interface AwsContainer {

    CommonRequestHandler createHandler();

    S3Handler injectS3Handler(S3Handler s3Handler);
}