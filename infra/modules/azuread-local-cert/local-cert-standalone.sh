#!/bin/bash
# generate keys for MSFT connectors; adapted from Worklytics node script to do this, with output
# formatted to be consumed by Terraform via `data "external" {}`
# example usage: ./local-cert.sh "/C=US/ST=New York/L=New York/CN=www.worklytics.co" 30
SUBJECT=$1
TTL=$2
AZURE_TOOL=$3

if [[ $OSTYPE == 'darwin'* ]]; then
  alias md5sum='md5 -r'
fi

# avoid conflict if building multiple connectors concurrently
RAND_ID=`echo $RANDOM | md5sum | head -c 20`
KEY_FILE=$3_key_${RAND_ID}.pem
KEY_FILE_PKCS8=$3_key_pkcs8_${RAND_ID}.pem
CERT_FILE=$3_cert_${RAND_ID}.pem

# build generate key
openssl req -x509 -newkey rsa:2048 -subj "${SUBJECT}" -keyout $KEY_FILE -out $CERT_FILE -days $TTL -nodes >/dev/null 2>&1
openssl pkcs8 -nocrypt -in $KEY_FILE  -inform PEM -topk8 -outform PEM -out $KEY_FILE_PKCS8 >/dev/null 2>&1

#CERT_DER=`openssl x509 -in $CERT_FILE -outform DER | base64`
FINGERPRINT_RESULT_RAW=`openssl x509 -in $CERT_FILE -noout -fingerprint -sha1`
FINGERPRINT_RESULT=`echo $FINGERPRINT_RESULT_RAW | sed 's/://g' | sed 's/SHA1 Fingerprint=//g'`
# output as JSON
OUTPUT_JSON="{\"cert\": \"`cat $CERT_FILE | base64`\",\"key_pkcs8\": \"`cat $KEY_FILE_PKCS8 | base64`\",\"fingerprint\":\"${FINGERPRINT_RESULT}\"}"
# echo "$OUTPUT_JSON"

BLOCK="========================================================================"

OUTPUT_FILE="TODO_${AZURE_TOOL^^}_CERTS.txt"
printf "" > $OUTPUT_FILE

function appendToFile() {
    printf "$1" >> $OUTPUT_FILE
}

appendToFile "Follow instructions and copy the contents between the === blocks\n"
appendToFile "IMPORTANT: After setup complete please remove this file.\n\n"
appendToFile "\nTODO 1: Upload the following cert to the ${AZURE_TOOL} app in your Azure Console\n"
appendToFile "Or give it to an admin with rights to do so.\n"
appendToFile "${BLOCK}\n"
cat $CERT_FILE >> $OUTPUT_FILE
appendToFile "${BLOCK}\n\n"

appendToFile "TODO 2: Update the value of PSOXY_${AZURE_TOOL^^}_PRIVATE_KEY_ID with the certificate fingerprint:\n"
appendToFile "${BLOCK}\n"
appendToFile "$FINGERPRINT_RESULT\n"
appendToFile "${BLOCK}\n\n"

appendToFile "TODO 3: Update the value of PSOXY_${AZURE_TOOL^^}_PRIVATE_KEY with the following certificate:\n"
appendToFile "${BLOCK}\n"
cat $KEY_FILE_PKCS8 >> $OUTPUT_FILE
appendToFile "${BLOCK}\n\n"


# cleanup
rm *.pem

echo "Open $OUTPUT_FILE and follow the instructions to complete the setup."
