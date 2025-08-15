package co.worklytics.psoxy.rules.chatgpt;

import co.worklytics.psoxy.rules.JavaRulesTestBaseCase;
import co.worklytics.psoxy.rules.RESTRules;
import lombok.Getter;

import java.util.stream.Stream;

@Getter
public class ChatGPTComplianceTests extends JavaRulesTestBaseCase {

    final RESTRules rulesUnderTest = PrebuiltSanitizerRules.ENTERPRISE;

    final RulesTestSpec rulesTestSpec = RulesTestSpec.builder()
        .defaultScopeId("chatgpt")
        .sourceKind("chatgpt-enterprise")
        .exampleApiResponsesDirectoryPath("example-api-responses/original/")
        .exampleSanitizedApiResponsesPath("example-api-responses/sanitized/")
        .checkUncompressedSSMLength(false)
        .build();

    @Override
    public Stream<InvocationExample> getExamples() {
        return Stream.of(
            InvocationExample.of("https://api.chatgpt.com/v1/compliance/workspaces/some_id/conversations", "conversations.json"),
            InvocationExample.of("https://api.chatgpt.com/v1/compliance/workspaces/some_id/conversations/some_id/messages", "messages.json"),
            InvocationExample.of("https://api.chatgpt.com/v1/compliance/workspaces/some_id/conversations?after=blabla&limit=100", "conversations.json"),
            InvocationExample.of("https://api.chatgpt.com/v1/compliance/workspaces/{workspaceId}/automations", "automations.json"),
            InvocationExample.of("https://api.chatgpt.com/v1/compliance/workspaces/some_id/projects", "projects.json"),
            InvocationExample.of("https://api.chatgpt.com/v1/compliance/workspaces/some_id/projects?after=blabla&limit=100", "projects.json"),
            InvocationExample.of("https://api.chatgpt.com/v1/compliance/workspaces/some_id/users", "users.json"),
            InvocationExample.of("https://api.chatgpt.com/v1/compliance/workspaces/some_id/users?after=blabla&limit=100", "users.json")
        );
    }
}
