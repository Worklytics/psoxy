package co.worklytics.psoxy;

import com.fasterxml.jackson.databind.ObjectMapper;
import dagger.Component;

import javax.inject.Named;
import javax.inject.Singleton;

@Singleton
@Component(modules = {
    PsoxyModule.class,
    CmdLineModule.class,
})
public interface CmdLineContainer {

    @Named("ForYAML")
    ObjectMapper yamlMapper();

    Handler fileHandler();
}
