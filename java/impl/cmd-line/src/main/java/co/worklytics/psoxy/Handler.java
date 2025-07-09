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
import java.io.*;

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

        try (BufferedReader in =  new BufferedReader(new FileReader(inputFile));
             Writer writer = new AppendableWriter(out)) {
            sanitizer.sanitize(in, writer, pseudonymizer);
        }
    }

    static class AppendableWriter extends java.io.Writer {
        private final Appendable appendable;

        public AppendableWriter(Appendable appendable) {
            this.appendable = appendable;
        }

        @Override
        public void write(char[] cbuf, int off, int len) throws IOException {
            appendable.append(new String(cbuf, off, len));
        }

        @Override
        public void flush() throws IOException {
            // No need to flush for Appendable
        }

        @Override
        public void close() throws IOException {
            // No need to close for Appendable
        }
    }
}
