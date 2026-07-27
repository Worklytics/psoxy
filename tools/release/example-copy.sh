#!/bin/bash

COLORSCHEME_SH="$(dirname "$0")/../set-term-colorscheme.sh"
if [ -f "$COLORSCHEME_SH" ]; then
    source "$COLORSCHEME_SH"
else
    ERR='\033[0;31m'; SUCCESS='\033[0;32m'; WARN='\033[1;33m'; INFO='\033[0;34m'; CODE='\033[0;36m'; NC='\033[0m'
fi

EXAMPLE_TO_COPY_FROM=$1
EXAMPLE_TEMPLATE_REPO=$2
PATH_TO_MAIN_REPO_ROOT=$3

if [ -z "$EXAMPLE_TO_COPY_FROM" ]; then
  printf "${ERR}Path to example is required.${NC}\n"
  printf "Usage: ./example-copy.sh <path-to-example> <path-to-example-repo> [path-to-main-repo]\n"
  exit 1
fi

if [ -z "$EXAMPLE_TEMPLATE_REPO" ]; then
  printf "${ERR}Path to example repo is required.${NC}\n"
  printf "Usage: ./example-copy.sh <path-to-example> <path-to-example-repo> [path-to-main-repo]\n"
  exit 1
fi

# append / if needed
if [[ "${EXAMPLE_TEMPLATE_REPO: -1}" != "/" ]]; then
    EXAMPLE_TEMPLATE_REPO="$EXAMPLE_TEMPLATE_REPO/"
fi
if [ -n "$PATH_TO_MAIN_REPO_ROOT" ] && [[ "${PATH_TO_MAIN_REPO_ROOT: -1}" != "/" ]]; then
    PATH_TO_MAIN_REPO_ROOT="$PATH_TO_MAIN_REPO_ROOT/"
fi

# Copy a text file while forcing Unix (LF) line endings.
# CRLF shebangs fail under WSL/Linux with: bad interpreter: /bin/bash^M
copy_lf() {
  local src="$1"
  local dest="$2"
  tr -d '\r' < "$src" > "$dest"
}

# Copy an executable script with LF endings and +x.
copy_script_lf() {
  local src="$1"
  local dest="$2"
  printf "copying ${CODE}%s${NC} -> ${CODE}%s${NC} (LF)\n" "$src" "$dest"
  copy_lf "$src" "$dest"
  chmod +x "$dest"
}

cd "$EXAMPLE_TO_COPY_FROM"
FILES_TO_COPY=( *.tf )

for file in "${FILES_TO_COPY[@]}"
do
  if [ -f ${EXAMPLE_TO_COPY_FROM}/${file} ]; then
     echo "copying ${EXAMPLE_TO_COPY_FROM}/${file} to ${EXAMPLE_TEMPLATE_REPO}${file}"
     copy_lf "${EXAMPLE_TO_COPY_FROM}/${file}" "${EXAMPLE_TEMPLATE_REPO}${file}"

     # uncomment Terraform module remotes
     sed -i .bck 's/^\(.*\)# source = "git::\(.*\)"/\1source = "git::\2"/' "${EXAMPLE_TEMPLATE_REPO}${file}"

     # remove references to local modules
     sed -i .bck '/source = "..\/..\/modules\/[^"]*"/d' "${EXAMPLE_TEMPLATE_REPO}${file}"
  fi
done

rm ${EXAMPLE_TEMPLATE_REPO}*.bck

# copy the README template intended to be published to the example repo
copy_lf README.template.md "${EXAMPLE_TEMPLATE_REPO}README.md"

# copy AGENTS template if it exists
if [ -f AGENTS.template.md ]; then
  copy_lf AGENTS.template.md "${EXAMPLE_TEMPLATE_REPO}AGENTS.md"
fi

copy_script_lf "${PATH_TO_MAIN_REPO_ROOT}tools/init-example.sh" "${EXAMPLE_TEMPLATE_REPO}init"
copy_script_lf "${PATH_TO_MAIN_REPO_ROOT}tools/check-prereqs.sh" "${EXAMPLE_TEMPLATE_REPO}check-prereqs"

if [ -f "${EXAMPLE_TO_COPY_FROM}/preflight.sh" ]; then
  copy_script_lf "${EXAMPLE_TO_COPY_FROM}/preflight.sh" "${EXAMPLE_TEMPLATE_REPO}preflight"
fi

copy_script_lf "${PATH_TO_MAIN_REPO_ROOT}tools/available-connectors.sh" "${EXAMPLE_TEMPLATE_REPO}available-connectors"
copy_script_lf "${PATH_TO_MAIN_REPO_ROOT}tools/az-auth.sh" "${EXAMPLE_TEMPLATE_REPO}az-auth"

# Force LF on checkout for customer-facing scripts (overrides Git for Windows autocrlf).
# Scoped to scripts only — do not force LF on all text in the example repo.
cat > "${EXAMPLE_TEMPLATE_REPO}.gitattributes" <<'EOF'
# Shell scripts must stay LF; CRLF breaks shebangs under WSL/Linux
# (bad interpreter: /bin/bash^M).
*.sh text eol=lf

# Extensionless customer-facing scripts published to this example repo
init text eol=lf
check-prereqs text eol=lf
available-connectors text eol=lf
az-auth text eol=lf
preflight text eol=lf
EOF

# Dev-only artifacts present in examples-dev (symlinks, local scripts, backups).
# Never publish these to customer example repos — cp would follow symlinks and copy script contents.
DEV_ONLY_ARTIFACTS=(
  reset-example
  build.sh
  upgrade-terraform-modules.sh
  .psoxy-iac-backup
)

for artifact in "${DEV_ONLY_ARTIFACTS[@]}"; do
  if [ -e "${EXAMPLE_TEMPLATE_REPO}${artifact}" ] || [ -L "${EXAMPLE_TEMPLATE_REPO}${artifact}" ]; then
    printf "${INFO}Removing dev-only artifact from example template: ${CODE}${artifact}${NC}\n"
    rm -rf "${EXAMPLE_TEMPLATE_REPO}${artifact}"
  fi
done

if [ -e "${EXAMPLE_TEMPLATE_REPO}reset-example" ] || [ -L "${EXAMPLE_TEMPLATE_REPO}reset-example" ]; then
  printf "${ERR}Error: reset-example still present in ${EXAMPLE_TEMPLATE_REPO} after publish copy.${NC}\n"
  exit 1
fi
