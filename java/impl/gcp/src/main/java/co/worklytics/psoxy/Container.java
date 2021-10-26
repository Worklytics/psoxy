package co.worklytics.psoxy;

import dagger.Component;
import dagger.Module;

@Component(modules = GCPContainer.DIModule.class)
interface Container {

    Route providesRoute();
}
