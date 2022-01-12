package co.worklytics.psoxy;

import co.worklytics.psoxy.gateway.ConfigService;
import co.worklytics.psoxy.gateway.ProxyConfigProperty;
import co.worklytics.psoxy.rules.PrebuiltSanitizerRules;
import co.worklytics.psoxy.rules.RulesUtils;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.google.api.client.http.HttpRequestFactory;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.jayway.jsonpath.Configuration;
import com.jayway.jsonpath.Option;
import com.jayway.jsonpath.spi.json.JacksonJsonProvider;
import com.jayway.jsonpath.spi.mapper.JacksonMappingProvider;
import dagger.Module;
import dagger.Provides;


import javax.inject.Named;
import java.util.Optional;
import java.util.logging.Logger;

/**
 * provides implementations for platform-independent dependencies of 'core' module
 *
 */
@Module
public class PsoxyModule {


    @Provides
    ObjectMapper providesObjectMapper() {
        return new ObjectMapper();
    }

    @Provides
    @Named("ForYAML")
    ObjectMapper providesYAMLObjectMapper() {
        return new ObjectMapper(new YAMLFactory());
    }

    @Provides
    Configuration providesJSONConfiguration() {
        //jackson here because it's our common JSON stack, but adds dependency beyond the one pkg'd
        // with JsonPath.
        return Configuration.defaultConfiguration()
            .jsonProvider(new JacksonJsonProvider()) //TODO: DI here (share jackson with rest of app)
            .mappingProvider(new JacksonMappingProvider()) // TODO: DI here (share jackson with rest of app)
            .setOptions(Option.SUPPRESS_EXCEPTIONS); //we specifically want to ignore PATH_NOT_FOUND cases
    }


    //q: is this platform dependent?
    @Provides
    HttpRequestFactory providesHttpRequestFactory() {
        return (new NetHttpTransport()).createRequestFactory();
    }

    @Provides
    static Logger logger() {
        return Logger.getLogger(PsoxyModule.class.getCanonicalName());
    }


}
