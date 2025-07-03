package co.worklytics.psoxy.gateway.impl.output;

import co.worklytics.psoxy.gateway.*;
import co.worklytics.psoxy.gateway.output.*;
import com.google.common.annotations.VisibleForTesting;
import lombok.*;
import org.apache.commons.lang3.StringUtils;

import javax.inject.Inject;
import javax.inject.Provider;
import javax.inject.Singleton;
import java.util.*;

/**
 * utility methods for working with side output
 *
 * - eg, to create a canonical key for the response to be stored in the side output
 * - or to build metadata for the side output
 */
@NoArgsConstructor(onConstructor_ = {@Inject})
@Singleton
public class OutputUtils {

    @Inject
    ConfigService configService;
    @Inject
    Provider<NoOutput> noSideProvider;
    @Inject
    Set<OutputFactory<?>> outputFactories;
    @Inject
    OutputToSideOutputAdapterFactory outputToSideOutputAdapterFactory;

    /**
     * helper method to interpret config, as to whether there's a side output for the given content stage or not
     *
     * @param processedDataStage the stage of processed data to be written to the side output
     */
    public ApiDataSideOutput forStage(ProcessedDataStage processedDataStage) {

        ProxyConfigProperty configProperty = switch (processedDataStage) {
            case ORIGINAL -> ProxyConfigProperty.SIDE_OUTPUT_ORIGINAL;
            case SANITIZED -> ProxyConfigProperty.SIDE_OUTPUT_SANITIZED;
        };

        Output outputToAdapt =  configService.getConfigPropertyAsOptional(configProperty)
            .map(OutputLocationImpl::of)
            .map(this::createOutputForLocation)
            .map(output -> (Output) CompressedOutputWrapper.wrap((Output) output))
            .orElseGet(noSideProvider::get);

        return outputToSideOutputAdapterFactory.create(outputToAdapt);
    }

    public ApiSanitizedDataOutput asyncOutput() {
        Output asyncOutput = configService.getConfigPropertyAsOptional(ProxyConfigProperty.ASYNC_OUTPUT_DESTINATION)
            .map(OutputLocationImpl::of)
            .map(this::createOutputForLocation)
            .map(output -> (Output) CompressedOutputWrapper.wrap((Output) output))
            .orElseGet(noSideProvider::get);

        return outputToSideOutputAdapterFactory.create(asyncOutput);
    }

    public <T extends Output> T forWebhooks() {
        return createOutputForLocation(locationForWebhooks());
    }

    public <T extends Output> T forWebhookQueue() {
        return createOutputForLocation(locationForWebhookQueue());
    }

    @VisibleForTesting
    OutputLocation locationForWebhooks() {
        return configService.getConfigPropertyAsOptional(WebhookCollectorModeConfigProperty.WEBHOOK_OUTPUT)
            .map(OutputLocationImpl::of).orElseThrow(() -> new IllegalStateException("No side output configured for webhooks"));
    }

    @VisibleForTesting
    OutputLocation locationForWebhookQueue() {
        return configService.getConfigPropertyAsOptional(WebhookCollectorModeConfigProperty.WEBHOOK_BATCH_OUTPUT)
            .map(OutputLocationImpl::of)
            .orElseThrow(() -> new IllegalStateException("No side output configured for webhook queue"));
    }

    private <T extends Output> T createOutputForLocation(OutputLocation outputLocation) {
        OutputFactory<?> outputFactory =  outputFactories.stream()
            .filter(factory -> factory.supports(outputLocation))
            .findFirst()
            .orElseThrow(() -> new IllegalArgumentException("No output factory found for location: " + outputLocation));

        return (T) outputFactory.create(outputLocation);
    }

    // Ensure the path prefix ends with a slash, if non-empty
    public static String formatObjectPathPrefix(String rawPathPrefix) {
        String trimmedPath = StringUtils.trimToEmpty(rawPathPrefix);
        return (trimmedPath.endsWith("/") || StringUtils.isEmpty(trimmedPath)) ? trimmedPath : trimmedPath + "/";
    }

}
