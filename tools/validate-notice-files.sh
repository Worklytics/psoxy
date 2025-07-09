#!/bin/bash

# Validation script to check for NOTICE and LICENSE files in Psoxy JARs
# Usage: ./validate-notice-files.sh [aws|gcp]

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(dirname "$SCRIPT_DIR")"

echo "🔍 Validating NOTICE and LICENSE files in Psoxy JARs..."

validate_jar() {
    local module=$1
    local jar_path="$PROJECT_ROOT/java/impl/$module/target/psoxy-$module-*.jar"
    
    echo ""
    echo "📦 Checking $module module..."
    
    # Find the JAR file
    local jar_file=$(ls $jar_path 2>/dev/null | head -1)
    
    if [ ! -f "$jar_file" ]; then
        echo "❌ JAR file not found: $jar_path"
        echo "   Run 'mvn clean package' in java/impl/$module first"
        return 1
    fi
    
    echo "✅ Found JAR: $(basename "$jar_file")"
    
    # Check for NOTICE files
    echo "📋 Checking NOTICE files..."
    local notice_files=$(jar tf "$jar_file" | grep -i notice || true)
    if [ -n "$notice_files" ]; then
        echo "✅ NOTICE files found:"
        echo "$notice_files" | sed 's/^/   /'
    else
        echo "❌ No NOTICE files found!"
    fi
    
    # Check for LICENSE files
    echo "📄 Checking LICENSE files..."
    local license_files=$(jar tf "$jar_file" | grep -i license || true)
    if [ -n "$license_files" ]; then
        echo "✅ LICENSE files found:"
        echo "$license_files" | sed 's/^/   /'
    else
        echo "❌ No LICENSE files found!"
    fi
    
    # Show merged NOTICE content if it exists
    if jar tf "$jar_file" | grep -q "META-INF/NOTICE"; then
        echo ""
        echo "📝 Merged NOTICE file content (first 10 lines):"
        jar xf "$jar_file" META-INF/NOTICE >/dev/null 2>&1 && head -10 META-INF/NOTICE | sed 's/^/   /' || echo "   Could not extract NOTICE file"
        rm -f META-INF/NOTICE
    fi
    
    echo ""
    echo "📊 Summary for $module:"
    local notice_count=$(echo "$notice_files" | wc -l)
    local license_count=$(echo "$license_files" | wc -l)
    echo "   NOTICE files: $notice_count"
    echo "   LICENSE files: $license_count"
}

# Main execution
case "${1:-both}" in
    aws)
        validate_jar "aws"
        ;;
    gcp)
        validate_jar "gcp"
        ;;
esac

echo ""
echo "✅ Validation complete!"