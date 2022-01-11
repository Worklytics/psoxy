package co.worklytics.psoxy;

import co.worklytics.psoxy.di.PsoxyModule;
import co.worklytics.psoxy.gateway.di.GatewayModule;
import dagger.Component;

import javax.inject.Singleton;

@Singleton
@Component(modules = {
    GcpModule.class,
    GatewayModule.class,
    PsoxyModule.class
})
interface GcpContainer {

    Route injectRoute(Route route);
}
