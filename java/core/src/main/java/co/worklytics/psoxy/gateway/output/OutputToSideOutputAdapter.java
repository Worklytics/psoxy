package co.worklytics.psoxy.gateway.output;

import java.io.IOException;
import javax.inject.Inject;
import co.worklytics.psoxy.gateway.ProcessedContent;
import co.worklytics.psoxy.gateway.impl.ApiDataRequestHandler;
import dagger.assisted.Assisted;
import dagger.assisted.AssistedInject;

/**
 * an adapter that takes an {@link Output} and wraps it to be used as an {@link ApiDataSideOutput}.
 */
public class OutputToSideOutputAdapter implements ApiDataSideOutput {

    final Output wrappedOutput;

    @Inject
    public ApiDataOutputUtils apiDataOutputUtils;

    @AssistedInject
    public OutputToSideOutputAdapter(@Assisted Output wrappedOutput) {
        this.wrappedOutput = wrappedOutput;
    }

    @Override
    public void writeRaw(ProcessedContent content, ApiDataRequestHandler.ProcessingContext processingContext) throws IOException {
            String key = apiDataOutputUtils.buildSanitizedOutputKey(processingContext);

            // TODO: enforce no sensitive data in sanitized output metadata ??

            wrappedOutput.write(key, content);
    }

    @Override
    public boolean hasRawOutput() {
        return true;
    }
}
