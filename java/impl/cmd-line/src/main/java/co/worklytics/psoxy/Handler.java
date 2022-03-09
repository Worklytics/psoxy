package co.worklytics.psoxy;

import co.worklytics.psoxy.storage.FileHandlerFactory;
import com.fasterxml.jackson.databind.ObjectMapper;
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

    @Inject ObjectMapper jsonMapper;
    @Inject SanitizerFactory sanitizerFactory;
    @Inject
    FileHandlerFactory fileHandlerStrategy;

    @SneakyThrows
    public void pseudonymize(@NonNull Config config,
                             @NonNull File inputFile,
                             @NonNull Appendable out) {
        Sanitizer.Options.OptionsBuilder options = Sanitizer.Options.builder()
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

        options.rules(Rules.builder()
                .pseudonymization(Rules.Rule.builder()
                        .csvColumns(Lists.newArrayList(config.getColumnsToPseudonymize()))
                        .build())
                .redaction(Rules.Rule.builder()
                        .csvColumns(Lists.newArrayList(config.getColumnsToRedact()))
                        .build())
                .build());

        Sanitizer sanitizer = sanitizerFactory.create(options.build());

        try (FileReader in = new FileReader(inputFile)) {
            out.append(new String(fileHandlerStrategy.get(inputFile.getName()).handle(in, sanitizer)));
        }
    }
}
