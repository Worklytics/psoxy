# Delete Maven package versions from the GitHub Packages registry.
# Sourced by tools/release/publish-mvn-artifacts.sh

# delete_gh_maven_package_versions <path-to-repo> <version>
# Deletes all co.worklytics.psoxy and com.avaulta.gateway packages at <version>.
# Only -SNAPSHOT versions may be deleted.
delete_gh_maven_package_versions() {
  local path_to_repo="$1"
  local target_version="$2"

  if [ -z "$path_to_repo" ] || [ -z "$target_version" ]; then
    printf "${ERR}delete_gh_maven_package_versions requires path-to-repo and version arguments.${NC}\n"
    return 1
  fi

  if [[ "$target_version" != *-SNAPSHOT ]]; then
    printf "${ERR}Refusing to delete non-SNAPSHOT version ${target_version}; only -SNAPSHOT packages may be removed.${NC}\n"
    return 1
  fi

  if ! command -v jq >/dev/null 2>&1; then
    printf "${WARN}Warning: 'jq' not found. Skipping deletion of GitHub Packages version ${target_version}.${NC}\n"
    return 0
  fi

  if ! command -v gh >/dev/null 2>&1; then
    printf "${WARN}Warning: 'gh' not found. Skipping deletion of GitHub Packages version ${target_version}.${NC}\n"
    return 0
  fi

  printf "Checking for GitHub Packages artifacts at version ${INFO}${target_version}${NC}...\n"

  local repo_full_name
  repo_full_name=$(git -C "$path_to_repo" config --get remote.origin.url | sed -E 's#.*github\.com[:/]+([^/]+/[^/.]+)(\.git)?$#\1#')
  local org_name
  org_name=$(printf '%s\n' "$repo_full_name" | cut -d'/' -f1)

  local group_ids=("co.worklytics.psoxy" "com.avaulta.gateway")
  local packages_json
  packages_json=$(gh api "/orgs/${org_name}/packages?package_type=maven" 2>/dev/null)

  if [ $? -ne 0 ]; then
    printf "${ERR}Warning: Failed to list packages. Check 'read:packages' scope.${NC}\n"
    return 1
  fi

  local group_id pkg versions_json version_id
  for group_id in "${group_ids[@]}"; do
    printf "  Scanning for packages starting with: ${INFO}${group_id}${NC}...\n"

    while IFS= read -r pkg; do
      [ -z "$pkg" ] && continue
      printf "    Checking package: ${INFO}${pkg}${NC} for version ${target_version}...\n"

      versions_json=$(gh api "/orgs/${org_name}/packages/maven/${pkg}/versions" 2>/dev/null)
      if [ $? -ne 0 ]; then
        printf "      ${ERR}Failed to list versions for ${pkg}.${NC}\n"
        continue
      fi

      version_id=$(echo "$versions_json" | jq -r ".[] | select(.name == \"${target_version}\") | .id")
      if [ -n "$version_id" ] && [ "$version_id" != "null" ]; then
        printf "      Found version ${target_version} (ID: ${version_id}). Deleting...\n"
        if gh api -X DELETE "/orgs/${org_name}/packages/maven/${pkg}/versions/${version_id}" 2>/dev/null; then
          printf "      ${SUCCESS}✓ Deleted ${pkg}:${target_version}${NC}\n"
        else
          printf "      ${ERR}✗ Failed to delete ${pkg}:${target_version}. Likely 403 Forbidden.${NC}\n"
          printf "        Ensure your token has ${INFO}delete:packages${NC} scope.\n"
        fi
      fi
    done < <(echo "$packages_json" | jq -r ".[] | select(.name | startswith(\"${group_id}\")) | .name")
  done
}
