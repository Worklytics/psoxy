package co.worklytics.psoxy;

import dagger.Component;
import org.junit.jupiter.api.Test;

import javax.inject.Inject;
import javax.inject.Singleton;

import static org.junit.jupiter.api.Assertions.*;

class GCSFileEventTest {

    @Singleton
    @Component(modules = {
        PsoxyModule.class,
        ForRules.class,
        MockModules.ForConfigService.class,
        MockModules.ForHostEnvironment.class,
    })
    public interface Container {
        void inject( StorageHandlerTest test);
    }


    @Inject
    GCSFileEvent gcsFileEvent;

    @Test
    void process() {
        gcsFileEvent.process("bucket", "name", null);

    }
}
