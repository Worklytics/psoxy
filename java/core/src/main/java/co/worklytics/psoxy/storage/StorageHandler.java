package co.worklytics.psoxy.storage;

import co.worklytics.psoxy.Rules;
import co.worklytics.psoxy.Sanitizer;
import co.worklytics.psoxy.SanitizerFactory;
import co.worklytics.psoxy.gateway.ConfigService;
import co.worklytics.psoxy.gateway.StorageEventRequest;
import co.worklytics.psoxy.gateway.StorageEventResponse;
import lombok.NoArgsConstructor;
import lombok.SneakyThrows;

import javax.inject.Inject;

@NoArgsConstructor(onConstructor_ = @Inject)
public class StorageHandler {

    @Inject
    ConfigService config;
    @Inject
    Rules rules;
    @Inject
    FileHandlerFactory fileHandlerStrategy;
    @Inject
    SanitizerFactory sanitizerFactory;

    private volatile Sanitizer sanitizer;
    private final Object $writeLock = new Object[0];

    private Sanitizer loadSanitizerRules() {
        if (this.sanitizer == null) {
            synchronized ($writeLock) {
                if (this.sanitizer == null) {
                    this.sanitizer = sanitizerFactory.create(sanitizerFactory.buildOptions(config, rules));
                }
            }
        }
        return this.sanitizer;
    }

    @SneakyThrows
    public StorageEventResponse handle(StorageEventRequest request) {

        FileHandler fileHandler = fileHandlerStrategy.get(request.getSourceObjectPath());
        this.sanitizer = loadSanitizerRules();

        return StorageEventResponse.builder()
                .destinationBucketName(request.getDestinationBucket())
                .bytes(fileHandler.handle(request.getReaderStream(), sanitizer))
                .destinationObjectPath(request.getSourceObjectPath())
                .build();
    }
}
