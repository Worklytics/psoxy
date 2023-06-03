# gcp-host


Module for provisioning Psoxy connectors in GCP, independent of:
   - how connection to source is authenticated/authorized
   - intended user of connection (eg, Worklytics use-case)

Eg, auth info should be encoded within the variables passed in here; anything needed to support that
is outside the scope of this module. This module will just make that information available to the
connector.



