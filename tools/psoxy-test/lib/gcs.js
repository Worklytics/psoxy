import { execFileSync } from 'child_process';

/**
 * GCS operations via `gcloud storage` (Google's recommended CLI).
 *
 * Refs:
 * - https://cloud.google.com/storage/docs/discover-object-storage-gcloud
 * - https://cloud.google.com/storage/docs/gsutil-transition-to-gcloud
 */

/**
 * @param {string} bucketName
 * @param {string} objectName
 * @returns {string}
 */
export function gcsUri(bucketName, objectName) {
  const objectPath = objectName.replace(/^\/+/, '');
  return `gs://${bucketName}/${objectPath}`;
}

/**
 * @param {Error & { stderr?: Buffer, stdout?: Buffer }} error
 * @returns {Error & { code?: number }}
 */
function toGcloudError(error) {
  const stderr = error.stderr?.toString() || '';
  const stdout = error.stdout?.toString() || '';
  const message = (stderr || stdout || error.message).trim();
  const err = new Error(message);
  if (/404|Not Found|does not exist|No URLs matched/i.test(message)) {
    err.code = 404;
  }
  return err;
}

/**
 * @param {string[]} args - gcloud subcommand args (without the `gcloud` binary name)
 * @returns {string}
 */
function runGcloud(args) {
  try {
    return execFileSync('gcloud', args, { encoding: 'utf-8' });
  } catch (error) {
    throw toGcloudError(error);
  }
}

/**
 * @param {string} output
 * @returns {Array<Object>}
 */
function parseJsonArrayOutput(output) {
  const trimmed = output.trim();
  if (!trimmed) {
    return [];
  }
  const parsed = JSON.parse(trimmed);
  return Array.isArray(parsed) ? parsed : [parsed];
}

/**
 * @param {string} bucketName
 * @returns {Array<{ name: string, timeCreated?: string }>}
 */
export function listObjects(bucketName) {
  const output = runGcloud([
    'storage', 'objects', 'list', `gs://${bucketName}`,
    '--format=json(name,timeCreated)',
  ]);
  return parseJsonArrayOutput(output);
}

/**
 * @param {string} bucketName
 * @param {string} objectName
 * @returns {Object}
 */
export function describeObject(bucketName, objectName) {
  const output = runGcloud([
    'storage', 'objects', 'describe', gcsUri(bucketName, objectName),
    '--format=json',
  ]);
  return JSON.parse(output.trim());
}

/**
 * @param {string} bucketName
 * @param {string} localPath
 * @param {string} objectName
 * @param {{ contentType?: string, contentEncoding?: string }} [options]
 * @returns {{ mediaLink: string }}
 */
export function uploadObject(bucketName, localPath, objectName, options = {}) {
  const args = ['storage', 'cp', localPath, gcsUri(bucketName, objectName)];
  if (options.contentType) {
    args.push(`--content-type=${options.contentType}`);
  }
  if (options.contentEncoding) {
    args.push(`--content-encoding=${options.contentEncoding}`);
  }
  runGcloud(args);
  return { mediaLink: gcsUri(bucketName, objectName) };
}

/**
 * @param {string} bucketName
 * @param {string} objectName
 */
export function deleteObject(bucketName, objectName) {
  runGcloud(['storage', 'rm', gcsUri(bucketName, objectName)]);
}

/**
 * Download object to a local path. Gzip-encoded objects are decompressed by default.
 *
 * @param {string} bucketName
 * @param {string} objectName
 * @param {string} destination
 * @returns {{ metadata: Object }}
 */
export function downloadObject(bucketName, objectName, destination) {
  runGcloud(['storage', 'cp', gcsUri(bucketName, objectName), destination]);
  return { metadata: describeObject(bucketName, objectName) };
}

/**
 * @param {string} bucketName
 * @returns {Array<Object>}
 */
export function listObjectsMetadata(bucketName) {
  return listObjects(bucketName).map((object) => describeObject(bucketName, object.name));
}
