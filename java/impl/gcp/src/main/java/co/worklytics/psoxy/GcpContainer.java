package co.worklytics.psoxy;

import dagger.Component;

import javax.inject.Singleton;
import java.util.concurrent.ExecutorService;

@Singleton
@Component(modules = {
    GcpModule.class,
    PsoxyModule.class,
    FunctionRuntimeModule.class,
    ConfigRulesModule.class,
    SourceAuthModule.class, //move to include of PsoxyModule??
})
interface GcpContainer {

    @Singleton
    GcpApiDataRequestHandler httpRequestHandler();

    @Singleton
    GcsFileEventHandler gcsFileEventHandler();

    @Singleton
    GcpWebhookCollectionHandler gcpWebhookCollectionHandler();

    @Singleton
    ExecutorService executorService();
}
