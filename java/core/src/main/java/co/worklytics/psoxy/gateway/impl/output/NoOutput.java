package co.worklytics.psoxy.gateway.impl.output;

import co.worklytics.psoxy.gateway.ProcessedContent;
import co.worklytics.psoxy.gateway.output.Output;

public class NoOutput implements Output {
    @Override
    public void write(ProcessedContent ignored) {
        // no-op
    }

    @Override
    public void write(String key, ProcessedContent ignored) {
        // no-op
    }
}
