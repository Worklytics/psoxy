import test from 'ava';
import { gcsUri } from '../lib/gcs.js';

test('gcsUri builds gs:// URIs', (t) => {
  t.is(gcsUri('my-bucket', 'path/to/file.csv'), 'gs://my-bucket/path/to/file.csv');
  t.is(gcsUri('my-bucket', '/leading/slash.csv'), 'gs://my-bucket/leading/slash.csv');
});
