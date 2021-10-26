# cmd-line

Use this tool to pseudonymize CSV files consistently with Psoxy instances running in the cloud
(assuming all configured with the same salt).

First, ensure you have java, Maven, etc installed.

Second, create a `config.yaml` file within `java/impl/cmd-line` in your checkout, with your salt and
a list of which columns in your CSV need to be pseudonymized. See `example-config.yaml` for an
example. The salt value MUST match the salt value configured for any psoxy instances with your
pseudonyms should be consistent (eg, that the pseudonymized identifier produced from CSV will match
the pseudonym produced by the psoxy instance for the same identifier).

Usage:  (from `java/impl/cmd-line`)
```shell
mvn clean package

java -jar target/psoxy-cmd-line-1.0-SNAPSHOT.jar src/test/resources/hris-example.csv > pseudonymized.csv
```
