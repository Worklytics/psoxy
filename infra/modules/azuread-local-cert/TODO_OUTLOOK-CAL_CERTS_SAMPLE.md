Note: this is an example of output for [refreshing MSFT certificates outside Terraform](README.md#non-terraform-alternative).

# MSFT Certificates outlook-cal update
## IMPORTANT: After setup complete please remove this file
## TODO 1. Azure Console
Upload the following cert to the outlook-cal app in your Azure Console or give it to an admin with rights to do so.
```
-----BEGIN CERTIFICATE-----
MIIDfzCCAmegAwIBAgIUG2UaDCqO8rrUY0uLgexFoRfzrGowDQYJKoZIhvcNAQEL
BQAwTzELMAkGA1UEBhMCVVMxETAPBgNVBAgMCE5ldyBZb3JrMREwDwYDVQQHDAhO
..............
WAQ8YLXDcVMFmdlZLz/Gt7o7/IJIPl6RRF+hvaLMk1PhmA11mE7qwDDSoV7JkKlT
gPddJqivo/OOfI2jXuZblq+7AtWC+V/jMqNJTWbFmYDykcs=
-----END CERTIFICATE-----
```

## TODO 2. Secret Manager
Update the value of PSOXY_OUTLOOK-CAL_PRIVATE_KEY_ID in the secret manager of choice with the certificate fingerprint:
```
9222CABFEB84165BEFA5EF6DAC7AF5C770A6BF51
```

## TODO 3. Secret Manager
Update the value of PSOXY_OUTLOOK-CAL_PRIVATE_KEY in the secret manager of choice with the following certificate:
```
-----BEGIN PRIVATE KEY-----
MIIEvgIBADANBgkqhkiG9w0BAQEFAASCBKgwggSkAgEAAoIBAQC5luiZ7N2OGZRv
KVgFXNRtbYP44ykEVYpcB9j9Qm9geAVxRn09Vn7rymvqOtVk7BzmQVs31rECsXLY
..............
fN/PxVajaRC1MkLdNCs28JyXycr6C4YaPwhY7jQwGMihGeB26LhmrnEO8XAOhDKS
ugkvdNFed3oOM7KX+a73hmDO5LNdj9srPMkv007wJBvCrunUAtsYKaddDXH1dTh1
1aogFrpIwNFMB8+pfkqjW1HQ
-----END PRIVATE KEY-----
```

