

```shell

gcloud functions deploy psoxy-gmail \
    --entry-point=co.worklytics.psoxy.Route \
    --runtime=java11 \
    --trigger-http \
    --source=target/deployment \
    --project=psoxy-dev-erik \
    --service-account=psoxy-google-chat-dwd@psoxy-dev-erik.iam.gserviceaccount.com \
    --env-vars-file=configs/gmail.yaml \
    --update-secrets 'SERVICE_ACCOUNT_KEY=projects/psoxy-erik-dev/secrets/PSOXY_SERVICE_ACCOUNT_KEY_gmail:1'
```
