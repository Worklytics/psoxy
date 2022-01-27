package co.worklytics.psoxy.aws;

import co.worklytics.psoxy.ConfigRulesModule;
import co.worklytics.psoxy.FunctionRuntimeModule;
import co.worklytics.psoxy.Handler;
import co.worklytics.psoxy.PsoxyModule;
import co.worklytics.psoxy.SourceAuthModule;
import dagger.Component;

import javax.inject.Singleton;

@Singleton
@Component(modules = {
    AwsModule.class,
    ConfigRulesModule.class,
    PsoxyModule.class,
    FunctionRuntimeModule.class,
    SourceAuthModule.class, //move to include of PsoxyModule??
})
public interface AwsContainer {

    Handler injectHandler(Handler handler);

}
