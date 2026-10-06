# Releases

Cut, QA, and local publish are run from the internal `Worklytics/proxy-dev` repo against a psoxy checkout. This repo only retains the scripts invoked by GitHub Actions (see [tools/release/README.md](../../tools/release/README.md)).

GitHub Actions in **this** repo still publish artifacts when a `v*` tag or `rc-*` branch is pushed:

- `publish-release-artifacts.yaml` — Maven packages, SBOMs, deployment JARs on the GitHub release
- `publish-bundles.yaml` — AWS (S3) and GCP (GCS) deployment bundles, then verify

Tagging `vX.Y.Z` on `main` (after the rc→main PR merges) is what triggers the production publish path.

Customer-facing scripts (`check-prereqs`, `init-example*`, `psoxy-test`, connector auth helpers, `build.sh`, etc.) stay in this repo and are copied into the public example repos at example-publish time.

### Maven Artifacts

Published to GitHub Packages by the workflow above. Local `mvn clean deploy` from `java/` is possible but not the usual path.

**Prerequisites for local deploy:**
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

For information on consuming these artifacts, see [Maven Artifacts](maven-artifacts.md).
