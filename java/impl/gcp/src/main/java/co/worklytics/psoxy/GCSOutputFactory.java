package co.worklytics.psoxy;

import co.worklytics.psoxy.gateway.output.OutputFactory;
import co.worklytics.psoxy.gateway.output.OutputLocation;

public interface GCSOutputFactory implements OutputFactory<GCSOutput> {

    public GCSOutput create(OutputLocation outputLocation);

    @Override
    default public boolean supports(OutputLocation outputLocation) {
        return "gcs".equals(outputLocation.getKind());
    }
}
