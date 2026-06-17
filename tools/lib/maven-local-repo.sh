#!/bin/bash

# Shared Maven settings for Psoxy tooling builds.
# Source after setting PSOXY_CHECKOUT_ROOT to the repository root.
#
# Exports:
#   PSOXY_MAVEN_LOCAL_REPO - passed to mvn as -Dmaven.repo.local when set (local dev only)
#
# Callers may export PSOXY_SKIP_OPENNLP=1 before sourcing to skip OpenNLP model downloads.

if [ -z "${PSOXY_CHECKOUT_ROOT:-}" ]; then
    printf 'PSOXY_CHECKOUT_ROOT must be set before sourcing maven-local-repo.sh\n' >&2
    exit 1
fi

COLORSCHEME_SH="$(dirname "$0")/../set-term-colorscheme.sh"
if [ -f "$COLORSCHEME_SH" ]; then
    # shellcheck source=/dev/null
    source "$COLORSCHEME_SH"
else
    INFO='\033[0;34m'
    NC='\033[0m'
fi

# When not in CI, use a checkout-local Maven repository to avoid polluting the global cache.
# Treat CI as "not in CI" when CI is unset, empty, or explicitly set to "false".
if [ -z "${PSOXY_MAVEN_LOCAL_REPO:-}" ]; then
    CI_VALUE="${CI:-}"
    if [ -z "$CI_VALUE" ] || [ "$CI_VALUE" = "false" ] || [ "$CI_VALUE" = "False" ]; then
        PSOXY_MAVEN_LOCAL_REPO="${PSOXY_CHECKOUT_ROOT}/.m2/repository"
        mkdir -p "${PSOXY_MAVEN_LOCAL_REPO}"
        printf "${INFO}Running locally (not in CI). Using Maven repository at ${PSOXY_MAVEN_LOCAL_REPO}${NC}\n"
    fi
fi

export PSOXY_MAVEN_LOCAL_REPO
