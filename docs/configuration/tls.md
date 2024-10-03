# Configuring Transport Layer Security (TLS)

By default, proxy from version 0.4.61 will connect to data source APIs using TLS 1.3.

Prior to 0.4.61, the proxy should have negotiated to use 1.3 with all sources that supported it;
but may have fallen back to 1.2 for some sources.

It will no longer fall back; but you can configure the proxy to use TLS 1.2 for a given source by
setting the `TLS_VERSION` environment variable on a proxy instance to `TLSv1.2`. As TLS 1.3 offers
security and performance improvements, we recommend using it whenever possible.

As of Sept 2024, we've confirmed that the following public APIs of various data sources support
TLS 1.3, either through end-to-end proxy testing OR via openssl negotiation (see next section):
  - Google Workspace
  - Microsoft 365 (Microsoft Graph)
  - GitHub (cloud version)
  - Asana
  - Atlassian (JIRA, etc)
  - Slack
  - Zoom

## Testing TLS 1.3 Support for a Source API

To test TLS 1.3 support, you can use something like the following command (assuming you have
`openssl` installed on  a Mac):

```shell
openssl s_client -connect api.asana.com:443 -tls1_3
```

