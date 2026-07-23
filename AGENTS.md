# Worklytics Agent Conventions

This document outlines conventions and guidelines for tools and agents interacting with this repository.

## Bash Scripting Conventions

### Terminal Colors
When writing or modifying bash scripts that use styled or colored output, adhere to the following conventions:

1. **Use Semantic Names**: Use short semantic variable names for colors indicating the purpose of the output (e.g., `ERR`, `SUCCESS`, `WARN`, `INFO`, `NC` for No Color) rather than explicit color names.

2. **Dynamic Configuration**: Dynamically set these terminal color variables based on the terminal's capabilities, favoring standard utilities like `tput` over hardcoded ANSI escape sequences. 

**Example Implementation**:
```bash
# Use semantic colors dynamically based on terminal capability
if [ -t 1 ] && command -v tput >/dev/null 2>&1; then
    ERR=$(tput setaf 1)
    SUCCESS=$(tput setaf 2)
    WARN=$(tput setaf 3)
    INFO=$(tput setaf 4)
    NC=$(tput sgr0)
else
    ERR='\033[0;31m'
    SUCCESS='\033[0;32m'
    WARN='\033[1;33m'
    INFO='\033[0;34m'
    NC='\033[0m'
fi

# Usage
printf "${SUCCESS}Operation completed successfully.${NC}\n"
```

## Release QA

Before merging an `rc-vX.Y.Z` branch to `main`, follow [tools/release/release-qa.md](tools/release/release-qa.md). The orchestrator is `./tools/release/run-release-qa.sh vX.Y.Z`.

## Testing Conventions

When modifying code in this repository, you should ensure that your changes pass our standardized tests. 

### Terraform Testing
Terraform changes should be validated against multiple versions. CI tests against Terraform versions from `~1.7.0` up to `~1.14.0` and `latest`. In practice, testing with `latest` is OK for local / pre-commit testing.

To validate terraform changes locally:
1. Navigate to the example directories (`infra/examples-dev/aws` and `infra/examples-dev/gcp`)
2. Run `terraform init` and `terraform validate`
3. If modifying modules, you may also need to run `terraform test` within those module directories if tests are defined (e.g. `terraform test --var="deployment_bundle=..."`)

### Java Testing
Java changes are tested across multiple Java versions to ensure compatibility. The GitHub Actions workflows test against Java 21 (LTS), 25 (LTS), and latest (26). In practice, testing with Java 21 is sufficient for local development.

When testing Java code locally:
1. Ensure your code builds and tests pass using Maven.
2. The standard test command used in CI is:
   ```bash
   mvn post-clean test -T 2C -Dversions.logOutput=false -DprocessDependencies=false -DprocessDependencyManagement=false -Dsurefire.forkCount=2.5C -Dsurefire.reuseForks=true
   ```
3. For a simpler local test run, you can use:
   ```bash
   mvn clean test
   ```

## Java Coding Conventions

When modifying Java files, follow these guidelines:

1. **Avoid Fully Qualified Names (FQNs)**: Prefer explicitly importing classes and using their simple names instead of using fully qualified names in the code, except where there are intractable naming collisions.
2. **Prefer Fluid Builders**: We generally prefer using fluid-builder patterns, leveraging Lombok's `@Builder` annotation for object construction instead of constructors with many parameters.
3. **Stylistic Changes**: Agents should avoid making stylistic changes (e.g., reformatting code, optimizing all imports, or resolving linting issues irrelevant to the functional change) to the repository unless explicitly directed by the user. 
4. **Separate Commits**: When explicitly directed to make stylistic changes or broad refactoring, these should be separated into distinct commits from functional changes to simplify review.
5. **Concurrency**: The proxy may handle concurrent requests. Any new code introducing shared mutable state, lazy initialization, or caches must be thread-safe. Use `volatile`, `synchronized`, `ConcurrentHashMap`, or immutable snapshots (`Set.copyOf`, `List.copyOf`) as appropriate. Document thread-safety assumptions in javadoc.

## Markdown Conventions

When writing or modifying markdown files (`.md`) in this repository:

1. **No Hard-Wrapping**: Do not hard-wrap prose at 80 columns (or any fixed width). Write each paragraph as a single long line and let the editor soft-wrap. Hard-wrapped prose creates noisy diffs when sentences are edited.
2. **Tables and Code Blocks**: These are inherently fixed-width; format them for readability as needed.
3. **Headings**: Use ATX-style headings (`#`, `##`, etc.).

## Documentation Conventions

### Connector Documentation
When writing or modifying documentation for data sources under `docs/sources/`, you must always explicitly include the Connector ID directly under the main header (H1/H2).

**Example Format**:
```markdown
# Asana

**Connector ID:** `asana`
```

### Example API Response Data

Connectors document sanitization behavior with paired fixtures under `docs/sources/<family>/<connector>/example-api-responses/`:

- `original/` — unsanitized API responses, as returned by the upstream source. These intentionally include PII-shaped fields (names, emails, phone numbers, etc.) so rules tests can exercise redaction and pseudonymization.
- `sanitized/` — expected proxy output for the matching `original/` files. Java rules tests (e.g. `*Tests.java` extending `RulesBaseTestCase`) sanitize each `original/` example and assert byte-for-byte equivalence with `sanitized/`.
- Variant directories such as `sanitized_no-app-ids/` follow the same pairing rules for alternate rule sets.

**All PII in committed `original/` examples must be fake.** You may capture real responses locally while developing a connector, but before committing to this repository replace every value that could identify a real person or organization. Never commit employee names, work emails, phone numbers, or other customer/tenant-specific identifiers from a live environment.

Use clearly synthetic stand-ins instead, consistent with existing connectors:

- Person names: generic personas (e.g. `Alice Warren`, `Sam Parker`) — not teammates, customers, or your own name
- Emails: fictional domains such as `contoso.com`, `example.com`, or clearly fake addresses on `worklytics.onmicrosoft.com`
- Phone numbers: obviously fake values (e.g. `555555555`, `+1 5555555555`)
- Other identifiers: fake UUIDs/IDs as needed; avoid values tied to real accounts

When you change `original/` fixtures, regenerate the matching `sanitized/` files and run the connector's Java rules tests (`mvn test -pl core -Dtest=<Connector>Tests`).

