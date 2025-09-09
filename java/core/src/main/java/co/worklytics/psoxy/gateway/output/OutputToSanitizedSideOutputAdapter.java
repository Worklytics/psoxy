package co.worklytics.psoxy.gateway.output;

import co.worklytics.psoxy.gateway.ProcessedContent;
import co.worklytics.psoxy.gateway.impl.ApiDataRequestHandler;
import dagger.assisted.Assisted;
import dagger.assisted.AssistedInject;

import javax.inject.Inject;
import java.io.IOException;

/**
 * an adapter that takes an {@link Output} and wraps it to be used as an {@link ApiDataSideOutput}.
 */
public class OutputToSanitizedSideOutputAdapter implements ApiSanitizedDataOutput {

    final Output wrappedOutput;

    @Inject
    public ApiDataOutputUtils apiDataOutputUtils;

    @AssistedInject
    public OutputToSanitizedSideOutputAdapter(@Assisted Output wrappedOutput) {
        this.wrappedOutput = wrappedOutput;
    }

    @Override
    public void writeSanitized(ProcessedContent sanitizedContent,
            ApiDataRequestHandler.ProcessingContext processingContext) throws IOException {
        String key = apiDataOutputUtils.buildSanitizedOutputKey(processingContext);

        // TODO: enforce no sensitive data in sanitized output metadata ??

        wrappedOutput.write(key, sanitizedContent);
    }

    @Override
    public boolean hasSanitizedOutput() {
        return true;
    }
}
