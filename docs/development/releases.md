# Releases

## Prepare Release Candidate

From `main`:

```shell
./tools/release/prep.sh v0.4.15 rc-v0.4.16
```

- follow steps output by that tool
- if need interim testing, create a "branch" of the release (eg, branch `v0.4.16` instead of tag),
  and trigger `gh workflow run ci-terraform-examples-release.yaml`

## Release

On `rc-`:

```shell
./tools/release/prep.sh rc-v0.4.16 v0.4.16
```

The first argument is the string currently in module refs / `JAVA_SOURCE_CODE_VERSION`; the second is what to substitute. They do not have to match the git branch name. A major bump can keep the existing rc branch:

```shell
# still on branch rc-v0.4.16:
./tools/release/prep.sh rc-v0.4.16 v0.5.0
```

Then:

```shell
cd tools/psoxy-test
npm audit fix
git commit -a -m "update deps in psoxy-test"
```

TODO: review versions of terraform, java, node uses in github actions. Ensure we're explicitly using the latest of each, and that we're ALSO testing explicitly for latest-1 version, even if it's not officially supported still.


QA aws and gcp dev examples before merging. See [release QA runbook](../../tools/release/release-qa.md) or run:

```shell
./tools/release/run-release-qa.sh vX.Y.Z
```

Create PR to merge `rc-` to `main`. The RC branch name may differ from the release tag; `rc-to-main.sh` uses the current `rc-v*` branch when you are on one:

```shell
./tools/release/rc-to-main.sh v0.4.16
# or explicitly: ./tools/release/rc-to-main.sh v0.5.0 rc-v0.4.16
```



After merged to `main`:

```shell
./tools/release/publish.sh v0.4.16
```

This script will:
- Create and push a git tag
- Create a GitHub release
- Publish Maven artifacts to GitHub Packages (requires authentication)
- Provide instructions for additional release steps

### Maven Artifacts Publishing

The `publish.sh` script automatically publishes Maven artifacts (`gateway-core` and `psoxy-core`) to GitHub Packages.

**Prerequisites:**
- Maven installed
- GitHub Personal Access Token with `write:packages` permission
- Authentication configured in `~/.m2/settings.xml`:

```xml
<settings>
  <servers>
    <server>
      <id>github</id>
      <username>YOUR_GITHUB_USERNAME</username>
      <password>YOUR_GITHUB_TOKEN</password>
    </server>
  </servers>
</settings>
```

If the automatic publish fails, you can manually run:

```shell
cd java
mvn clean deploy
```

For information on consuming these artifacts, see [Maven Artifacts](maven-artifacts.md).
