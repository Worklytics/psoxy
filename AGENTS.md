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

## Release cut / QA / publish

Release orchestration (cut RC, QA examples-dev, rc→main, tag/publish) lives in the internal `Worklytics/proxy-dev` repo. GitHub Actions in this repo still publish Maven packages and deployment bundles on `v*` tags and `rc-*` branches; those workflows call the remaining scripts under `tools/release/`.

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

## Scope and consistency

Stay consistent with existing architecture, naming, and style unless you are explicitly instructed to deviate or the task itself is to improve that pattern. Implementing a feature is not a license to overhaul style, architecture, or tooling.

Pull requests should accomplish **one** big-picture thing, plus only ancillary work that is directly required for that thing. Do not bundle drive-by refactors, speculative extensibility (YAGNI), or unrelated cleanup. Do not change existing types to serve a new use case when a dedicated type already exists (e.g. keep `JsonSchemaFilter` for filtering; use `JsonSchema` / a 3rd-party schema for validation).

## Java Coding Conventions

When modifying Java files, follow these guidelines:

1. **Avoid Fully Qualified Names (FQNs)**: Prefer explicitly importing classes and using their simple names instead of using fully qualified names in the code, except where there are intractable naming collisions.
2. **Prefer Fluid Builders**: We generally prefer using fluid-builder patterns, leveraging Lombok's `@Builder` annotation for object construction instead of constructors with many parameters.
3. **Lombok + Dagger are the standard**: Use Lombok (`@NoArgsConstructor(onConstructor_ = @Inject)`, `@AllArgsConstructor(onConstructor_ = @Inject)`, `@Getter`, `@Value`, `@Builder`) rather than hand-written constructors, getters, or empty private constructors. Do **not** use Lombok `@UtilityClass` (experimental) and do not introduce Dagger `UtilityClass`. Follow existing Dagger modules: `@Provides` / `@Binds` in `PsoxyModule`, `AwsModule`, `GcpModule` — platform modules provide platform types. `new` belongs in `@Provides` methods, not in injectable service constructors.
4. **Avoid static helpers**: Inject behavior via Dagger-constructed services. Do not wrap a library method in a private helper that adds nothing (`StringUtils.containsIgnoreCase` — call it directly). Named constants instead of repeated magic strings.
5. **No generic `catch (Exception)`**: Catch the specific failure you expect. Unexpected errors should propagate. Do not swallow errors as `false` / warnings unless that is an explicit, documented contract.
6. **Readable control flow**: Do not nest or chain ternaries. Prefer `if` / `else` or `Optional`. Invert conditions when that removes a nested ternary.
7. **Stylistic Changes**: Agents should avoid making stylistic changes (e.g., reformatting code, optimizing all imports, or resolving linting issues irrelevant to the functional change) to the repository unless explicitly directed by the user.
8. **Separate Commits**: When explicitly directed to make stylistic changes or broad refactoring, these should be separated into distinct commits from functional changes to simplify review.
9. **Concurrency**: The proxy may handle concurrent requests. Any new code introducing shared mutable state, lazy initialization, or caches must be thread-safe. Use `volatile`, `synchronized`, `ConcurrentHashMap`, or immutable snapshots (`Set.copyOf`, `List.copyOf`) as appropriate. Document thread-safety assumptions in javadoc.
10. **Prefer dependency injection over static helpers**: Platform adapters should expose raw request data; cross-cutting normalization belongs in shared handlers/services (e.g. `ApiDataRequestHandler`).
11. **Config property placement**: API connector settings belong on `ApiModeConfig.ApiModeConfigProperty` (or platform-specific API-mode config types). Use `ProxyConfigProperty` for proxy-wide settings; `BulkModeConfigProperty` for bulk connectors; webhook settings on webhook config types. Do not hardcode example payloads, field names from a specific source, or demo-only constants in production code.
12. **Keep platform code in platform modules**: AWS-only types live in `impl.aws`; GCP-only types live in `impl.gcp`. Core stays cloud-agnostic so deployment JARs stay small. Cmd-line may throw `UnsupportedOperationException` for cloud-only features.

## Terraform Conventions

1. **Do not add `validation` blocks in modules** unless asked. They make modules brittle; coverage belongs in CI tests (`*.tftest.hcl`) if needed.
2. **Keep `.tf` files canonical**. Splitting by feature is OK only when the file can stand alone. Locals used by a split file should live with it, or the resources should stay in `main.tf`. Hyphenated names (`gen-metadata.tf`) if you do split.
3. **Customer TODOs are step-by-step actions** the customer still must take. Do not explain what Terraform already did, why, or mix troubleshooting into the steps. Extra notes (SCPs, edge cases) go after the steps. Link docs at `https://docs.worklytics.co/psoxy/...`, not GitHub blob URLs.
4. **Enable optional GCP APIs from a list** (`for_each = toset(local.services)`), not a separate `count` resource per service.
5. **IAM extras**: `concat()` lists of statements (empty list when the feature is off), rather than a one-off ternary per feature.

## Documentation Conventions

Customer-facing docs (`docs/configuration/`, Terraform TODOs) describe what the customer configures and does. Implementation internals belong in `docs/development/` (and platform-specific AWS/GCP pages when the behavior is cloud-only). Alphabetize GitBook `SUMMARY.md` menu entries. Do not hard-wrap markdown prose.

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


