package co.worklytics.psoxy.di;

import co.worklytics.psoxy.Sanitizer;
import co.worklytics.psoxy.impl.SanitizerImpl;
import co.worklytics.psoxy.rules.RulesUtils;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
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
}
