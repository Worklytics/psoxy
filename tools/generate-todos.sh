#!/bin/bash
# Write Psoxy TODO markdown files from `terraform output`.
#
# Run from the root of a Terraform configuration (an AWS or GCP example, or a
# deployment based on one). After `terraform apply`, this reads the `todo_files`
# output (filename => markdown) and writes those files into the current directory.
#
# If `todo_files` is absent (older modules), falls back to string outputs named
# `todos_1`, `todos_2`, … and writes them as `todos_N.md`.
#
# Existing files are left unchanged unless you confirm overwrite (default Y) or
# pass --yes. A non-interactive run never overwrites existing files unless --yes
# is set.
#
# Usage:
#   ./generate-todos.sh
#   ./generate-todos.sh --yes
#
# Tests (no cloud credentials): ./tools/generate-todos.test.sh

set -euo pipefail

COLORSCHEME_SH="$(dirname "$0")/set-term-colorscheme.sh"
if [ -f "$COLORSCHEME_SH" ]; then
  # shellcheck disable=SC1090
  source "$COLORSCHEME_SH"
else
  ERR='\033[0;31m'; SUCCESS='\033[0;32m'; WARN='\033[1;33m'; INFO='\033[0;34m'; CODE='\033[0;36m'; NC='\033[0m'
fi

# Bold on top of the semantic colors, only when the terminal can show them.
BOLD=''
if [ -t 1 ] && command -v tput >/dev/null 2>&1; then
  ncolors=$(tput colors 2>/dev/null || true)
  if [ -n "$ncolors" ] && [ "$ncolors" -ge 8 ]; then
    BOLD=$(tput bold)
  fi
fi

ASSUME_YES=0

plural_s() {
  if [[ "$1" -eq 1 ]]; then
    printf ''
  else
    printf 's'
  fi
}

