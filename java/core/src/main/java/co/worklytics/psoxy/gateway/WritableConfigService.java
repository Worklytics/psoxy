package co.worklytics.psoxy.gateway;

import com.google.common.util.concurrent.Uninterruptibles;

import java.time.Duration;

public interface WritableConfigService extends ConfigService {

    /**
     * write value of property in config, if supports it
     *
     * @param property to write value for
     * @param value to write
     */
    void putConfigProperty(ConfigService.ConfigProperty property, String value);

    /**
     * write value of property in config, if supports it
     *
     * @param property to write value for
     * @param value to write
     * @throws WritePropertyRetriesExhaustedException if write fails after designated retries
     */
    default void putConfigProperty(ConfigService.ConfigProperty property, String value, int retries) throws WritePropertyRetriesExhaustedException {
        if (retries <= 0) {
            // use the non-retry version
            throw new IllegalArgumentException("retries must be > 0");
        }
        Exception lastException;
        do {
            try {
                putConfigProperty(property, value);
                return;
            } catch (Exception e) {
                // retry - wait slightly
                lastException = e;
                Uninterruptibles.sleepUninterruptibly(Duration.ofMillis(150));
            }
        } while (--retries > 0);
        throw new WritePropertyRetriesExhaustedException("Failed to write config property " + property, lastException);
    }
}
