#!/bin/bash
# generate keys for MSFT connectors; adapted from Worklytics node script to do this, with output
# formatted to be consumed by Terraform via `data "external" {}`
SUBJECT=$1
TTL=$2

# avoid conflict if building multiple connectors concurrently
RAND_ID=`echo $RANDOM | md5 | head -c 20`
KEY_FILE=key_${RAND_ID}.pem
KEY_FILE_PKCS8=key_pkcs8_${RAND_ID}.pem
CERT_FILE=cert_${RAND_ID}.pem

# build generate key
openssl req -x509 -newkey rsa:2048 -subj "${SUBJECT}" -keyout $KEY_FILE -out $CERT_FILE -days $TTL -nodes >/dev/null 2>&1
openssl pkcs8 -nocrypt -in $KEY_FILE  -inform PEM -topk8 -outform PEM -out $KEY_FILE_PKCS8 >/dev/null 2>&1

# output as JSON
OUTPUT_JSON="{\"cert\": \"`cat $CERT_FILE | base64`\",\"key_pkcs8\": \"`cat $KEY_FILE_PKCS8 | base64`\"}"
echo "$OUTPUT_JSON"

# cleanup
rm $CERT_FILE
rm $KEY_FILE
rm $KEY_FILE_PKCS8
