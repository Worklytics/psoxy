#!/bin/bash

# Reset a Psoxy Terraform configuration directory to its pre-init template state.
#
# Optionally backs up local IaC files (terraform.tfvars, git-modified config, etc.)
# under .psoxy-iac-backup/ before deletion. Restore them after re-running ./init
# with --recover.
#
# Usage (from a Terraform config directory, e.g. infra/examples-dev/aws):
#   ../../../tools/reset-example.sh                    # reset; prompts for backup (default Y)
#   ../../../tools/reset-example.sh --backup             # backup only, no reset
#   ../../../tools/reset-example.sh --no-backup          # reset without backup
#   ../../../tools/reset-example.sh --list-backups       # list available backups
#   ../../../tools/reset-example.sh --recover            # restore latest backup
#   ../../../tools/reset-example.sh --recover 20260527-143022

set -euo pipefail

COLORSCHEME_SH="$(dirname "$0")/set-term-colorscheme.sh"
if [ -f "$COLORSCHEME_SH" ]; then
  # shellcheck source=set-term-colorscheme.sh
  source "$COLORSCHEME_SH"
else
  ERR='\033[0;31m'; SUCCESS='\033[0;32m'; WARN='\033[1;33m'; INFO='\033[0;34m'; CODE='\033[0;36m'; NC='\033[0m'
fi

BACKUP_ROOT=".psoxy-iac-backup"
LEGACY_BACKUP_ROOT=".psoxy-example-backup"
BACKUP_CHOICE="" # unset = prompt (default yes); true/false = explicit
MODE="reset"
RECOVER_ID=""

usage() {
  sed -n '2,15p' "$0" | sed 's/^# \{0,1\}//'
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --backup)
      MODE="backup"
      shift
      ;;
    --recover)
      MODE="recover"
      shift
      if [[ $# -gt 0 && "$1" != --* ]]; then
        RECOVER_ID="$1"
        shift
      fi
      ;;
    --list-backups)
      MODE="list"
      shift
      ;;
    --no-backup)
      BACKUP_CHOICE="false"
      shift
      ;;
    -h|--help)
      usage
      exit 0
      ;;
    *)
      printf "${ERR}Unknown option: $1${NC}\n\n"
      usage
      exit 1
      ;;
  esac
done

reset_example_collect_backup_candidates() {
  local candidates=()
  local explicit=(
    terraform.tfvars
    terraform.tfstate
    terraform.tfstate.backup
    build.sh
    update-bundle
    upgrade-terraform-modules.sh
  )
  local f file xy

  for f in "${explicit[@]}"; do
    if [ -e "$f" ]; then
      candidates+=("$f")
    fi
  done

  for f in *.auto.tfvars; do
    if [ -f "$f" ]; then
      candidates+=("$f")
    fi
  done

  if git rev-parse --git-dir >/dev/null 2>&1; then
    while IFS= read -r line; do
      [ -z "$line" ] && continue
      xy="${line:0:2}"
      file="${line:3}"
      if [[ "$xy" == *D* ]] && [[ "$xy" != "??" ]]; then
        continue
      fi
      if [ -f "$file" ]; then
        candidates+=("$file")
      fi
    done < <(git status --porcelain 2>/dev/null || true)
  fi

  if [ "${#candidates[@]}" -eq 0 ]; then
    return 0
  fi

  printf '%s\n' "${candidates[@]}" | awk '!seen[$0]++'
}

reset_example_backup_local_files() {
  local timestamp backup_dir candidate count=0
  local candidates=()

  while IFS= read -r candidate; do
    [ -n "$candidate" ] && candidates+=("$candidate")
  done < <(reset_example_collect_backup_candidates)

  if [ "${#candidates[@]}" -eq 0 ]; then
    printf "${INFO}No local IaC files to back up.${NC}\n"
    return 0
  fi

  timestamp="$(date +%Y%m%d-%H%M%S)"
  backup_dir="${BACKUP_ROOT}/${timestamp}"
  mkdir -p "$backup_dir"

  for candidate in "${candidates[@]}"; do
    if [ ! -e "$candidate" ]; then
      continue
    fi
    mkdir -p "$backup_dir/$(dirname "$candidate")"
    cp -p "$candidate" "$backup_dir/$candidate"
    count=$((count + 1))
  done

  printf '%s\n' "${candidates[@]}" > "${backup_dir}/.backup-manifest"
  printf 'created=%s\nfile_count=%s\n' "$timestamp" "$count" > "${backup_dir}/.backup-meta"

  rm -f "${BACKUP_ROOT}/latest"
  ln -s "$timestamp" "${BACKUP_ROOT}/latest"

  printf "${SUCCESS}Backed up ${count} file(s) to ${CODE}${backup_dir}${NC}\n"
  printf "Restore with: ${CODE}$(basename "$0") --recover${NC}\n\n"
}

