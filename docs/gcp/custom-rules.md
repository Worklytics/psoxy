# Custom Rules

Predefined sanitization rules are defined in Java, with YAML equivalents available in [`java/core/src/main/resources/rules/`](../java/core/src/main/resources/rules). To customize these rules, you should begin with one of those
examples and modify it to suit your needs.

## Deployment
To deploy your modified rules, set `RULES` environment variable of your cloud function to base64-encoded version of the yaml
   contents  (eg, `cat java/core/src/main/resources/rules/google-workspace/gmail.yaml | base64`);
   restart all instances of the function, or wait for them to be restarted.

Alternatively, you may create a GCP Secret Manager secret prefixed by your function name in all caps, with suffix `_RULES`. (eg, `PSOXY_GCAL_RULES`).

### Continuous Integration with Java
To continuously test your rules, you should create your one private fork of the psoxy repo and write
tests in java. You can extend on of the `RulesTestBaseCase`.


