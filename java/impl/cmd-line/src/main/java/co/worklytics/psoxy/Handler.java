package co.worklytics.psoxy;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.OutputStream;
import javax.inject.Inject;
import com.avaulta.gateway.rules.ColumnarRules;
import com.google.api.client.util.Lists;
import co.worklytics.psoxy.storage.BulkDataSanitizer;
import co.worklytics.psoxy.storage.BulkDataSanitizerFactory;
import lombok.NonNull;
import lombok.SneakyThrows;

//@NoArgsConstructor(onConstructor_ = @Inject) //q: compile complaints - lombok annotation processing not reliable??
public class Handler {

    @Inject
    public Handler() {

    }

    @Inject
    BulkDataSanitizerFactory fileHandlerStrategy;
    @Inject
    PseudonymizerImplFactory pseudonymizerImplFactory;


    //visible for testing
    ColumnarRules defaultRules = ColumnarRules.builder().build();

    @SneakyThrows
    public void sanitize(@NonNull Config config,
                         @NonNull File inputFile,
                         @NonNull OutputStream out) {


        Pseudonymizer.ConfigurationOptions.ConfigurationOptionsBuilder options =
            Pseudonymizer.ConfigurationOptions.builder();

        ColumnarRules.ColumnarRulesBuilder<?, ?> rulesBuilder = defaultRules.toBuilder();

        if (config.getColumnsToPseudonymize() != null) {
            rulesBuilder.columnsToPseudonymize(Lists.newArrayList(config.getColumnsToPseudonymize()));
        }
        if (config.getColumnsToRedact() != null) {
            rulesBuilder.columnsToRedact(Lists.newArrayList(config.getColumnsToRedact()));
        }

        ColumnarRules rules = rulesBuilder.build();

        Pseudonymizer pseudonymizer = pseudonymizerImplFactory.create(options.build());
        BulkDataSanitizer sanitizer = fileHandlerStrategy.get(rules);

        try (InputStream in = new FileInputStream(inputFile)) {

            String contentType = java.net.URLConnection.guessContentTypeFromName(inputFile.getName());

            co.worklytics.psoxy.gateway.StorageEventRequest request =
                co.worklytics.psoxy.gateway.StorageEventRequest.builder()
                .sourceBucketName("local-file")
                .sourceObjectPath(inputFile.getName())
                .destinationBucketName("local-output")
                .destinationObjectPath(inputFile.getName() + "-sanitized")
                .contentType(contentType)
                .build();

            sanitizer.sanitize(request, in, out, pseudonymizer);
        }
    }
}
