# Cursor

**ALPHA**; may change in backwards incompatible ways. We are NOT committed to supporting it or making it available to all customers.


Our Cursor data connector uses the Admin API to import data about Team Members (accounts),  Daily Usage (metrics), and Usage Events (work events) to Worklytics.

[https://docs.cursor.com/account/teams/admin-api](https://docs.cursor.com/account/teams/admin-api)


## Steps to Connect

See Cursor's documentation for the latest, but as of July 10, 2025, you must create an API key and fill that as a secret in your host platform. The Proxy will then use Basic Authentication when connecting to Cursor.


1. Navigate to [https://cursor.com/dashboard](https://cursor.com/dashboard) → Settings tab → Cursor Admin API Keys

2. Click 'Create New API Key'

3. Give your key a descriptive name (e.g., “Usage Dashboard Integration”)

4. Copy the generated key immediately - you won’t see it again; then paste it as the `PSOXY_CURSOR_BASIC_AUTH_USER_ID` parameter value  in your proxy's host platform.

NOTE: cursor calls it an "API Key", but their authentication mechanism is a form a Basic Auth per RFC 7617 Section 2. That RFC would call what Cursor calls an 'API key' a 'user-id'. So we're following the RFC term in the configuration.





