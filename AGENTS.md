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

## Testing Conventions

When modifying code in this repository, you should ensure that your changes pass our standardized tests. 

### Terraform Testing
Terraform changes should be validated against multiple versions. CI tests against Terraform versions from `~1.6.0` up to `~1.12.0` and `latest`. In practice, testing with `latest` is OK for local / pre-commit testing.

To validate terraform changes locally:
1. Navigate to the example directories (`infra/examples-dev/aws` and `infra/examples-dev/gcp`)
2. Run `terraform init` and `terraform validate`
3. If modifying modules, you may also need to run `terraform test` within those module directories if tests are defined (e.g. `terraform test --var="deployment_bundle=..."`)

## JSON Formatting Conventions

Example API response files (under `example-api-responses/original/` and `example-api-responses/sanitized/`) must use **pretty-printed JSON** with the following style:

- **2-space indentation** for nested objects and arrays
- **Space after each colon** in object entries (i.e., `"key": value`, not `"key":value`)
- **Unix line endings** (`\n`)

This matches the default output of Jackson's `DefaultPrettyPrinter` (without the `withoutSpacesInObjectEntries()` option). Tests in `RulesBaseTestCase.testExamples` compare sanitized output against these files after running it through `TestUtils.prettyPrintJson()`, so the files must match that format exactly.

Example of correct formatting:
```json
{
  "id": "abc123",
  "labelIds": [
    "UNREAD",
    "INBOX"
  ],
  "sizeEstimate": 1234
}
```

### Java Testing
Java changes are tested across multiple Java versions to ensure compatibility. The GitHub Actions workflows test against Java 17, 21 (LTS), 23, 24, and 25. In practice, testing with Java 21 is sufficient for local development.

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
