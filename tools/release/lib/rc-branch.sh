#!/bin/bash
# Resolve the git branch that holds a release candidate.
#
# Usually that branch is named rc-$RELEASE (e.g. rc-v0.4.16 for v0.4.16). A
# major bump can keep an older rc branch name while shipping a different tag
# (e.g. branch rc-v0.4.16, release v0.5.0).
#
# Sourced by prep.sh, rc-to-main.sh, publish.sh, run-release-qa.sh, and
# qa/verify-release-refs.sh. Expects ERR/WARN/INFO/NC color vars if you want
# colored warnings; they may be empty.

# Usage: resolve_rc_branch <release> [explicit-rc-branch]
# Prints the branch name.
resolve_rc_branch() {
  local release="$1"
  local explicit="${2:-}"
  local current

  if [ -n "$explicit" ]; then
    printf '%s\n' "$explicit"
    return 0
  fi

  current="$(git branch --show-current 2>/dev/null || true)"
  if [[ "$current" =~ ^rc-v[0-9]+\.[0-9]+\.[0-9]+$ ]]; then
    printf '%s\n' "$current"
    return 0
  fi

  printf 'rc-%s\n' "$release"
}

# Usage: warn_if_rc_release_mismatch <rc-branch> <release>
warn_if_rc_release_mismatch() {
  local rc_branch="$1"
  local release="$2"
  local expected="rc-${release}"

  if [ "$rc_branch" != "$expected" ]; then
    printf "${WARN}RC branch '%s' does not match release tag '%s' (naive branch name would be '%s'). Proceeding with '%s'.${NC}\n" \
      "$rc_branch" "$release" "$expected" "$rc_branch"
  fi
}
