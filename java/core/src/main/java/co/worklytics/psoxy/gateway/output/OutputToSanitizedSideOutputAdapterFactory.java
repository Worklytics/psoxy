package co.worklytics.psoxy.gateway.output;

import dagger.assisted.Assisted;
import dagger.assisted.AssistedFactory;

@AssistedFactory
public interface OutputToSanitizedSideOutputAdapterFactory {


    /**
     * Creates a {@link ApiDataSideOutput} that adapts the given {@link Output} into a side output.
     *
     * @param wrappedOutput the output to wrap
     * @return a new instance of {@link ApiDataSideOutput}
     */
    OutputToSanitizedSideOutputAdapter create(@Assisted Output wrappedOutput);
}
