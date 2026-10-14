# Concrete configuration POJOs

Proxy configuration is moving from ad hoc `ConfigService.getConfigProperty(...)` calls (often via scattered property enums) toward **immutable POJOs** assembled once at startup and injected where needed.

## Target pattern

Follow `WebhookCollectorModeConfig` as the reference:

1. **POJO** — Lombok `@Value` / `@Builder` (or `@Getter` + private fields for types that need defaults), holding parsed configuration for one domain (API mode, bulk mode, webhooks, etc.).
2. **Property enum** — Nested on the POJO (e.g. `WebhookCollectorModeConfig.WebhookCollectorModeConfigProperty`) or, during migration, a standalone enum that will be folded in later. Keys stay off `ProxyConfigProperty` when they are mode- or connector-specific (see AGENTS.md config placement).
3. **Factory** — `static X fromConfigService(ConfigService configService)` (or `from(...)` when multiple sources are required, as in `RemoteResourceConfig`) is the **only** place that reads those keys from `ConfigService` and performs parsing (booleans, integers, CSV splits, etc.).
4. **Dagger** — `*Module` `@Provides` methods call the factory and expose the POJO. Use `dagger.Lazy<>` when building the POJO must not run during graph validation or when it depends on optional runtime wiring.
5. **Consumers** — Handlers, sanitizers, and strategies depend on the POJO (and `SecretStore` only when secrets must be read with versioning or rotation semantics), not on `ConfigService`.

`ConfigService` (and `SecretStore` / `WritableConfigService`) remain appropriate for:

- Implementations and decorators (`EnvVarsConfigService`, `ParameterStoreConfigService`, `CachingConfigServiceDecorator`, etc.).
- Dagger modules and factories that **assemble** POJOs or platform-specific bindings.
- Code that must **introspect** live config for diagnostics (e.g. health checks listing missing auth properties), or read **versioned secrets** at request time.
- Command-line and test harnesses that substitute in-memory config.

## Already on the POJO path

| Type | Module(s) | Notes |
|------|-----------|--------|
| `WebhookCollectorModeConfig` | `FunctionRuntimeModule`, `GcpModule` | Reference implementation |
| `ApiModeConfig` | `FunctionRuntimeModule`, `CmdLineModule` | Used by handlers, sanitizers, transport |
| `AwsEnvironment.AwsApiModeConfig` | `AwsModule` | Platform async queue URL |
| `GcpEnvironment.GcpApiModeConfig` | `GcpModule` | Service URL, Pub/Sub async |
| `GcpEnvironment.GcpWebhookCollectorModeConfig` | `GcpModule` | GCP webhook collector extras |
| `BulkModeConfig` | `PsoxyModule` | **Partial** — factory only maps `BULK_OUTPUT_FORMAT`; see gaps below |
| `LoggingConfiguration` | `FunctionRuntimeModule` | New Relic-related settings |
| `RemoteResourceConfig` | `AwsModule`, `GcpModule` | Env + optional remote paths |
| `GenMetadataConfig` (+ `VertexGenMetadataConfig`, `BedrockGenMetadataConfig`) | `AwsModule`, `GcpModule` | Gen-metadata alpha; factories still take `ConfigService` |

## Gaps — runtime `ConfigService` / property reads

These are the main areas **not** yet following the pattern. Prefer introducing or extending a POJO and injecting it instead of adding new `getConfigProperty` calls here.

### Proxy-wide (`ProxyConfigProperty`)

No single `ProxyConfig` POJO exists yet; many call sites still read proxy-wide keys directly.

| Location | What it reads | Suggested direction |
|----------|---------------|-------------------|
| `PsoxyModule` | Salt, encryption keys, grant type, parser id, pseudonymizer wiring | `ProxyConfig` / `PseudonymizationConfig` POJO from `SecretStore` + `ConfigService` in module only |
| `FunctionRuntimeModule` | `USER_AGENT`, `RULES`, TLS via `ApiModeConfig` | Extend `ProxyConfig` or keep rules assembly in module but stop passing raw `ConfigService` to runtime |
| `ConfigRulesModule` | `SOURCE`, `PSEUDONYMIZE_APP_IDS`, rules defaults | `RulesConfig` POJO or fold into `ProxyConfig` |
| `PseudonymizerImplFactory` | `PSEUDONYM_IMPLEMENTATION`, email handling | Same pseudonymization POJO |
| `RulesUtils` | `RULES`, `CUSTOM_RULES_SHA`, `ADDITIONAL_TRANSFORMS` | Accept POJOs or rule-set inputs, not `ConfigService` |
| `ApiDataRequestHandler` | `FOLLOW_REDIRECTS` (also has `ApiModeConfig`) | Add field to `ApiModeConfig` |
| `ApiDataOutputUtils` | `SIDE_OUTPUT_ORIGINAL` | `SideOutputConfig` or fields on `ProxyConfig` |
| `OutputUtils` | Maps outputs to `SIDE_OUTPUT_*` properties | Inject config POJO |
| `InboundWebhookHandler` | `IS_DEVELOPMENT_MODE` | `EnvVarsConfigService` / `LoggingConfiguration` / small `RuntimeFlags` POJO |
| `HealthCheckRequestHandler` | Broad introspection across `ProxyConfigProperty`, auth enums, `SecretStore` | Keep `ConfigService` for discovery, but use POJOs for static proxy fields where possible |
| `StorageHandler` | `RULES` (SHA metadata), `PSOXY_SALT` via `SecretStore` | Proxy + bulk POJOs |
| `CmdLineModule` | `PSOXY_SALT` for tokenization | `ProxyConfig` |

