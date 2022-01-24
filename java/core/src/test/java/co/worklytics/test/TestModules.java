package co.worklytics.test;

import dagger.Module;
import dagger.Provides;

import javax.inject.Singleton;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.UUID;


public class TestModules {


    @Module
    public interface ForFixedClock {

        @Provides
        @Singleton
        static Clock fixedClock() {
            return Clock.fixed(Instant.parse("2021-12-15T00:00:00Z"), ZoneId.of("UTC"));
        }
    }

    //provide deterministic, fixed UUID from `Provider<UUID>` instead of random
    @Module
    public interface ForFixedUUID {

        @Provides
        static UUID uuid() {
            return UUID.fromString("886cd2d1-2a1d-43e9-91d4-6a2b166dff9e");
        }
    }
}
