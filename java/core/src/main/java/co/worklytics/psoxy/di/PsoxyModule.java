package co.worklytics.psoxy.di;

import co.worklytics.psoxy.Sanitizer;
import co.worklytics.psoxy.impl.SanitizerImpl;
import co.worklytics.psoxy.rules.RulesUtils;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.jayway.jsonpath.Configuration;
import com.jayway.jsonpath.Option;
import com.jayway.jsonpath.spi.json.JacksonJsonProvider;
import com.jayway.jsonpath.spi.mapper.JacksonMappingProvider;
import dagger.Module;
import dagger.Provides;

import javax.inject.Named;

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
            .jsonProvider(new JacksonJsonProvider())
            .mappingProvider(new JacksonMappingProvider())
            .setOptions(Option.SUPPRESS_EXCEPTIONS); //we specifically want to ignore PATH_NOT_FOUND cases
    }
}
