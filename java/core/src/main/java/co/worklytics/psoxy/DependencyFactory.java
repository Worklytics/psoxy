package co.worklytics.psoxy;

import co.worklytics.psoxy.gateway.ConfigService;
import co.worklytics.psoxy.gateway.SourceAuthStrategy;
import co.worklytics.psoxy.rules.RulesUtils;
import com.fasterxml.jackson.databind.ObjectMapper;

public interface DependencyFactory {

    ConfigService getConfig();

    SourceAuthStrategy getSourceAuthStrategy();

    ObjectMapper getObjectMapper();

    RulesUtils getRulesUtils();

    Sanitizer getSanitizer();
}
