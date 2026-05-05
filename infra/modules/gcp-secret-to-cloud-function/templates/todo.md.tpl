Run the following command from functions deployment directory (containing bundled JAR or pom.xml)
to finish exposing `${slugified_secret_name}` to `${function_name}`:

```shell
gcloud beta functions deploy ${function_name} \
    --project=${project_id} \
    --runtime=${runtime} \
    --update-secrets 'SERVICE_ACCOUNT_KEY=${secret_name}:${secret_version_number}'
```
