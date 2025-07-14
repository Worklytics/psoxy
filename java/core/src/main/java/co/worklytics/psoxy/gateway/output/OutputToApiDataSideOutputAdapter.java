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
public class OutputToApiDataSideOutputAdapter implements ApiDataSideOutput {

    final Output wrappedOutput;

    @Inject
    public ApiDataOutputUtils apiDataOutputUtils;

    @AssistedInject
    public OutputToApiDataSideOutputAdapter(@Assisted Output wrappedOutput) {
        this.wrappedOutput = wrappedOutput;
    }

    @Override
    public void writeRaw(ProcessedContent content,
            ApiDataRequestHandler.ProcessingContext processingContext) throws IOException {
        String key = apiDataOutputUtils.buildRawOutputKey(processingContext);
        wrappedOutput.write(key, content);
    }

    @Override
    public void writeSanitized(ProcessedContent sanitizedContent,
            ApiDataRequestHandler.ProcessingContext processingContext) throws IOException {
        String key = apiDataOutputUtils.buildSanitizedOutputKey(processingContext);

        // TODO: enforce no sensitive data in sanitized output metadata ??

        wrappedOutput.write(key, sanitizedContent);
    }

}
