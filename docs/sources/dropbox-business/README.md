# Dropbox Business

**DEPRECATED**; ***we are not actively supporting this connector as of Aug 2025.***


The Dropbox Business connector through Psoxy requires a Dropbox Application created in Dropbox Console. The application does not need to be public, but it must have the following scopes to support all operations for the connector:

- `files.metadata.read`: for file listing and revision
- `members.read`: member listing
- `events.read`: event listing
- `groups.read`: group listing

1. Go to https://www.dropbox.com/apps and Build an App.
2. Then go to https://www.dropbox.com/developers to enter the `App Console` to configure your app.
3. In the app, go to `Permissions` and mark all the scopes described above. NOTE: The UI may automatically select additional required permissions (like _account_info_read_). Just mark the ones described here, and the UI will prompt you to include any other required ones.
4. In settings, you can access the `App key` and `App secret`. You can create an access token here, but it will have limited expiration. To create a long-lived token, edit the following URL with your `App key` and paste it into your browser:

   `https://www.dropbox.com/oauth2/authorize?client_id=<APP_KEY>&token_access_type=offline&response_type=code`

   This will return an `Authorization Code` that you need to paste. **NOTE:** This `Authorization Code` is for single use; if it expires or is used, you will need to get it again by pasting the URL in the browser.

5. Replace the values in the following command and run it from your terminal. Replace `AUTHORIZATION_CODE`, `APP_KEY`, and `APP_SECRET` with your values:

   `curl https://api.dropbox.com/oauth2/token -d code=<AUTHORIZATION_CODE> -d grant_type=authorization_code -u <APP_KEY>:<APP_SECRET>`

6. After running that command, if successful, you will see a [JSON response](https://www.dropbox.com/developers/documentation/http/documentation#oauth2-authorize) like this:

```json
{
  "access_token": "some short live access token",
  "token_type": "bearer",
  "expires_in": 14399,
  "refresh_token": "some long live token we are going to use",
  "scope": "account_info.read events.read files.metadata.read groups.read members.read team_data.governance.read team_data.governance.write team_data.member",
  "uid": "",
  "team_id": "some team id"
}
```

7. Finally, set the following variables in AWS System Manager Parameter Store or GCP Cloud Secrets (if using the default implementation):

- `PSOXY_dropbox_business_REFRESH_TOKEN` secret variable with the value of `refresh_token` received in the previous response
- `PSOXY_dropbox_business_CLIENT_ID` with `App key` value.
- `PSOXY_dropbox_business_CLIENT_SECRET` with `App secret` value.
