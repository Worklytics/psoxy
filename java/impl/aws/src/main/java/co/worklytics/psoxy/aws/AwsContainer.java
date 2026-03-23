package co.worklytics.psoxy.aws;

import javax.inject.Singleton;
import com.fasterxml.jackson.databind.ObjectMapper;
import co.worklytics.psoxy.ConfigRulesModule;
import co.worklytics.psoxy.FunctionRuntimeModule;
import co.worklytics.psoxy.PsoxyModule;
import co.worklytics.psoxy.S3Handler;
import co.worklytics.psoxy.SourceAuthModule;
import co.worklytics.psoxy.aws.request.LambdaEventUtils;
import co.worklytics.psoxy.gateway.impl.ApiDataRequestHandler;
import co.worklytics.psoxy.gateway.impl.BatchMergeHandler;
import co.worklytics.psoxy.gateway.impl.InboundWebhookHandler;
import co.worklytics.psoxy.gateway.impl.JwksDecorator;
import dagger.Component;

@Singleton
@Component(modules = {
    AwsModule.class,
    ConfigRulesModule.class,
    PsoxyModule.class,
    FunctionRuntimeModule.class,
    SourceAuthModule.class, //move to include of PsoxyModule??
})
public interface AwsContainer {

    co.worklytics.psoxy.gateway.LoggingConfiguration loggingConfiguration();

    ApiDataRequestHandler apiDataRequestHandler();

    InboundWebhookHandler inboundWebhookHandler();

    BatchMergeHandler batchMergeHandler();

    ObjectMapper objectMapper();

    S3Handler injectS3Handler(S3Handler s3Handler);

    JwksDecorator.Factory jwksDecoratorFactory();

    LambdaEventUtils lambdaEventUtils();
}
