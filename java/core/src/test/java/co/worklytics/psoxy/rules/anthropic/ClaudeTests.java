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
            InvocationExample.of("https://api.anthropic.com/v1/compliance/activities?organization_ids[]=org_1&actor_ids[]=user_1&created_at.gte=2024-01-01T00:00:00Z", "activities-response.json"),

            // Apps Chats endpoint
            InvocationExample.of("https://api.anthropic.com/v1/compliance/apps/chats", "chats-response.json"),
            InvocationExample.of("https://api.anthropic.com/v1/compliance/apps/chats?limit=50", "chats-response.json"),
            InvocationExample.of("https://api.anthropic.com/v1/compliance/apps/chats?after_id=chat_123&organization_ids[]=org_1", "chats-response.json"),
            InvocationExample.of("https://api.anthropic.com/v1/compliance/apps/chats?project_ids[]=proj_1&user_ids[]=user_1&created_at.gte=2024-01-01T00:00:00Z&updated_at.lte=2024-12-31T00:00:00Z", "chats-response.json"),

            // Chat Messages endpoint
            InvocationExample.of("https://api.anthropic.com/v1/compliance/apps/chats/chat_abc123/messages", "chat-messages-response.json"),

            // Organizations endpoint
            InvocationExample.of("https://api.anthropic.com/v1/compliance/organizations", "organizations-response.json"),
            InvocationExample.of("https://api.anthropic.com/v1/compliance/organizations?page=1&limit=20", "organizations-response.json"),

            // Organization Users endpoint
            InvocationExample.of("https://api.anthropic.com/v1/compliance/organizations/org_uuid_123/users", "organization-users-response.json"),
            InvocationExample.of("https://api.anthropic.com/v1/compliance/organizations/org_uuid_123/users?page=2&limit=100", "organization-users-response.json")
        );
    }
}
