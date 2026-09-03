# API Call Examples for Slack Discovery

**Connector ID:** `slack-discovery-api`

Example commands (\*) that you can use to validate proxy behavior against the Slack Discovery APIs. Follow the steps and change the values to match your configuration when needed.

Path / query parameters:

- `{WORKSPACE_ID}`: a workspace/team id from `GET /api/discovery.enterprise.info` (`.enterprise.teams[].id`).
- `{CHANNEL_ID}`: a channel id from `GET /api/discovery.conversations.list` (`.channels[].id`).
- `{USER_ID}`: a user id from `GET /api/discovery.users.list` (`.users[].id`); required by `discovery.user.conversations`.

`discovery.conversations.info` allows only `channel`, `team`, and `offset` — do not pass `limit`.

For AWS, change the role to assume with one with sufficient permissions to call the proxy (`-r` flag). Example:

```shell
node tools/psoxy-test/cli-call.js -u [your_psoxy_url]/api/discovery.enterprise.info -r arn:aws:iam::PROJECT_ID:role/ROLE_NAME
```

If any call appears to fail, repeat it using the `-v` flag.

(\*) All commands assume that you are at the root path of the Psoxy project.

### Read workspaces

```shell
node tools/psoxy-test/cli-call.js -u [your_psoxy_url]/api/discovery.enterprise.info
```

### Read Users in Grid

```shell
node tools/psoxy-test/cli-call.js -u [your_psoxy_url]/api/discovery.users.list?include_deleted=true
```

### Read conversations for a user

```shell
node tools/psoxy-test/cli-call.js -u [your_psoxy_url]/api/discovery.user.conversations?user={USER_ID}&limit=10
```

### Read Conversations in Workspace (any kind, public and private)

1. Get a workspace ID (accessor path in response `.enterprise.teams[0].id`):

```shell
node tools/psoxy-test/cli-call.js -u [your_psoxy_url]/api/discovery.enterprise.info?limit=1
```

2. Get conversation details of that workspace (replace `{WORKSPACE_ID}` with the corresponding value):

```shell
node tools/psoxy-test/cli-call.js -u [your_psoxy_url]/api/discovery.conversations.list?team={WORKSPACE_ID}&limit=10
```

### Read Messages in Workspace Channel

1. Get a channel ID (accessor path in response `.channels[0].id`):

```shell
node tools/psoxy-test/cli-call.js -u [your_psoxy_url]/api/discovery.conversations.list?team={WORKSPACE_ID}&limit=10
```

2. Get DM information (no workspace):

```shell
node tools/psoxy-test/cli-call.js -u [your_psoxy_url]/api/discovery.conversations.list?limit=10
```

3. Read messages for a workspace channel:

```shell
node tools/psoxy-test/cli-call.js -u [your_psoxy_url]/api/discovery.conversations.history?reactions=1&team={WORKSPACE_ID}&channel={CHANNEL_ID}&limit=10
```

4. Omit the workspace ID if the channel is a DM:

```shell
node tools/psoxy-test/cli-call.js -u [your_psoxy_url]/api/discovery.conversations.history?channel={CHANNEL_ID}&limit=10
```

### Workspace Channel Info

```shell
node tools/psoxy-test/cli-call.js -u [your_psoxy_url]/api/discovery.conversations.info?team={WORKSPACE_ID}&channel={CHANNEL_ID}
```

Omit the workspace ID if the channel is a DM:

```shell
node tools/psoxy-test/cli-call.js -u [your_psoxy_url]/api/discovery.conversations.info?channel={CHANNEL_ID}
```

### Recent Workspace Conversations

```shell
node tools/psoxy-test/cli-call.js -u [your_psoxy_url]/api/discovery.conversations.recent
```
