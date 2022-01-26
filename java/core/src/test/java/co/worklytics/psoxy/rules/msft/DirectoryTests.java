package co.worklytics.psoxy.rules.msft;

import co.worklytics.psoxy.Rules;
import co.worklytics.psoxy.rules.JavaRulesTestBaseCase;
import lombok.Getter;

public class DirectoryTests extends JavaRulesTestBaseCase {

    @Getter
    final Rules rulesUnderTest = PrebuiltSanitzerRules.DIRECTORY;

    @Getter
    final String exampleDirectoryPath = "api-response-examples/microsoft-365/directory";

    @Getter
    final String defaultScopeId = "azure-ad";


    @Getter
    final String yamlSerializationFilepath = "microsoft-365/directory";
}