### Bulk mode (`BulkModeConfigProperty` vs `BulkModeConfig`)

`BulkModeConfig` is only partially populated. **`BulkModeConfigProperty`** (standalone enum) still drives most bulk behavior:

| Location | Properties used |
|----------|-----------------|
| `StorageHandler` | `OUTPUT_BUCKET`, `INPUT_BASE_PATH`, `OUTPUT_BASE_PATH`, `BUFFER_SIZE`, `COMPRESS_OUTPUT_ALWAYS` |
| `RulesUtils` | `ADDITIONAL_TRANSFORMS` |

**Direction:** Extend `BulkModeConfig.fromConfigService` to load all bulk keys, nest or merge `BulkModeConfigProperty` into the POJO, inject `BulkModeConfig` into `StorageHandler` and `RulesUtils`, and remove direct `ConfigService` from those classes.

### Source authentication and OAuth

Each strategy/builder typically defines a **local** `ConfigProperty` enum and reads `ConfigService` and/or `SecretStore` at runtime. Candidates for per-strategy config POJOs (built in `SourceAuthModule` / `PsoxyModule` when the strategy is selected):

| Class |
|-------|
| `BasicAuthStrategy` |
| `ClaudeAuthStrategy` |
| `WindsurfServiceKeyAuthStrategy` |
| `GoogleCloudPlatformServiceAccountKeyAuthStrategy` |
| `GcpIamSignJwtAuthStrategy` |
| `AwsWifGoogleCloudProcessIdentity` |
| `OAuthAccessTokenSourceAuthStrategy` |
| `OAuthRefreshTokenSourceAuthStrategy` |
| `ClientCredentialsGrantTokenRequestBuilder` |
| `AccountCredentialsGrantTokenRequestBuilder` |
| `CertificateGrantTokenRequestBuilder` |
| `RefreshTokenTokenRequestBuilder` |
| `RefreshTokenRequestViaQueryParameterBuilder` |
| `WorkloadIdentityFederationGrantTokenRequestBuilder` |
| `AWSWorkloadIdentityFederationGrantTokenRequestBuilder` (AWS) |
| `GCPWorkloadIdentityFederationGrantTokenRequestBuilder` (GCP) |

`PsoxyModule` still uses `ConfigService` to choose grant type and response parser before constructing strategies — that selection logic could move into a small `SourceAuthConfig` POJO.

### Platform handlers (dev / logging only)

| Location | Usage |
|----------|--------|
| `GcpApiDataRequestHandler` | `EnvVarsConfigService.isDevelopment()` for verbose logging |
| `ApiDataRequestViaPubSub` | Same |
| `GcpWebhookCollectionHandler` | Injects `ConfigService` but **does not use** the field (remove) |
| `DevLogUtils` | `EnvVarsConfigService` for dev-gated logs — acceptable cross-cutting helper |

### Intentional exceptions

- **ConfigService implementations** and **SecretStore** adapters.
- **Health checks** that enumerate missing auth properties across strategies.
- **OAuth token refresh** paths that read **versioned** private keys from `SecretStore` at refresh time (may stay on `SecretStore` even after POJO migration for static non-secret fields).
- **Tests** using `MemoryConfigService` or mocks.

## Migration checklist (per domain)

1. Add or extend POJO + nested property enum.
2. Implement `fromConfigService` (parse and validate there).
3. Add `@Provides` in the appropriate `*Module` (use `@Lazy` if needed).
4. Replace injected `ConfigService` in consumers with the POJO.
5. Delete unused `ConfigService` constructor parameters and imports.
6. Add or update unit tests on the factory (see `ApiModeConfigTest`).

## Related

- AGENTS.md — Java coding conventions (config property placement and concrete POJO pattern).
- `docs/development/gcp-parameter-manager.md` — why remote config is not Parameter Manager.
