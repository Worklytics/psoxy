# Maven Artifacts

Psoxy distributes the following Maven artifacts via GitHub Packages:

- `com.avaulta.gateway:gateway-core` - Core gateway library for implementations and clients
- `co.worklytics.psoxy:psoxy-core` - Psoxy core functionality

## Using Artifacts in Your Project

### Prerequisites

To consume packages from GitHub Packages, you need:

1. A GitHub account
2. A GitHub Personal Access Token (PAT) with `read:packages` permission
   - Create one at: https://github.com/settings/tokens
   - Select the `read:packages` scope

### Maven Configuration

#### 1. Configure Authentication

Add the following to your `~/.m2/settings.xml`:

```xml
<settings>
  <servers>
    <server>
      <id>github-psoxy</id>
      <username>YOUR_GITHUB_USERNAME</username>
      <password>YOUR_GITHUB_TOKEN</password>
    </server>
  </servers>
</settings>
```

Replace `YOUR_GITHUB_USERNAME` with your GitHub username and `YOUR_GITHUB_TOKEN` with your Personal Access Token.

#### 2. Add Repository to Your Project

Add the repository to your project's `pom.xml`:

```xml
<repositories>
  <repository>
    <id>github-psoxy</id>
    <name>GitHub Psoxy Packages</name>
    <url>https://maven.pkg.github.com/Worklytics/psoxy</url>
  </repository>
</repositories>
```

**Note:** The repository `<id>` must match the server `<id>` in your `settings.xml`.

#### 3. Add Dependencies

Add the artifacts you need to your `pom.xml`:

```xml
<dependencies>
  <!-- Gateway Core -->
  <dependency>
    <groupId>com.avaulta.gateway</groupId>
    <artifactId>gateway-core</artifactId>
    <version>0.5.12</version>
  </dependency>

  <!-- Psoxy Core -->
  <dependency>
    <groupId>co.worklytics.psoxy</groupId>
    <artifactId>psoxy-core</artifactId>
    <version>0.5.12</version>
  </dependency>
</dependencies>
```

Replace `0.5.12` with the version you want to use. Check the [releases page](https://github.com/Worklytics/psoxy/releases) for available versions.

### Gradle Configuration

If you're using Gradle, add the following to your `build.gradle`:

```groovy
repositories {
    maven {
        url = uri("https://maven.pkg.github.com/Worklytics/psoxy")
        credentials {
            username = project.findProperty("gpr.user") ?: System.getenv("GITHUB_USERNAME")
            password = project.findProperty("gpr.key") ?: System.getenv("GITHUB_TOKEN")
        }
    }
}

dependencies {
    implementation 'com.avaulta.gateway:gateway-core:0.5.12'
    implementation 'co.worklytics.psoxy:psoxy-core:0.5.12'
}
```

Then either:
- Set environment variables `GITHUB_USERNAME` and `GITHUB_TOKEN`, or
- Add to `~/.gradle/gradle.properties`:
  ```properties
  gpr.user=YOUR_GITHUB_USERNAME
  gpr.key=YOUR_GITHUB_TOKEN
  ```

### JDK 17 Variants

The artifacts are compiled for JDK 17. If you need a different Java version, you may need to use compatibility settings in your project.

## Troubleshooting

### Authentication Errors

If you see errors like "Could not find artifact" or "401 Unauthorized":

1. Verify your GitHub token has `read:packages` permission
2. Ensure the token hasn't expired
3. Check that the `<id>` in your `pom.xml` repository matches the `<id>` in your `settings.xml` server configuration
4. Verify your GitHub username is correct

### Version Not Found

If a specific version isn't available:

1. Check the [releases page](https://github.com/Worklytics/psoxy/releases) for available versions
2. Check the [packages page](https://github.com/orgs/Worklytics/packages?repo_name=psoxy) to see published packages

### Rate Limiting

GitHub Packages has rate limits. If you're hitting limits:

1. Ensure you're properly authenticated (unauthenticated requests have lower limits)
2. Consider using a local Maven repository manager like Nexus or Artifactory to cache artifacts

## For Package Publishers

See [Releases](releases.md) for information on publishing new versions of these artifacts.

