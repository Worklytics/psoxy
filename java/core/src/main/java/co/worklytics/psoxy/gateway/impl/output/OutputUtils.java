package co.worklytics.psoxy.gateway.impl.output;

import java.util.Set;
import javax.inject.Inject;
import javax.inject.Provider;
import javax.inject.Singleton;

import co.worklytics.psoxy.gateway.output.*;
import org.apache.commons.lang3.StringUtils;
import com.google.common.annotations.VisibleForTesting;
import co.worklytics.psoxy.gateway.ApiModeConfigProperty;
import co.worklytics.psoxy.gateway.ConfigService;
import co.worklytics.psoxy.gateway.ProxyConfigProperty;
import co.worklytics.psoxy.gateway.WebhookCollectorModeConfigProperty;
import lombok.NoArgsConstructor;
import lombok.extern.java.Log;

/**
 * utility methods for working with side output
 * <p>
 * - eg, to create a canonical key for the response to be stored in the side output
 * - or to build metadata for the side output
 * </p>
 */
@Log
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
    @Inject
    OutputToSanitizedSideOutputAdapterFactory outputToSanitizedSideOutputAdapterFactory;

    public ApiDataSideOutput originalSideOutput() {
        Output outputToAdapt = fromConfigProperty( ProxyConfigProperty.SIDE_OUTPUT_ORIGINAL);
        return outputToSideOutputAdapterFactory.create(outputToAdapt);
    }

    public ApiSanitizedDataOutput sanitizedSideOutput() {
        Output outputToAdapt = fromConfigProperty(ProxyConfigProperty.SIDE_OUTPUT_SANITIZED);
        return outputToSanitizedSideOutputAdapterFactory.create(outputToAdapt);
    }

    public ApiSanitizedDataOutput asyncOutput() {
        Output asyncOutput =  fromConfigProperty(ApiModeConfigProperty.ASYNC_OUTPUT_DESTINATION);
        return outputToSanitizedSideOutputAdapterFactory.create(asyncOutput);
    }

    private Output fromConfigProperty(ConfigService.ConfigProperty property) {
        return configService.getConfigPropertyAsOptional(property)
            .map(OutputLocationImpl::of)
            .map(this::createOutputForLocation)
            .map(output -> (Output) CompressedOutputWrapper.wrap((Output) output))
            .orElseGet(noSideProvider::get);
    }

    public <T extends Output> T forIncomingWebhooks() {
        return createOutputForLocation(locationForWebhooks());
    }

    public <T extends Output> T forBatchedWebhookContent() {
        return createOutputForLocation(locationForWebhookQueue());
    }

    @VisibleForTesting
    OutputLocation locationForWebhooks() {
        return configService
                .getConfigPropertyAsOptional(WebhookCollectorModeConfigProperty.WEBHOOK_OUTPUT)
                .map(OutputLocationImpl::of).orElseThrow(
                        () -> new IllegalStateException("No side output configured for webhooks"));
    }

    @VisibleForTesting
    OutputLocation locationForWebhookQueue() {
        return configService
                .getConfigPropertyAsOptional(
                        WebhookCollectorModeConfigProperty.WEBHOOK_BATCH_OUTPUT)
                .map(OutputLocationImpl::of).orElseThrow(() -> new IllegalStateException(
                        "No side output configured for webhook queue"));
    }


    private <T extends Output> T createOutputForLocation(OutputLocation outputLocation) {
        OutputFactory<?> outputFactory = outputFactories.stream()
            .filter(factory -> factory.supports(outputLocation))
            .findFirst()
            .orElseThrow(() -> new IllegalArgumentException("No output factory found for location: " + outputLocation));

        return (T) outputFactory.create(outputLocation);
    }

    // Ensure the path prefix ends with a slash, if non-empty
    public static String formatObjectPathPrefix(String rawPathPrefix) {
        String trimmedPath = StringUtils.trimToEmpty(rawPathPrefix);
        return (trimmedPath.endsWith("/") || StringUtils.isEmpty(trimmedPath)) ? trimmedPath
                : trimmedPath + "/";
    }

}
