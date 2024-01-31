#!/bin/bash
# generate keys for MSFT connectors; adapted from Worklytics node script to do this, with output
# formatted to be consumed by Terraform via `data "external" {}`
# example usage: ./local-cert-standalone.sh "/C=US/ST=New York/L=New York/CN=www.worklytics.co" 30 OUTLOOK_MAIL
SUBJECT=$1
TTL=$2
AZURE_TOOL=$3

MD5_CMD='md5sum'
if [[ $OSTYPE == 'darwin'* ]]; then
  MD5_CMD='md5 -r'
fi

BLUE='\e[0;34m'
NC='\e[0m' # No Color

# avoid conflict if building multiple connectors concurrently
RAND_ID=`echo $RANDOM | $MD5_CMD | head -c 20`
KEY_FILE=$3_key_${RAND_ID}.pem
KEY_FILE_PKCS8=$3_key_pkcs8_${RAND_ID}.pem
CERT_FILE=$3_cert_${RAND_ID}.pem

# build generate key
openssl req -x509 -newkey rsa:2048 -subj "${SUBJECT}" -keyout $KEY_FILE -out $CERT_FILE -days $TTL -nodes >/dev/null 2>&1
openssl pkcs8 -nocrypt -in $KEY_FILE  -inform PEM -topk8 -outform PEM -out $KEY_FILE_PKCS8 >/dev/null 2>&1

FINGERPRINT_RESULT_RAW=`openssl x509 -in $CERT_FILE -noout -fingerprint -sha1`
FINGERPRINT_RESULT=`echo $FINGERPRINT_RESULT_RAW | sed 's/://g' | sed 's/SHA1 Fingerprint=//gi'`

OUTPUT_FILE="TODO_${AZURE_TOOL}_CERTS.md"
rm -f $OUTPUT_FILE

function appendToFile() {
    printf "$1\n" >> $OUTPUT_FILE
}

CODE_BLOCK="\`\`\`"

appendToFile "# MSFT Certificates ${AZURE_TOOL} update"
appendToFile "## IMPORTANT: After setup complete please remove this file"
appendToFile "## TODO 1. Azure Console"
appendToFile "Upload the contents of ``${CERT_FILE}`` to the ${AZURE_TOOL} app in your Azure Console or give it to an admin with rights to do so."
appendToFile "When done, you should delete your local copy of ``${CERT_FILE}``"
appendToFile "\n"

printf "Certificate generated, stored in ${BLUE}${CERT_FILE}${NC}. Upload this to Azure AD console for your App.\n"

appendToFile "## TODO 2. Secret Manager"
appendToFile "Update the value of PSOXY_${AZURE_TOOL}_PRIVATE_KEY_ID in the secret manager of choice with the certificate fingerprint:"
appendToFile ${CODE_BLOCK}
appendToFile "$FINGERPRINT_RESULT"
appendToFile "${CODE_BLOCK}\n"
printf "Certificate thumbprint: ${BLUE}${FINGERPRINT_RESULT}${NC} (this value is the 'PRIVATE_KEY_ID')\n"

appendToFile "## TODO 3. Secret Manager"
appendToFile "Update the value of PSOXY_${AZURE_TOOL}_PRIVATE_KEY in the secret manager of choice with the following certificate:"
appendToFile ${CODE_BLOCK}
cat $KEY_FILE_PKCS8 >> $OUTPUT_FILE
appendToFile "${CODE_BLOCK}\n"

# cleanup generated files
rm $KEY_FILE
rm $KEY_FILE_PKCS8



printf "\nOpen $BLUE${OUTPUT_FILE}$NC and follow the instructions to complete the setup.\n"