usage() {
  cat <<EOF
Usage: $(basename "$0") [--yes]

Write TODO markdown files from \`terraform output\` into the current directory.
Run this from the root of a Psoxy Terraform configuration, after \`terraform apply\`.

  --yes, -y   Overwrite existing TODO files without prompting
  --help, -h  Show this help

When a TODO file already exists, you are asked whether to overwrite it [Y/n].
Press Enter to overwrite. Answer n to leave the file unchanged.
EOF
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    -y|--yes)
      ASSUME_YES=1
      shift
      ;;
    -h|--help)
      usage
      exit 0
      ;;
    *)
      printf "${ERR}Unknown argument: %s${NC}\n" "$1" >&2
      usage >&2
      exit 1
      ;;
  esac
done

if ! command -v terraform >/dev/null 2>&1; then
  printf "${ERR}terraform is not installed or not on PATH.${NC}\n" >&2
  exit 1
fi

if ! command -v jq >/dev/null 2>&1; then
  printf "${ERR}jq is required to read terraform output.${NC}\n" >&2
  printf "Install it with ${CODE}brew install jq${NC} or your platform package manager.\n" >&2
  exit 1
fi

shopt -s nullglob
tf_files=( *.tf )
shopt -u nullglob
if [[ ${#tf_files[@]} -eq 0 ]]; then
  printf "${ERR}No Terraform configuration (*.tf) in %s.${NC}\n" "$(pwd)" >&2
  printf "Run this script from the root of your Psoxy Terraform configuration.\n" >&2
  exit 1
fi

printf "${BOLD}${INFO}Reading Terraform outputs...${NC}\n"

if ! outputs_json="$(terraform output -json)"; then
  printf "${ERR}terraform output failed. Apply this configuration first, then re-run.${NC}\n" >&2
  exit 1
fi

# NUL-delimited filename/content pairs. Prefer the structured todo_files map.
# Fall back to joined todos_N string outputs only when that map is not present.
# One compact JSON object per line ({key, value}). Newlines inside markdown stay escaped,
# so bash can read a record per line. jq cannot emit NUL delimiters.
files_stream="$(printf '%s' "$outputs_json" | jq -c '
  def entries_from_map:
    [ to_entries[]
      | select(.value != null and (.value | type) == "string" and .value != "")
      | {key: .key, value: .value} ];

  def joined_blobs:
    [ to_entries[]
      | select(.key | test("^todos_[0-9]+$"))
      | select(.value.value != null and (.value.value | type) == "string" and .value.value != "")
      | {key: (.key + ".md"), value: .value.value} ];

  (if has("todo_files") and (.todo_files.value | type) == "object" then
    .todo_files.value | entries_from_map
  else
    joined_blobs
  end)
  | sort_by(.key)
  | .[]
')"

if [[ -z "$files_stream" ]]; then
  printf "${WARN}No TODO content found in Terraform outputs.${NC}\n"
  printf "Expected ${CODE}todo_files${NC} (filename => markdown) or ${CODE}todos_1${NC}, ${CODE}todos_2${NC}, ${CODE}todos_3${NC}.\n"
  exit 0
fi

file_count="$(printf '%s\n' "$files_stream" | grep -c . || true)"
printf "${BOLD}Found ${INFO}%s${NC}${BOLD} TODO file%s.${NC}\n" "$file_count" "$(plural_s "$file_count")"

written=()
overwritten=()
skipped=()
rejected=()
noninteractive_notice=0

safe_filename() {
  local name="$1"
  # Bash variables cannot contain NUL, so only reject empty names, directories, and newlines.
  if [[ -z "$name" || "$name" == *"/"* || "$name" == *".."* || "$name" == *$'\n'* ]]; then
    return 1
  fi
  case "$name" in
    /*|.*) return 1 ;;
  esac
  return 0
}

confirm_overwrite() {
  local name="$1"
  local reply=""

  if [[ "$ASSUME_YES" -eq 1 ]]; then
    return 0
  fi
  if [[ ! -t 0 ]]; then
    return 1
  fi

  printf "${WARN}Already exists:${NC} ${BOLD}${CODE}%s${NC}\n" "$name"
  printf "Overwrite? ${BOLD}${INFO}[Y/n]${NC} "
  read -r reply || reply="n"
  reply="${reply:-Y}"
  case "$reply" in
    [yY]|[yY][eE][sS]) return 0 ;;
    *) return 1 ;;
  esac
}

write_file() {
  local name="$1"
  local content="$2"
  local tmp
  tmp="$(mktemp)"
  printf '%s' "$content" > "$tmp"
  if [[ -n "$content" && "${content: -1}" != $'\n' ]]; then
    printf '\n' >> "$tmp"
  fi
  mv "$tmp" "$name"
}

# Read records on fd 3 so prompts can use the terminal on stdin.
while IFS= read -r record <&3; do
  filename="$(printf '%s' "$record" | jq -r '.key')"
  content="$(printf '%s' "$record" | jq -r '.value')"

  if ! safe_filename "$filename"; then
    printf "${ERR}Refusing to write unsafe path:${NC} %s\n" "$filename" >&2
    rejected+=("$filename")
    continue
  fi

  if [[ -e "$filename" ]]; then
    if [[ "$ASSUME_YES" -eq 0 && ! -t 0 && "$noninteractive_notice" -eq 0 ]]; then
      printf "${WARN}Not a terminal. Existing TODO files will be left unchanged. Pass ${CODE}--yes${NC}${WARN} to overwrite them.${NC}\n"
      noninteractive_notice=1
    fi
    if confirm_overwrite "$filename"; then
      write_file "$filename" "$content"
      overwritten+=("$filename")
    else
      skipped+=("$filename")
    fi
  else
    write_file "$filename" "$content"
    written+=("$filename")
  fi
done 3< <(printf '%s\n' "$files_stream")

printf "\n"
if [[ ${#written[@]} -gt 0 ]]; then
  printf "${BOLD}${SUCCESS}Wrote %s new TODO file%s${NC}\n" "${#written[@]}" "$(plural_s "${#written[@]}")"
  for name in "${written[@]}"; do
    printf "  ${SUCCESS}+${NC} ${CODE}%s${NC}\n" "$name"
  done
fi

if [[ ${#overwritten[@]} -gt 0 ]]; then
  printf "${BOLD}${SUCCESS}Overwrote %s existing TODO file%s${NC}\n" "${#overwritten[@]}" "$(plural_s "${#overwritten[@]}")"
  for name in "${overwritten[@]}"; do
    printf "  ${SUCCESS}~${NC} ${CODE}%s${NC}\n" "$name"
  done
fi

if [[ ${#skipped[@]} -gt 0 ]]; then
  printf "${BOLD}${WARN}Left %s existing TODO file%s unchanged${NC}\n" "${#skipped[@]}" "$(plural_s "${#skipped[@]}")"
  for name in "${skipped[@]}"; do
    printf "  ${WARN}-${NC} ${CODE}%s${NC}\n" "$name"
  done
fi

if [[ ${#rejected[@]} -gt 0 ]]; then
  printf "${BOLD}${ERR}Skipped %s unsafe path%s${NC}\n" "${#rejected[@]}" "$(plural_s "${#rejected[@]}")"
  for name in "${rejected[@]}"; do
    printf "  ${ERR}!${NC} ${CODE}%s${NC}\n" "$name"
  done
  exit 1
fi

total_written=$(( ${#written[@]} + ${#overwritten[@]} ))
if [[ "$total_written" -eq 0 && ${#skipped[@]} -eq 0 ]]; then
  printf "${WARN}Nothing was written.${NC}\n"
fi
