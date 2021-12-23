# Custom Rules

Predefined sanitization rules are defined in Java, with YAML equivalents available in [`java/core/src/main/resources/rules/`](../java/core/src/main/resources/rules). To customize these rules, you should begin with one of those
examples and modify it to suit your needs.

## Deployment
To deploy your modified rules, either:
1. set `RULES` environment variable of your cloud function to base64-encoded version of the yaml
   contents  (eg, `cat java/core/src/main/resources/rules/google-workspace/gmail.yaml | base64`);
   restart all instances of the function, or wait for them to be restarted.
2. include your YAML file as `rules.yaml` inside your deployment directory (eg, put it at `java/impl/gcp/target/deployment/rules.yaml`) and redeploy your cloud function.

## Testing

### Curl

After deploying your rules, you can test them with the the example commands found in
[`docs/example-api-calls/`]('example-api-calls/)

### Continuous Integration with Java
To continuously test your rules, you should create your one private fork of the psoxy repo and write
tests in java. You can extend `YAMLRulesTestBaseCase`.


