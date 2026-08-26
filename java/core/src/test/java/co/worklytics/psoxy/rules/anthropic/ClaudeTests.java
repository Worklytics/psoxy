package co.worklytics.psoxy.rules.anthropic;

import co.worklytics.psoxy.rules.JavaRulesTestBaseCase;
import co.worklytics.psoxy.rules.RESTRules;
import lombok.Getter;

import java.util.stream.Stream;

@Getter
public class ClaudeTests extends JavaRulesTestBaseCase {

    final RESTRules rulesUnderTest = PrebuiltSanitizerRules.CLAUDE;

    final RulesTestSpec rulesTestSpec = RulesTestSpec.builder()
        .defaultScopeId("claude")
        .sourceKind("claude")
        .exampleApiResponsesDirectoryPath("example-api-responses/original/")
        .exampleSanitizedApiResponsesPath("example-api-responses/sanitized/")
        .sourceFamily("anthropic")
        .checkUncompressedSSMLength(false)
        .build();

    @Override
    public Stream<InvocationExample> getExamples() {
        return Stream.of(
            // Compliance Activities endpoint
            InvocationExample.of("https://api.anthropic.com/v1/compliance/activities", "activities-response.json"),
            InvocationExample.of("https://api.anthropic.com/v1/compliance/activities?limit=100", "activities-response.json"),
            InvocationExample.of("https://api.anthropic.com/v1/compliance/activities?after_id=act_123&limit=50", "activities-response.json"),
            InvocationExample.of("https://api.anthropic.com/v1/compliance/activities?before_id=act_456&limit=50", "activities-response.json"),
            InvocationExample.of("https://api.anthropic.com/v1/compliance/activities?organization_ids[]=org_1&actor_ids[]=user_1&created_at.gte=2024-01-01T00:00:00Z", "activities-response.json"),
            InvocationExample.of("https://api.anthropic.com/v1/compliance/activities?activity_types[]=claude_chat_created&created_at.gt=2024-01-01T00:00:00Z&created_at.lte=2024-12-31T00:00:00Z", "activities-response.json"),
            InvocationExample.of("https://api.anthropic.com/v1/compliance/activities?created_at.gte=2024-01-01T00:00:00Z&created_at.lt=2024-12-31T00:00:00Z", "activities-response.json"),

            // Apps Chats endpoint
            InvocationExample.of("https://api.anthropic.com/v1/compliance/apps/chats", "chats-response.json"),
            InvocationExample.of("https://api.anthropic.com/v1/compliance/apps/chats?limit=50", "chats-response.json"),
            InvocationExample.of("https://api.anthropic.com/v1/compliance/apps/chats?after_id=chat_123&organization_ids[]=org_1", "chats-response.json"),
            InvocationExample.of("https://api.anthropic.com/v1/compliance/apps/chats?before_id=chat_456&limit=25", "chats-response.json"),
            InvocationExample.of("https://api.anthropic.com/v1/compliance/apps/chats?project_ids[]=proj_1&user_ids[]=user_1&created_at.gte=2024-01-01T00:00:00Z&updated_at.lte=2024-12-31T00:00:00Z", "chats-response.json"),
            InvocationExample.of("https://api.anthropic.com/v1/compliance/apps/chats?created_at.gt=2024-01-01T00:00:00Z&created_at.lt=2024-12-31T00:00:00Z", "chats-response.json"),
            InvocationExample.of("https://api.anthropic.com/v1/compliance/apps/chats?updated_at.gte=2024-01-01T00:00:00Z&updated_at.gt=2024-01-01T00:00:00Z", "chats-response.json"),

            // Chat Messages endpoint
            InvocationExample.of("https://api.anthropic.com/v1/compliance/apps/chats/chat_abc123/messages", "chat-messages-response.json"),
            InvocationExample.of("https://api.anthropic.com/v1/compliance/apps/chats/chat_abc123/messages?limit=50", "chat-messages-response.json"),
            InvocationExample.of("https://api.anthropic.com/v1/compliance/apps/chats/chat_abc123/messages?after_id=msg_123&limit=100", "chat-messages-response.json"),
            InvocationExample.of("https://api.anthropic.com/v1/compliance/apps/chats/chat_abc123/messages?before_id=msg_456&order=asc", "chat-messages-response.json"),
            InvocationExample.of("https://api.anthropic.com/v1/compliance/apps/chats/chat_abc123/messages?order=desc&limit=25", "chat-messages-response.json"),
            InvocationExample.of("https://api.anthropic.com/v1/compliance/apps/chats/chat_abc123/messages?created_at.gte=2024-01-01T00:00:00Z", "chat-messages-response.json"),
            InvocationExample.of("https://api.anthropic.com/v1/compliance/apps/chats/chat_abc123/messages?created_at.gt=2024-01-01T00:00:00Z&created_at.lt=2024-12-31T00:00:00Z", "chat-messages-response.json"),
            InvocationExample.of("https://api.anthropic.com/v1/compliance/apps/chats/chat_abc123/messages?created_at.gte=2024-01-01T00:00:00Z&created_at.lte=2024-12-31T00:00:00Z&limit=100", "chat-messages-response.json"),
            InvocationExample.of("https://api.anthropic.com/v1/compliance/apps/chats/chat_abc123/messages?updated_at.gte=2024-06-01T00:00:00Z", "chat-messages-response.json"),
            InvocationExample.of("https://api.anthropic.com/v1/compliance/apps/chats/chat_abc123/messages?updated_at.gt=2024-06-01T00:00:00Z&updated_at.lt=2024-06-30T00:00:00Z", "chat-messages-response.json"),
            InvocationExample.of("https://api.anthropic.com/v1/compliance/apps/chats/chat_abc123/messages?updated_at.gte=2024-06-01T00:00:00Z&updated_at.lte=2024-06-30T00:00:00Z", "chat-messages-response.json"),
            InvocationExample.of("https://api.anthropic.com/v1/compliance/apps/chats/chat_abc123/messages?tool_result_max_chars=1000", "chat-messages-response.json"),
            InvocationExample.of("https://api.anthropic.com/v1/compliance/apps/chats/chat_abc123/messages?tool_use_input_max_chars=500", "chat-messages-response.json"),
            InvocationExample.of("https://api.anthropic.com/v1/compliance/apps/chats/chat_abc123/messages?tool_result_max_chars=1000&tool_use_input_max_chars=500&limit=50", "chat-messages-response.json"),
            InvocationExample.of("https://api.anthropic.com/v1/compliance/apps/chats/chat_abc123/messages?after_id=msg_789&created_at.gte=2024-01-01T00:00:00Z&limit=50&order=asc", "chat-messages-response.json"),

            // Local Sessions endpoint
            InvocationExample.of("https://api.anthropic.com/v1/compliance/apps/sessions/local", "local-sessions-response.json"),
            InvocationExample.of("https://api.anthropic.com/v1/compliance/apps/sessions/local?limit=100", "local-sessions-response.json"),
            InvocationExample.of("https://api.anthropic.com/v1/compliance/apps/sessions/local?page=page_abc123", "local-sessions-response.json"),
            InvocationExample.of("https://api.anthropic.com/v1/compliance/apps/sessions/local?created_at.gte=2024-01-01T00:00:00Z", "local-sessions-response.json"),
            InvocationExample.of("https://api.anthropic.com/v1/compliance/apps/sessions/local?created_at.lt=2024-12-31T00:00:00Z", "local-sessions-response.json"),
            InvocationExample.of("https://api.anthropic.com/v1/compliance/apps/sessions/local?created_at.gte=2024-01-01T00:00:00Z&created_at.lt=2024-12-31T00:00:00Z&limit=50", "local-sessions-response.json"),

            // Local Session Messages endpoint
            InvocationExample.of("https://api.anthropic.com/v1/compliance/apps/sessions/local/clls_abc123/messages", "local-session-messages-response.json"),
            InvocationExample.of("https://api.anthropic.com/v1/compliance/apps/sessions/local/clls_abc123/messages?limit=100", "local-session-messages-response.json"),
            InvocationExample.of("https://api.anthropic.com/v1/compliance/apps/sessions/local/clls_abc123/messages?order=desc", "local-session-messages-response.json"),
            InvocationExample.of("https://api.anthropic.com/v1/compliance/apps/sessions/local/clls_abc123/messages?page=page_def456", "local-session-messages-response.json"),
            InvocationExample.of("https://api.anthropic.com/v1/compliance/apps/sessions/local/clls_abc123/messages?tool_result_max_bytes=1000", "local-session-messages-response.json"),
            InvocationExample.of("https://api.anthropic.com/v1/compliance/apps/sessions/local/clls_abc123/messages?tool_use_input_max_bytes=500", "local-session-messages-response.json"),
            InvocationExample.of("https://api.anthropic.com/v1/compliance/apps/sessions/local/clls_abc123/messages?order=asc&limit=50&tool_result_max_bytes=-1&tool_use_input_max_bytes=-1", "local-session-messages-response.json"),

            // Remote Sessions endpoint
            InvocationExample.of("https://api.anthropic.com/v1/compliance/apps/sessions/remote", "remote-sessions-response.json"),
            InvocationExample.of("https://api.anthropic.com/v1/compliance/apps/sessions/remote?limit=100", "remote-sessions-response.json"),
            InvocationExample.of("https://api.anthropic.com/v1/compliance/apps/sessions/remote?page=page_ghi789", "remote-sessions-response.json"),
            InvocationExample.of("https://api.anthropic.com/v1/compliance/apps/sessions/remote?organization_ids[]=org_1", "remote-sessions-response.json"),
            InvocationExample.of("https://api.anthropic.com/v1/compliance/apps/sessions/remote?user_ids[]=user_1", "remote-sessions-response.json"),
            InvocationExample.of("https://api.anthropic.com/v1/compliance/apps/sessions/remote?created_at.gte=2024-01-01T00:00:00Z&created_at.lte=2024-12-31T00:00:00Z", "remote-sessions-response.json"),
            InvocationExample.of("https://api.anthropic.com/v1/compliance/apps/sessions/remote?created_at.gt=2024-01-01T00:00:00Z&created_at.lt=2024-12-31T00:00:00Z", "remote-sessions-response.json"),

            // Remote Session Messages endpoint
            InvocationExample.of("https://api.anthropic.com/v1/compliance/apps/sessions/remote/cse_abc123/messages", "remote-session-messages-response.json"),
            InvocationExample.of("https://api.anthropic.com/v1/compliance/apps/sessions/remote/cse_abc123/messages?limit=100", "remote-session-messages-response.json"),
            InvocationExample.of("https://api.anthropic.com/v1/compliance/apps/sessions/remote/cse_abc123/messages?order=desc", "remote-session-messages-response.json"),
            InvocationExample.of("https://api.anthropic.com/v1/compliance/apps/sessions/remote/cse_abc123/messages?page=page_jkl012", "remote-session-messages-response.json"),
            InvocationExample.of("https://api.anthropic.com/v1/compliance/apps/sessions/remote/cse_abc123/messages?tool_result_max_bytes=1000&tool_use_input_max_bytes=500", "remote-session-messages-response.json"),

            // Organizations endpoint
            InvocationExample.of("https://api.anthropic.com/v1/compliance/organizations", "organizations-response.json"),

            // Organization Users endpoint
            InvocationExample.of("https://api.anthropic.com/v1/compliance/organizations/org_uuid_123/users", "organization-users-response.json"),
            InvocationExample.of("https://api.anthropic.com/v1/compliance/organizations/org_uuid_123/users?page=2&limit=100", "organization-users-response.json"),
            InvocationExample.of("https://api.anthropic.com/v1/compliance/organizations/org_uuid_123/users?page=3&limit=50", "organization-users-response.json")
        );
    }
}
