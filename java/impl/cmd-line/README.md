# cmd-line

Use this tool to pseudonymize CSV files consistently with Psoxy instances running in the cloud
(assuming all configured with the same salt).

First, ensure your environment has java, Maven, etc available. (Google Cloud Shell will suffice)

Second, create a `config.yaml` file within `java/impl/cmd-line` in your checkout, with your salt and
a list of which columns in your CSV need to be pseudonymized. See `example-config.yaml` for an
example.

We recommend your salt be retrieved from GCP Secret Manager, as this will ensure consistency with
other proxy instances, simplify configurion, and minimize handling of the salt itself. This requires
that your environment is  authenticated with GCP as a user or service account authorized to access
that secret. For example:
   - execute within Google Cloud Shell as yourself
   - execute within Compute Engine / App Engine / etc VM/container (service account must be
     authorized to access secret)
   - execute somewhere else you've installed `gcloud`, and have authenticated via `gcloud auth application-default login`


Usage:  (from `java/impl/cmd-line`)
```shell
mvn clean package

java -jar target/psoxy-cmd-line-1.0-SNAPSHOT.jar src/test/resources/hris-example.csv > pseudonymized.csv
```
