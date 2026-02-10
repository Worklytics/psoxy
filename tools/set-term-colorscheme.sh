#!/bin/bash

# Centralized terminal color scheme script
# Sources semantic color variables for use in other scripts
# Retains NC (No Color) for reset

# Default to no color
ERROR=''
WARNING=''
SUCCESS=''
CODE=''
NC=''

# Check if stdout is a terminal
if [ -t 1 ]; then
    # Use tput if available for portability
    if command -v tput >/dev/null 2>&1; then
        ncolors=$(tput colors)
        if [ -n "$ncolors" ] && [ "$ncolors" -ge 8 ]; then
            # Standard ANSI colors
            RED=$(tput setaf 1)
            GREEN=$(tput setaf 2)
            YELLOW=$(tput setaf 3)
            BLUE=$(tput setaf 4)
            CYAN=$(tput setaf 6)
            
            # Reset
            NC=$(tput sgr0)

            # Semantic mappings
            ERROR="${RED}"
            WARNING="${YELLOW}"
            SUCCESS="${GREEN}"
            
            # Dynamic CODE color based on background
            # Attempt to detect background color (not standard, but common hack is to rely on user env vars or just default)
            # Since we can't reliably detect background color in a portable way without user input or complex escape sequences that might hang,
            # we will default to BLUE which is generally visible on both, or CYAN if we suspect dark mode.
            
            # However, standard practice to support both is often to use a color that stands out on both, or just stick to one.
            # Blue is hard to read on Black. Cyan is better on Black. Blue is better on White.
            # Let's try to infer from COLORFGBG if set (rxvt/xterm)
            
            # Use CYAN for CODE: generally readable on both dark and light backgrounds.
            CODE="${CYAN}"
        fi
    else
        # Fallback to manual escape codes if tput not found
        ERROR='\033[0;31m'
        WARNING='\033[0;33m'
        SUCCESS='\033[0;32m'
        CODE='\033[0;36m' # Cyan
        NC='\033[0m'
    fi
fi

# Export variables so they are available to sourcing script
export ERROR
export WARNING
export SUCCESS
export CODE
export NC
