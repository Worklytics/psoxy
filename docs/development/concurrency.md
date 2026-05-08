# Concurrency Support

Psoxy supports concurrent request handling within a single Cloud Run Function instance,
controlled by the `instance_concurrency` Terraform variable.

## Configuration

Both `gcp-proxy-api` and `gcp-webhook-collector` modules accept:

```hcl
variable "instance_concurrency" {
  type    = number
  default = 5
}
```

When `instance_concurrency > 1`, the module automatically sets `available_cpu = "1"` because
GCP Cloud Run requires at least 1 vCPU for concurrent request handling.

The `gcp-proxy-bulk` module does **not** support concurrency > 1 — bulk processing involves
long-running file transforms that are CPU-intensive and not suited for concurrent sharing.

## Why concurrency > 1?

Psoxy's workload is **I/O-bound**: most request time is spent waiting for upstream API responses.
This makes it ideal for serving multiple requests on a single instance:

- **Cold start reduction** — Java cold starts on Cloud Run are 3–10 seconds. With concurrency=5,
  a burst of 5 requests uses 1 warm instance instead of 5 (potentially 4 cold starts).
- **Memory efficiency** — JVM overhead, classes, regex caches, and config are shared across
  concurrent requests (~200–300MB saved per avoided instance).
- **Cost** — 1 instance × 1 vCPU < 5 instances × 0.58 vCPU for I/O-bound work.

## Thread-Safety Architecture

The codebase uses several patterns to ensure thread safety:

### Double-Checked Locking (DCL)

Lazily-initialized fields that are computed once and read on every request use the DCL pattern:

```java
volatile SomeType field;
private final Object $writeLock = new Object[0];

private SomeType getField() {
    if (field == null) {
        synchronized ($writeLock) {
            if (field == null) {
                field = computeExpensiveValue();
            }
        }
    }
    return field;
}
```

**Requirements:**
- The field **must** be declared `volatile` for safe publication
- The lock object should be `private final`
- The inner null check prevents double-initialization

### ConcurrentHashMap for Caches

Lazily-populated caches (e.g., compiled JsonPath transforms) use `ConcurrentHashMap` with
`computeIfAbsent`:

```java
Map<Key, Value> cache = new ConcurrentHashMap<>();
Value v = cache.computeIfAbsent(key, k -> expensive(k));
```

### Immutable-After-Construction

Most handler and utility classes are injected via Dagger and are immutable after construction.
All fields are set in the constructor and never reassigned. This is inherently thread-safe.

### Static Final Fields

Static fields that hold constants (sets, maps, ObjectMappers) must be declared `final` to
guarantee safe publication per the Java Memory Model.

## Guidelines for New Code

1. **New lazily-initialized instance fields** — use `volatile` + DCL as shown above.
2. **New static fields** — always declare `final` unless there's a compelling reason not to.
3. **Mutable collections** — if a collection could be accessed from multiple threads, use
   `ConcurrentHashMap` or return a new copy rather than mutating in place.
4. **Thread-local state** — method-local variables are always safe. Prefer keeping state
   local to methods rather than storing it in fields.
5. **Avoid lazy field mutation** — don't replace a field's value on first access (like
   converting a `HashMap` to a `TreeMap` in a getter). Instead, do the conversion eagerly
   at construction time.

## Testing Concurrency

### Unit Tests

Concurrency-specific tests live alongside the regular tests with a `ConcurrencyTest` suffix:

- `RESTApiSanitizerImplConcurrencyTest` — races DCL initialization paths
- `ApiDataRequestHandlerConcurrencyTest` — races sanitizer lazy loading
- `BatchMergeHandlerConcurrencyTest` — verifies no output interleaving

These tests use `CyclicBarrier` to force threads to enter the critical section simultaneously
and `@RepeatedTest` to increase the probability of exposing race conditions.

### CLI Load Testing

The psoxy-test CLI supports a `--concurrency N` flag:

```bash
node cli-call.js -u https://your-proxy-url/path --concurrency 5
```

This fires N copies of the request simultaneously and reports:
- Per-request status and latency
- p50/p95 latency
- Cross-contamination detection (responses should be identical for same input)