reset_example_prompt_backup() {
  if [ "$BACKUP_CHOICE" = "false" ]; then
    return 1
  fi
  if [ "$BACKUP_CHOICE" = "true" ]; then
    return 0
  fi

  printf "Back up local IaC files (e.g. ${CODE}terraform.tfvars${NC}) to ${CODE}${BACKUP_ROOT}/${NC}?\n"
  read -r -p "[Y/n]: " response
  if [[ -z "$response" || "$response" =~ ^([yY][eE][sS]|[yY])$ ]]; then
    return 0
  fi
  return 1
}

reset_example_maybe_backup() {
  if reset_example_prompt_backup; then
    reset_example_backup_local_files
    return 0
  fi
  printf "${INFO}Skipping backup.${NC}\n\n"
  return 1
}

reset_example_list_backups() {
  local found=false
  local root

  for root in "$BACKUP_ROOT" "$LEGACY_BACKUP_ROOT"; do
    if [ ! -d "$root" ]; then
      continue
    fi
    found=true
    printf "${INFO}Available backups in ${CODE}${root}/${NC}:\n"
    local entry latest_target=""
    if [ -L "${root}/latest" ]; then
      latest_target="$(readlink "${root}/latest")"
    fi

    while IFS= read -r entry; do
      [ -z "$entry" ] && continue
      [ "$entry" = "latest" ] && continue
      if [ -f "${root}/${entry}/.backup-meta" ]; then
        # shellcheck source=/dev/null
        source "${root}/${entry}/.backup-meta"
        if [ "$entry" = "$latest_target" ]; then
          printf "  ${SUCCESS}${entry}${NC} (${file_count} files) ${INFO}[latest]${NC}\n"
        else
          printf "  ${entry} (${file_count} files)\n"
        fi
      else
        printf "  ${entry}\n"
      fi
    done < <(find "$root" -mindepth 1 -maxdepth 1 -type d -exec basename {} \; | sort -r)
    printf "\n"
  done

  if [ "$found" = "false" ]; then
    printf "${INFO}No backups found under ${CODE}${BACKUP_ROOT}/${NC}"
    if [ -d "$LEGACY_BACKUP_ROOT" ]; then
      printf " or ${CODE}${LEGACY_BACKUP_ROOT}/${NC}"
    fi
    printf ".\n"
    return 0
  fi

  printf "Restore latest with: ${CODE}$(basename "$0") --recover${NC}\n"
}

reset_example_resolve_backup_dir() {
  local backup_id="${1:-latest}"
  local root backup_dir=""

  for root in "$BACKUP_ROOT" "$LEGACY_BACKUP_ROOT"; do
    [ -d "$root" ] || continue

    if [ "$backup_id" = "latest" ]; then
      if [ -L "${root}/latest" ]; then
        backup_dir="${root}/$(readlink "${root}/latest")"
        break
      elif [ -d "${root}/latest" ]; then
        backup_dir="${root}/latest"
        break
      fi
    elif [ -d "${root}/${backup_id}" ]; then
      backup_dir="${root}/${backup_id}"
      break
    fi
  done

  if [ -z "$backup_dir" ] || [ ! -d "$backup_dir" ]; then
    printf "${ERR}Error: backup not found" >&2
    if [ -n "$backup_id" ]; then
      printf " (${backup_id})" >&2
    fi
    printf ".${NC}\n" >&2
    printf "Run ${CODE}$(basename "$0") --list-backups${NC} to see available backups.\n" >&2
    return 1
  fi

  printf '%s' "$backup_dir"
}

