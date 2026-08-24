# API Call Examples for Slack Analytics

Example commands (\*) that you can use to validate proxy behavior against the Slack Admin Analytics APIs. Follow the steps and change the values to match your configuration when needed.

Path / query parameters:

- `{channelId}`: a Slack channel id from `GET /api/admin.analytics.getFile?type=public_channel&metadata_only=true` (or any public channel id your token can read). Required by `admin.analytics.messages.metadata` and `admin.analytics.messages.activity`.

For AWS, change the role to assume with one with sufficient permissions to call the proxy (`-r` flag). Example:

```shell
node tools/psoxy-test/cli-call.js -u [your_psoxy_url]/api/admin.analytics.getFile?type=member&date=2024-01-15 -r arn:aws:iam::PROJECT_ID:role/ROLE_NAME
```

If any call appears to fail, repeat it using the `-v` flag.

(\*) All commands assume that you are at the root path of the Psoxy project.

### Member analytics file

```shell
node tools/psoxy-test/cli-call.js -u [your_psoxy_url]/api/admin.analytics.getFile?type=member&date=2024-01-15
```

### Public channel metadata

```shell
node tools/psoxy-test/cli-call.js -u [your_psoxy_url]/api/admin.analytics.getFile?type=public_channel&metadata_only=true
```

Pull a channel id (`{channelId}`) from that metadata file, then:

### Message metadata for a channel

```shell
node tools/psoxy-test/cli-call.js -u [your_psoxy_url]/api/admin.analytics.messages.metadata?channel={channelId}&limit=100
```

### Message activity for a channel

```shell
node tools/psoxy-test/cli-call.js -u [your_psoxy_url]/api/admin.analytics.messages.activity?channel={channelId}&limit=50
```
