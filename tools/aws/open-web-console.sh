#!/bin/bash

# Constants for colored output
RED='\033[0;31m'
GREEN='\033[0;32m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Function to print error and exit
error_exit() {
    printf "${RED}%s${NC}\n" "$1"
    exit 1
}

# Check if jq is installed
if ! command -v jq &> /dev/null; then
    error_exit "Error: jq is not installed. Please install jq to use this script."
fi

# Check if python3 is installed
if ! command -v python3 &> /dev/null; then
    error_exit "Error: python3 is not installed. Please install python3 to use this script."
fi


# Check if terraform.tfvars file exists
TFVARS_FILE="terraform.tfvars"
if [[ ! -f $TFVARS_FILE ]]; then
    error_exit "Error: $TFVARS_FILE not found!"
fi

# Parse aws_assume_role_arn from terraform.tfvars
aws_assume_role_arn=$(awk -F'=' '/^aws_assume_role_arn/ {gsub(/^[ \t]+|[ \t]+$/, "", $2); gsub(/"/, "", $2); print $2}' "$TFVARS_FILE")
if [[ -z "$aws_assume_role_arn" ]]; then
    error_exit "Error: aws_assume_role_arn not found in $TFVARS_FILE!"
fi

aws_region=$(awk -F'=' '/^aws_region/ {gsub(/^[ \t]+|[ \t]+$/, "", $2); gsub(/"/, "", $2); print $2}' "$TFVARS_FILE")
if [[ -z "$aws_region" ]]; then
    error_exit "Error: aws_region not found in $TFVARS_FILE!"
fi

# Extract AWS Account ID and Role Name from the ARN
account_id=$(echo "$aws_assume_role_arn" | cut -d ':' -f 5)
role_name=$(echo "$aws_assume_role_arn" | cut -d '/' -f 2)

if [[ -z "$account_id" || -z "$role_name" ]]; then
    error_exit "Error: Unable to extract Account ID or Role Name from ARN!"
fi

# Assume the role using AWS CLI
printf "Parsed ${BLUE}%s${NC} from your ${BLUE}terraform.tfvars${NC}\n" "$aws_assume_role_arn"
printf "Do you want to open AWS Management Console as this role in your web browser? This will log you out of any existing AWS session in that browser. (Y/n): "
# prompt user to confirm opening the console, default to Y
read -r OPEN_CONSOLE
OPEN_CONSOLE=${OPEN_CONSOLE:-Y}
echo   # move to a new line
if [[ ! $OPEN_CONSOLE =~ ^[Yy]$ ]]; then
    printf "Aborting...\n"
    exit 0
fi


credentials=$(aws sts assume-role --role-arn "$aws_assume_role_arn" --role-session-name "SessionFromBash" --query 'Credentials.[AccessKeyId,SecretAccessKey,SessionToken]' --output text)

if [[ $? -ne 0 ]]; then
    error_exit "Error: Failed to assume role!"
fi

# Extract credentials
AWS_ACCESS_KEY_ID=$(echo "$credentials" | awk '{print $1}')
AWS_SECRET_ACCESS_KEY=$(echo "$credentials" | awk '{print $2}')
AWS_SESSION_TOKEN=$(echo "$credentials" | awk '{print $3}')

# Create a URL that allows console login with temporary credentials
json_data=$(printf '{"sessionId":"%s","sessionKey":"%s","sessionToken":"%s"}' "$AWS_ACCESS_KEY_ID" "$AWS_SECRET_ACCESS_KEY" "$AWS_SESSION_TOKEN")

# Encode the JSON object
encoded_data=$(python3 -c "import urllib.parse; print(urllib.parse.quote('''$json_data'''))")

# Generate the console URL
signin_url="https://signin.aws.amazon.com/federation?Action=getSigninToken&Session=$encoded_data"

signin_token=$(curl -s "$signin_url" | jq -r .SigninToken)

if [[ -z "$signin_token" ]]; then
    error_exit "Error: Failed to generate sign-in token!"
fi

# Generate final console URL with sign-in token
console_url="https://signin.aws.amazon.com/federation?Action=login&Issuer=Example&Destination=https%3A%2F%2Fconsole.aws.amazon.com%2F&SigninToken=$signin_token"

# Open the AWS Management Console in the default browser
printf "${GREEN}Opening AWS Management Console for account %s and role %s...${NC}\n" "$account_id" "$role_name"
open "$console_url"