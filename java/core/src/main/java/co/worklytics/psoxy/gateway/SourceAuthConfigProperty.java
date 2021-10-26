package co.worklytics.psoxy.gateway;

/**
 * config properties that control how Psoxy authenticates against host
 */
public enum SourceAuthConfigProperty implements ConfigService.ConfigProperty {
    OAUTH_SCOPES,
    //this should ACTUALLY be stored in secret manager, and then exposed as env var to the
    // cloud function
    // see "https://cloud.google.com/functions/docs/configuring/secrets#gcloud"
    SERVICE_ACCOUNT_KEY,
}
