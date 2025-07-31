package co.worklytics.psoxy.rules.chatgpt;

import co.worklytics.psoxy.rules.JavaRulesTestBaseCase;
import co.worklytics.psoxy.rules.RESTRules;
import lombok.Getter;

import java.util.stream.Stream;

@Getter
public class ChatGPTComplianceTests extends JavaRulesTestBaseCase {

    final RESTRules rulesUnderTest = PrebuiltSanitizerRules.COMPLIANCE;

    final RulesTestSpec rulesTestSpec = RulesTestSpec.builder()
        .defaultScopeId("chat-gpt")
        .sourceKind("chat-gpt")
        .rulesFile("compliance/chat-gpt-compliance")
        .exampleApiResponsesDirectoryPath("compliance/example-api-responses/original/")
        .exampleSanitizedApiResponsesPath("compliance/example-api-responses/sanitized/")
        .checkUncompressedSSMLength(false)
        .build();

    @Override
    public Stream<InvocationExample> getExamples() {
        return Stream.of(
            InvocationExample.of("https://api.chatgpt.com/compliance/workspaces/some_id/conversations", "conversations.json"),
            InvocationExample.of("https://api.chatgpt.com/compliance/workspaces/some_id/conversations?after=blabla&limit=100", "conversations.json")
        );
    }
}
