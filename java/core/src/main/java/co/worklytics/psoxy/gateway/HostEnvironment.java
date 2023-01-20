package co.worklytics.psoxy.gateway;

/**
 * encapsulates host-environment (eg, cloud platform) specific information
 *
 *   - why not in ConfigService? bc affects how ConfigService itself is spun up by DI
 *   - why not in DI module? bc the "host platform" modules aren't really an interface, so there's
 *     not a strong check of what they're supposed to provide. and what we implement there are
 *     static methods
 */
public interface HostEnvironment {

    /**
     * @return id of this proxy instance, eg `psoxy-gcal`
     */
    String getInstanceId();
}
