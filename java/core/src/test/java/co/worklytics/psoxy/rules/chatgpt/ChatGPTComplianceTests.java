package co.worklytics.psoxy.rules.chatgpt;

import co.worklytics.psoxy.rules.JavaRulesTestBaseCase;
import co.worklytics.psoxy.rules.RESTRules;
import lombok.Getter;
import org.junit.jupiter.api.Test;

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
            InvocationExample.of("https://api.chatgpt.com/v1/compliance/workspaces/some_id/conversations/some_id/messages", "conversation-messages.json"),
            InvocationExample.of("https://api.chatgpt.com/v1/compliance/workspaces/some_id/conversations?after=blabla&limit=100", "conversations.json"),
            InvocationExample.of("https://api.chatgpt.com/v1/compliance/workspaces/some_id/logs", "logs.json"),
            InvocationExample.of("https://api.chatgpt.com/v1/compliance/workspaces/some_id/logs?event_type=something&after=after&limit=limit&before=before", "logs.json"),
            InvocationExample.of("https://api.chatgpt.com/v1/compliance/workspaces/some_id/logs/some_id", "sample_log_message.json"),
            InvocationExample.of("https://api.chatgpt.com/v1/compliance/workspaces/some_id/logs/some_id", "codex_log_message.json"),
            InvocationExample.of("https://api.chatgpt.com/v1/compliance/workspaces/some_id/logs/some_id", "app_log_message.json"),
            InvocationExample.of("https://api.chatgpt.com/v1/compliance/workspaces/some_id/logs/some_id", "chatgpt_plugin_spreadsheet_log_message.json"),
            InvocationExample.of("https://api.chatgpt.com/v1/compliance/workspaces/some_id/logs/some_id", "chatgpt_plugin_presentation_log_message.json"),
            InvocationExample.of("https://api.chatgpt.com/v1/compliance/workspaces/some_id/logs/some_id", "chatgpt_plugin_document_log_message.json"),
            InvocationExample.of("https://api.chatgpt.com/v1/compliance/workspaces/some_id/logs/some_id", "custom_agents_log_message.json"),
            InvocationExample.of("https://api.chatgpt.com/v1/compliance/workspaces/some_id/logs/some_id", "codex_tool_call_log_message.json"),
            InvocationExample.of("https://api.chatgpt.com/v1/compliance/workspaces/some_id/logs/some_id", "codex_mcp_result_log_message.json"),
            InvocationExample.of("https://api.chatgpt.com/v1/compliance/workspaces/some_id/logs/some_id", "codex_site_deployment_log_message.json"),
            InvocationExample.of("https://api.chatgpt.com/v1/compliance/workspaces/some_id/logs/some_id", "custom_agents_published_log_message.json"),
            InvocationExample.of("https://api.chatgpt.com/v1/compliance/workspaces/some_id/logs/some_id", "codex_tool_decision_log_message.json"),
            InvocationExample.of("https://api.chatgpt.com/v1/compliance/workspaces/some_id/logs/some_id", "codex_execution_requested_log_message.json"),
            InvocationExample.of("https://api.chatgpt.com/v1/compliance/workspaces/{workspaceId}/automations", "automations.json"),
            InvocationExample.of("https://api.chatgpt.com/v1/compliance/workspaces/some_id/projects", "projects.json"),
            InvocationExample.of("https://api.chatgpt.com/v1/compliance/workspaces/some_id/projects?after=blabla&limit=100", "projects.json"),
            InvocationExample.of("https://api.chatgpt.com/v1/compliance/workspaces/some_id/users", "users.json"),
            InvocationExample.of("https://api.chatgpt.com/v1/compliance/workspaces/some_id/users?after=blabla&limit=100", "users.json")
        );
    }

    @Test
    void sanitizesRoleBasedUserMessageAuthorshipInLogs() {
        String original = asJson("sample_log_message.json")
            .replace("\"type\": \"user\",\n      \"client_type\": \"unknown\"", "\"role\": \"user\",\n      \"client_type\": \"unknown\"");

        String sanitized = sanitize("https://api.chatgpt.com/v1/compliance/workspaces/some_id/logs/some_id", original);

        assertRedacted(sanitized,
            "Can you summarize the Q2 financial results for the EMEA region?",
            "Q2 financial results");
    }

}