reset_example_recover_backup() {
  local backup_id="${1:-latest}"
  local backup_dir file count=0

  backup_dir="$(reset_example_resolve_backup_dir "$backup_id")" || return 1

  if [ ! -f "${backup_dir}/.backup-manifest" ]; then
    printf "${ERR}Error: backup manifest missing in ${backup_dir}${NC}\n" >&2
    return 1
  fi

  printf "${INFO}Recover local files from backup:${NC} ${CODE}${backup_dir}${NC}\n"
  if [ -f "${backup_dir}/.backup-meta" ]; then
    # shellcheck source=/dev/null
    source "${backup_dir}/.backup-meta"
    printf "  created: ${created}\n"
    printf "  files:   ${file_count}\n\n"
  fi

  printf "${WARN}The following files will be written in the current directory:${NC}\n"
  while IFS= read -r file; do
    [ -z "$file" ] && continue
    if [ ! -f "${backup_dir}/${file}" ]; then
      printf "  ${WARN}${file}${NC} (missing from backup; will skip)\n"
    elif [ -f "$file" ]; then
      printf "  ${CODE}${file}${NC} ${WARN}(overwrite existing)${NC}\n"
    else
      printf "  ${CODE}${file}${NC} (create)\n"
    fi
  done < "${backup_dir}/.backup-manifest"
  printf "\n"

  read -r -p "Continue? (y/N): " response
  if [[ ! "$response" =~ ^([yY][eE][sS]|[yY])$ ]]; then
    printf "Recovery cancelled.\n"
    return 0
  fi

  while IFS= read -r file; do
    [ -z "$file" ] && continue
    if [ ! -f "${backup_dir}/${file}" ]; then
      printf "${WARN}Skipping missing backup file: ${file}${NC}\n"
      continue
    fi
    mkdir -p "$(dirname "$file")"
    cp -p "${backup_dir}/${file}" "$file"
    count=$((count + 1))
    printf "  ${SUCCESS}restored${NC} ${file}\n"
  done < "${backup_dir}/.backup-manifest"

  printf "\n${SUCCESS}Recovered ${count} file(s) from backup.${NC}\n"
}

reset_example_restore_git_tracked_file() {
  local file="$1"

  if git status --short 2>/dev/null | grep -q "^ D ${file}$"; then
    printf "Restoring deleted configuration file ${INFO}${file}${NC} ...\n"
    git checkout HEAD -- "$file"
  fi
}

reset_example_do_reset() {
  rm -f .terraform.lock.hcl 2>/dev/null || true
  rm -f build build.sh 2>/dev/null || true
  rm -f update-bundle 2>/dev/null || true
  rm -f psoxy-* 2>/dev/null || true
  rm -rf .terraform 2>/dev/null || true
  rm -f terraform.tfvars 2>/dev/null || true
  rm -f terraform.tfstate terraform.tfstate.backup 2>/dev/null || true

  printf "Restoring ${INFO}main.tf${NC} from git ...\n"
  git checkout HEAD -- main.tf 2>/dev/null || true

  local files=(
    msft-365.tf
    msft-365-variables.tf
    google-workspace.tf
    google-workspace-variables.tf
  )
  local file
  for file in "${files[@]}"; do
    reset_example_restore_git_tracked_file "$file"
  done

  rm -f upgrade-terraform-modules upgrade-terraform-modules.sh 2>/dev/null || true

  printf "\n${SUCCESS}Reset complete.${NC}\n"
}

reset_example_run_reset() {
  local backed_up=false
  if reset_example_maybe_backup; then
    backed_up=true
  fi

  printf "This will ${ERR}delete${NC} local Terraform state, variable files, and init artifacts,\n"
  printf "restoring tracked configuration files from git.\n"
  if [ "$backed_up" != "true" ] && [ "$BACKUP_CHOICE" != "true" ]; then
    printf "If you have ${ERR}NOT${NC} committed local changes, they will be ${ERR}lost${NC}.\n"
  fi
  read -r -p "Continue with reset? (y/N): " response
  if [[ ! "$response" =~ ^([yY][eE][sS]|[yY])$ ]]; then
    printf "Reset cancelled."
    if [ "$backed_up" = "true" ]; then
      printf " Backup was already saved under ${CODE}${BACKUP_ROOT}/${NC}."
    fi
    printf "\n"
    exit 0
  fi

  reset_example_do_reset
  if [ "$backed_up" = "true" ]; then
    printf "Re-run ${CODE}./init${NC} if needed, then restore your settings with ${CODE}$(basename "$0") --recover${NC}\n"
  fi
}

case "$MODE" in
  backup)
    BACKUP_CHOICE="true"
    reset_example_backup_local_files
    ;;
  list)
    reset_example_list_backups
    ;;
  recover)
    reset_example_recover_backup "${RECOVER_ID:-latest}"
    ;;
  reset)
    reset_example_run_reset
    ;;
esac
