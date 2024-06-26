package co.worklytics.psoxy;

import com.fasterxml.jackson.databind.ObjectMapper;
import dagger.Component;
import org.apache.commons.cli.CommandLine;

import javax.inject.Named;
import javax.inject.Singleton;

@Singleton
@Component(modules = {
    CmdLineModule.class,
    PsoxyModule.class,
})
public interface CmdLineContainer {

    Handler fileHandler();

    @Component.Builder
    interface Builder {
        CmdLineContainer build();

        Builder cmdLineModule(CmdLineModule cmdLineModule);
    }

    CommandLineConfigServiceFactory commandLineConfigServiceFactory();

}
