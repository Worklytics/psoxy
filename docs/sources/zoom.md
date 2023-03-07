# Zoom

Zoom connector through Psoxy requires a custom managed app on the Zoom Marketplace (in development
mode, no need to publish).

1. Go to https://marketplace.zoom.us/develop/create and create an app of type "Server to Server OAuth"
2. After creation, it will show the App Credentials. Share them with the AWS/GCP administrator, the
   following secret values must be filled in the Secret Manager for the Proxy with the appropriate values:

    - `PSOXY_ZOOM_CLIENT_ID`
    - `PSOXY_ZOOM_ACCOUNT_ID`
    - `PSOXY_ZOOM_CLIENT_SECRET`
    - Note: Anytime the *client secret* is regenerated it needs to be updated in the Proxy too.

3. Fill the information section

4. Fill the scopes section, enabling the following:

    - Users / View all user information / `user:read:admin`
        - To be able to gather information about the zoom users
    - Meetings / View all user meetings / `meeting:read:admin`
        - Allows us to list all user meeting
    - Report / View report data / `report:read:admin`
        - Last 6 months view for user meetings

5. Activate the app
