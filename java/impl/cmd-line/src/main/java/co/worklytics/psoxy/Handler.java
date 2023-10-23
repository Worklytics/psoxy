package co.worklytics.psoxy;

import co.worklytics.psoxy.storage.BulkDataSanitizer;
import co.worklytics.psoxy.storage.BulkDataSanitizerFactory;
import com.avaulta.gateway.rules.ColumnarRules;
import com.google.api.client.util.Lists;
import com.google.cloud.secretmanager.v1.AccessSecretVersionResponse;
import com.google.cloud.secretmanager.v1.SecretManagerServiceClient;
import lombok.NonNull;
import lombok.SneakyThrows;

import javax.inject.Inject;
import java.io.File;
import java.io.FileReader;

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
                         @NonNull Appendable out) {


        Pseudonymizer.ConfigurationOptions.ConfigurationOptionsBuilder options =
            Pseudonymizer.ConfigurationOptions.builder()
            .defaultScopeId(config.getDefaultScopeId());

        if (config.getPseudonymizationSaltSecret() != null) {
            //TODO: platform dependent; inject
            try (SecretManagerServiceClient client = SecretManagerServiceClient.create()) {
                AccessSecretVersionResponse secretVersionResponse =
                    client.accessSecretVersion(config.getPseudonymizationSaltSecret().getIdentifier());
                options.pseudonymizationSalt(secretVersionResponse.getPayload().getData().toStringUtf8());
            }
        } else {
            options.pseudonymizationSalt(config.getPseudonymizationSalt());
        }

        ColumnarRules rules = defaultRules.toBuilder()
                .columnsToPseudonymize(Lists.newArrayList(config.getColumnsToPseudonymize()))
                .columnsToRedact(Lists.newArrayList(config.getColumnsToRedact()))
                .build();


        Pseudonymizer pseudonymizer = pseudonymizerImplFactory.create(options.build());
        BulkDataSanitizer sanitizer = fileHandlerStrategy.get(rules);

        try (FileReader in = new FileReader(inputFile)) {
            out.append(new String(sanitizer.sanitize(in, pseudonymizer)));
        }
    }
}
