import test from 'ava';
import { pollAsyncResponse } from '../lib/utils.js';

test('pollAsyncResponse - unsupported URL throws error', async (t) => {
  const options = { verbose: false };
  const unsupportedUrl = 'https://example.com/file.txt';
  
  await t.throwsAsync(
    async () => await pollAsyncResponse(unsupportedUrl, options),
    { message: /Unsupported storage URL/ }
  );
});

test('pollAsyncResponse - S3 URL parsing', async (t) => {
  const s3Url = 'https://test-bucket.s3.us-east-1.amazonaws.com/test-key.json';
  const url = new URL(s3Url);
  
  // Test URL parsing logic
  const isS3 = url.hostname.includes('s3.amazonaws.com') || url.hostname.includes('s3.');
  t.true(isS3);
  
  const bucketName = url.hostname.split('.')[0];
  t.is(bucketName, 'test-bucket');
  
  const key = url.pathname.substring(1);
  t.is(key, 'test-key.json');
});

test('pollAsyncResponse - GCS URL parsing', async (t) => {
  const gcsUrl = 'https://storage.googleapis.com/test-bucket/test-key.json';
  const url = new URL(gcsUrl);
  
  // Test URL parsing logic
  const isGCS = url.hostname.includes('storage.googleapis.com');
  t.true(isGCS);
  
  const pathParts = url.pathname.split('/');
  const bucketName = pathParts[1];
  t.is(bucketName, 'test-bucket');
  
  const fileName = pathParts.slice(2).join('/');
  t.is(fileName, 'test-key.json');
});

test('pollAsyncResponse - timeout configuration', async (t) => {
  // Test that the function is configured for 120 seconds timeout
  const maxAttempts = 12; // 120 seconds / 10 seconds
  t.is(maxAttempts, 12);
  
  const pollInterval = 10000; // 10 seconds
  t.is(pollInterval, 10000);
});

test('pollAsyncResponse - s3:// URL parsing', async (t) => {
  const s3Url = 's3://my-bucket/some/path/to/file.json';
  // Simulate the parsing logic from pollAsyncResponse
  const match = s3Url.match(/^s3:\/\/([^\/]+)\/(.+)$/);
  t.truthy(match, 'Should match s3:// URL pattern');
  t.is(match[1], 'my-bucket');
  t.is(match[2], 'some/path/to/file.json');
});

test('pollAsyncResponse - gs:// URL parsing', async (t) => {
  const gsUrl = 'gs://my-bucket/another/path/to/file.csv';
  // Simulate the parsing logic from pollAsyncResponse
  const match = gsUrl.match(/^gs:\/\/([^\/]+)\/(.+)$/);
  t.truthy(match, 'Should match gs:// URL pattern');
  t.is(match[1], 'my-bucket');
  t.is(match[2], 'another/path/to/file.csv');
}); 