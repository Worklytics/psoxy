package co.worklytics.psoxy.rules.chatgpt;

import co.worklytics.psoxy.rules.JavaRulesTestBaseCase;
import co.worklytics.psoxy.rules.RESTRules;
import lombok.Getter;

import java.util.stream.Stream;

@Getter
public class ChatGPTComplianceTests extends JavaRulesTestBaseCase {

    final RESTRules rulesUnderTest = PrebuiltSanitizerRules.COMPLIANCE;

    final RulesTestSpec rulesTestSpec = RulesTestSpec.builder()
        .defaultScopeId("chatgpt")
        .sourceKind("chatgpt")
        .rulesFile("compliance/chatgpt-compliance")
        .exampleApiResponsesDirectoryPath("compliance/example-api-responses/original/")
        .exampleSanitizedApiResponsesPath("compliance/example-api-responses/sanitized/")
        .checkUncompressedSSMLength(false)
        .build();

    @Override
    public Stream<InvocationExample> getExamples() {
        return Stream.of(
            InvocationExample.of("https://api.chatgpt.com/compliance/workspaces/some_id/conversations", "conversations.json"),
            InvocationExample.of("https://api.chatgpt.com/compliance/workspaces/some_id/conversations?after=blabla&limit=100", "conversations.json"),
            InvocationExample.of("https://api.chatgpt.com/compliance/workspaces/{workspaceId}/automations", "automations.json"),
            InvocationExample.of("https://api.chatgpt.com/compliance/workspaces/{workspaceId}/conversations/{conversationId}/messages", "conversation-messages.json"),
            InvocationExample.of("https://api.chatgpt.com/compliance/workspaces/{workspaceId}/conversations/{conversationId}/messages?after=blabla&limit=100", "conversation-messages.json"),
            InvocationExample.of("https://api.chatgpt.com/compliance/workspaces/some_id/projects", "projects.json"),
            InvocationExample.of("https://api.chatgpt.com/compliance/workspaces/some_id/projects?after=blabla&limit=100", "projects.json"),
            InvocationExample.of("https://api.chatgpt.com/compliance/workspaces/some_id/users", "users.json"),
            InvocationExample.of("https://api.chatgpt.com/compliance/workspaces/some_id/users?after=blabla&limit=100", "users.json")
        );
    }
}
