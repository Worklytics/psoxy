#!/usr/bin/env bash

variable=$1
filename="${2:-terraform.tfvars}"

# TODO: extend to look at all *.tfvars files in the directory, not just terraform.tfvars

json="{"


# Parse the file, find variable, convert it to JSON
# this approach of parsing it from a `terr
# alternative to parsing it ourselves would be `echo var.$variable | terraform console`, but that is
# slow and brittle to errors/warnings from terraform console.
if [ -f $filename ]; then
  while IFS='=' read -r key value; do
      # Skip commented lines
      [[ $key =~ ^#.*$ ||  -z $key ]] && continue


      # Remove leading and trailing whitespaces
      key=$(echo $key | xargs)
      value=$(echo $value | xargs)

      [[ $key != "$variable" ]] && continue

      # Append to json string
      json="$json\"$key\": \"$value\","
  done <"$filename"
fi

# Remove the trailing comma and close the JSON object
json="${json%,}}"
echo $json
