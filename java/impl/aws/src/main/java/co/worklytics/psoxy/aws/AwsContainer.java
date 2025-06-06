package co.worklytics.psoxy.aws;

import co.worklytics.psoxy.*;
import co.worklytics.psoxy.gateway.impl.ApiDataRequestHandler;
import co.worklytics.psoxy.gateway.impl.BatchMergeHandler;
import co.worklytics.psoxy.gateway.impl.InboundWebhookHandler;
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

    ApiDataRequestHandler apiDataRequestHandler();

    InboundWebhookHandler inboundWebhookHandler();

    BatchMergeHandler batchMergeHandler();

    S3Handler injectS3Handler(S3Handler s3Handler);
}
