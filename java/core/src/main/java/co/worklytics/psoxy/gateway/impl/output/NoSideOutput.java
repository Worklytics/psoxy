package co.worklytics.psoxy.gateway.impl.output;

import co.worklytics.psoxy.gateway.HttpEventRequest;
import co.worklytics.psoxy.gateway.ProcessedContent;
import co.worklytics.psoxy.gateway.SideOutput;
import lombok.NoArgsConstructor;

import javax.inject.Inject;

/**
 * a no-op implementation of SideOutput that does nothing.
 */
@NoArgsConstructor(onConstructor_ = {@Inject})
public class NoSideOutput implements SideOutput {

    @Override
    public void write(HttpEventRequest request, ProcessedContent content) {
        // no-op
    }
}
