package co.worklytics.psoxy.gateway.output;

import dagger.assisted.Assisted;
import dagger.assisted.AssistedFactory;

@AssistedFactory
public interface OutputToSideOutputAdapterFactory {


    /**
     * Creates a {@link ApiDataSideOutput} that adapts the given {@link Output} into a side output.
     *
     * @param wrappedOutput the output to wrap
     * @return a new instance of {@link ApiDataSideOutput}
     */
    OutputToApiDataSideOutputAdapter create(@Assisted Output wrappedOutput);
}
