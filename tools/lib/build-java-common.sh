# Shared helpers for building Psoxy Java deployment artifacts.
# Sourced by infra/modules/psoxy-package/build.sh (Terraform) and tools/build.sh (CLI).
#
# TODO (breaking change): unify CLI entry points — today argument order differs:
#   - psoxy-package/build.sh:  build.sh [-sf] <JAVA_SOURCE_ROOT> <IMPLEMENTATION>
#   - tools/build.sh:          build.sh [-qd] <IMPLEMENTATION> <JAVA_SOURCE_ROOT>
# TODO (breaking change): unify Maven invocations — psoxy-package uses sequential module
#   builds; tools/build.sh uses the parent-POM reactor (-pl/-am). Align on one approach.
# TODO (breaking change): unify deployment artifact path — psoxy-package expects
#   impl/<platform>/target/psoxy-<platform>-<version>.jar (Terraform/Lambda upload);
#   tools/build.sh with -Pdistribution reports impl/<platform>/target/deployment/*.jar.

psoxy_build_fail() {
  printf '%s\n' "$*" >&2
  exit 1
}

psoxy_build_warn() {
  printf 'WARNING: %s\n' "$*" >&2
}

psoxy_build_validate_implementation() {
  if [ -z "$1" ]; then
    psoxy_build_fail "IMPLEMENTATION is required (expected 'aws' or 'gcp')"
  fi
  if [ "$1" != "aws" ] && [ "$1" != "gcp" ]; then
    psoxy_build_fail "IMPLEMENTATION must be 'aws' or 'gcp', got '$1'"
  fi
}

psoxy_build_validate_java_source_root() {
  if [ -z "$1" ]; then
    psoxy_build_fail "JAVA_SOURCE_ROOT is required"
  fi
  if [ ! -f "${1}/pom.xml" ]; then
    psoxy_build_fail "JAVA_SOURCE_ROOT '$1' does not contain pom.xml"
  fi
}

# Path used by Terraform / psoxy-package (shaded JAR in module target/).
psoxy_build_terraform_jar_path() {
  printf '%s/impl/%s/target/psoxy-%s-%s.jar' "$1" "$2" "$2" "$3"
}

# Compute base64(SHA-256) for Terraform source_hash / filebase64sha256 compatibility.
# Warns and leaves JAR_HASH empty when openssl is missing or hashing fails.
psoxy_build_compute_jar_hash() {
  local jar_path="$1"
  JAR_HASH=""

  if ! command -v openssl >/dev/null 2>&1; then
    psoxy_build_warn "openssl not found; skipping SHA-256 hash for ${jar_path}. Terraform will compute the hash from the file if possible."
    return 0
  fi

  if ! JAR_HASH=$(openssl dgst -sha256 -binary "$jar_path" | openssl base64 | tr -d '\n'); then
    psoxy_build_warn "Could not compute SHA-256 hash for ${jar_path}; continuing without hash. Terraform will compute the hash from the file if possible."
    JAR_HASH=""
    return 0
  fi

  if [ -z "$JAR_HASH" ]; then
    psoxy_build_warn "Computed empty SHA-256 hash for ${jar_path}; continuing without hash."
  fi
}
