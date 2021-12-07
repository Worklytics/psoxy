package co.worklytics.psoxy.rules;

import co.worklytics.psoxy.Rules;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import lombok.SneakyThrows;
import org.apache.commons.codec.digest.DigestUtils;

public class RulesUtils {

    ObjectMapper yamlMapper = new ObjectMapper(new YAMLFactory());

    @SneakyThrows
    public String sha(Rules rules) {
        return DigestUtils.sha1Hex(yamlMapper.writeValueAsString(rules));
    }
}
