package co.worklytics.psoxy;

import dagger.Component;

import javax.inject.Singleton;

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
    HttpRequestHandler httpRequestHandler();

    @Singleton
    GcsFileEventHandler gcsFileEventHandler();

    @Singleton
    GcpWebhookCollectionHandler gcpWebhookCollectionHandler();
}
