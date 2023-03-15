package co.worklytics.psoxy;

import co.worklytics.psoxy.gateway.ConfigService;
import co.worklytics.psoxy.rules.CsvRules;
import co.worklytics.psoxy.storage.StorageHandler;
import com.google.common.collect.ImmutableList;
import lombok.SneakyThrows;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class S3HandlerTest {

    List<StorageHandler.ObjectTransform> transformList =
        ImmutableList.<StorageHandler.ObjectTransform>builder()
        .add(StorageHandler.ObjectTransform.of("blah", CsvRules.builder()
                .columnToPseudonymize("something")
            .build()))
        .build();


    @SneakyThrows
    @Test
    public void parseYamlRulesFromConfig() {
        S3Handler s3Handler = new S3Handler();
        s3Handler.yamlMapper = (new PsoxyModule()).providesYAMLObjectMapper();

        ConfigService config = mock(ConfigService.class);
        when(config.getConfigPropertyAsOptional(eq(AWSConfigProperty.ADDITIONAL_TRANSFORMS)))
            .thenReturn(Optional.of(s3Handler.yamlMapper.writeValueAsString(transformList)));

        assertEquals("blah",
        s3Handler.parseAdditionalTransforms(config).get(0).getDestinationBucketName());
        assertEquals("something",
            s3Handler.parseAdditionalTransforms(config).get(0).getRules().getColumnsToPseudonymize().get(0));

    }

}
