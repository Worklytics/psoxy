# Side Outputs

*alpha* - this may change in backwards-incompatible ways in the future. it has not be widely used in production scenarios yet.

The **Side Outputs** feature allows you to configure additional outputs from your connector, to fulfill a couple of use cases:
  - desired content is too large/long to be processed in a single synchronsous request from client --> proxy --> source and back
  - target API has rate limits; and you have another use for the desired response content
  - having extra copy of all data coming through the proxy, pre- or post-processing

## Configuration

Use the following configuration properties to configure the side outputs from a proxy instance in API connector mode. These should
be set as environment variables in the proxy instance:
  - `SIDE_OUTPUT` - defines the target of the side output. Eg `s3://bucket-name/`, `gs://bucket-name/`, etc;
      - in the future, we might support `bq://dataset.table`, etc.
  - `SIDE_OUTPUT_CONTENT` - defines the format of the side output.
      - `ORIGINAL` - the original content from the source API
      - `SANITIZED` - the content after it has been processed by the proxy

Only a single side output is supported, at least for now.

## Future Work
  - support for multiple side outputs
  - support for other targets (BQ, https endpoint, cloud watch, etc)
