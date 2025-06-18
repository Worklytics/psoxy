package co.worklytics.psoxy.gateway.output;

import co.worklytics.psoxy.gateway.HttpEventRequest;
import co.worklytics.psoxy.gateway.ProcessedContent;
import co.worklytics.psoxy.gateway.impl.output.OutputUtils;
import dagger.assisted.Assisted;
import dagger.assisted.AssistedInject;

import javax.inject.Inject;
import java.io.IOException;

public class OutputToApiDataSideOutputAdapter implements ApiDataSideOutput {

    final Output wrappedOutput;

    @Inject
    OutputUtils outputUtils;

    @AssistedInject
    public OutputToApiDataSideOutputAdapter(@Assisted Output wrappedOutput) {
        this.wrappedOutput = wrappedOutput;
    }

    @Override
    public void write(HttpEventRequest request, ProcessedContent content) throws IOException {
        wrappedOutput.write(outputUtils.canonicalResponseKey(request), content);
    }
}
