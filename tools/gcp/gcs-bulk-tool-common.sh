#!/bin/bash
# Shared helpers for GCS bulk debugging scripts.

gcs_bulk_tool_default_timestamp_one_week_ago() {
    if [[ "$OSTYPE" == "darwin"* ]]; then
        date -u -v-7d '+%Y-%m-%dT%H:%M:%SZ'
    else
        date -u -d '1 week ago' '+%Y-%m-%dT%H:%M:%SZ'
    fi
}

gcs_bulk_tool_validate_timestamp() {
    local timestamp="$1"
    [[ "$timestamp" =~ ^[0-9]{4}-[0-9]{2}-[0-9]{2}T[0-9]{2}:[0-9]{2}:[0-9]{2}Z$ ]]
}

gcs_bulk_tool_timestamp_to_epoch() {
    local timestamp="$1"
    if [[ "$OSTYPE" == "darwin"* ]]; then
        date -j -f "%Y-%m-%dT%H:%M:%SZ" "$timestamp" +%s
    else
        date -d "$timestamp" +%s
    fi
}

gcs_bulk_tool_strip_gs_uri() {
    local value="$1"
    if [[ "$value" == gs://* ]]; then
        value="${value#gs://}"
    fi
    echo "$value"
}

gcs_bulk_tool_derive_sanitized_bucket() {
    local input_bucket="$1"
    if [[ "$input_bucket" == *-input ]]; then
        echo "${input_bucket%-input}-sanitized"
    else
        echo ""
    fi
}

gcs_bulk_tool_expected_sanitized_key() {
    local input_key="$1"
    local input_base_path="$2"
    local output_base_path="$3"
    local relative="$input_key"
    if [ -n "$input_base_path" ]; then
        if [[ "$relative" == "$input_base_path"* ]]; then
            relative="${relative#$input_base_path}"
        fi
    fi
    echo "${output_base_path}${relative}"
}

gcs_bulk_tool_creation_time_to_epoch() {
    local creation_time="$1"
    if [[ "$OSTYPE" == "darwin"* ]]; then
        date -j -f "%Y-%m-%dT%H:%M:%SZ" "$creation_time" +%s 2>/dev/null || \
            date -j -f "%Y-%m-%d %H:%M:%S" "$creation_time" +%s 2>/dev/null
    else
        date -d "$creation_time" +%s 2>/dev/null
    fi
}

# List gs://bucket/prefix objects updated after timestamp_epoch.
# Writes lines: updated_time<TAB>object_key (key without gs://bucket/)
gcs_bulk_tool_list_input_objects_modified_after() {
    local bucket="$1"
    local prefix="$2"
    local timestamp_epoch="$3"
    local outfile="$4"

    local list_uri="gs://${bucket}/**"
    if [ -n "$prefix" ]; then
        list_uri="gs://${bucket}/${prefix}**"
    fi

    while read -r line; do
        if [[ "$line" =~ ^[0-9]+[[:space:]] ]]; then
            local creation_time
            creation_time=$(echo "$line" | awk '{print $2}')
            local object_path
            object_path=$(echo "$line" | awk '{print $3}')
            local creation_epoch
            creation_epoch=$(gcs_bulk_tool_creation_time_to_epoch "$creation_time")
            if [ -n "$creation_epoch" ] && [ "$creation_epoch" -gt "$timestamp_epoch" ]; then
                local key="${object_path#gs://${bucket}/}"
                printf '%s\t%s\n' "$creation_time" "$key" >> "$outfile"
            fi
        fi
    done < <(gsutil ls -l "$list_uri" 2>/dev/null)
}

gcs_bulk_tool_sanitized_object_exists() {
    local bucket="$1"
    local key="$2"
    gsutil -q stat "gs://${bucket}/${key}" 2>/dev/null
}
