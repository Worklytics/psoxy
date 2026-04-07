#!/bin/bash

# Centralized terminal color scheme script
# Sources semantic color variables for use in other scripts
# Complies with Worklytics AGENTS.md testing conventions

# Default to no color
ERR=''
WARN=''
SUCCESS=''
INFO=''
CODE=''
NC=''

# Check if stdout is a terminal
if [ -t 1 ]; then
    # Use tput if available for portability
    if command -v tput >/dev/null 2>&1; then
        ncolors=$(tput colors)
        if [ -n "$ncolors" ] && [ "$ncolors" -ge 8 ]; then
            # Semantic mappings dynamically based on terminal capability
            ERR=$(tput setaf 1)
            SUCCESS=$(tput setaf 2)
            WARN=$(tput setaf 3)
            INFO=$(tput setaf 4)
            CODE=$(tput setaf 6) # Cyan
            NC=$(tput sgr0)
        fi
    else
        # Fallback to manual escape codes if tput not found
        ERR='\033[0;31m'
        SUCCESS='\033[0;32m'
        WARN='\033[1;33m'
        INFO='\033[0;34m'
        CODE='\033[0;36m' # Cyan
        NC='\033[0m'
    fi
fi

# Export variables so they are available to sourcing script
export ERR WARN SUCCESS INFO CODE NC
