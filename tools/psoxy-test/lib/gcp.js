import {
  executeWithRetry,
  getCommonHTTPHeaders,
  request,
  executeCommand,
  resolveHTTPMethod,
} from './utils.js';
import { Logging } from '@google-cloud/logging';
import { Storage } from '@google-cloud/storage';
import getLogger from './logger.js';
import _ from 'lodash';

/**
 * Helper: check url deploy type
 *
 * @param {String|URL} url
 * @return {boolean}
 */
function isValidURL(url) {
  if (typeof url === 'string') {
    url = new URL(url);
  }
  return url.hostname.endsWith('cloudfunctions.net');
}

/**
 * Get identity token for current gcloud account.
 *
 * Refs:
 * - https://cloud.google.com/sdk/gcloud/reference/auth/login
 * - https://cloud.google.com/sdk/gcloud/reference/auth/print-identity-token
 *
 * @returns {String} identity token
 */
function getIdentityToken() {
  const command = 'gcloud auth print-identity-token';
  return executeCommand(command).trim();
}

/**
 * Psoxy test
 *
 * @param {Object} options - see `../index.js`
 * @returns {Promise}
 */
async function call(options = {}) {
  const logger = getLogger(options.verbose);
  if (!options.token) {
    logger.verbose('Getting Google Cloud identity token');
    options.token = getIdentityToken();
  }

  const headers = {
    ...getCommonHTTPHeaders(options),
    Authorization: `Bearer ${options.token}`,
  };

  logger.info(`Calling Psoxy and waiting response: ${options.url}`);
  logger.verbose('Request Options:', { additional: options });
  logger.verbose('Request Headers:', { additional: headers });

  const url = new URL(options.url);
  const method = options.method || resolveHTTPMethod(url.pathname);

  return await request(url, method, headers);
}

/**
 * Google Cloud Logging: get logs for a cloud function
 * Refs:
 * - https://cloud.google.com/functions/docs/monitoring/logging#using_the_logging_api
 * - resource names and filter format: https://cloud.google.com/logging/docs/reference/v2/rest/v2/entries/list
 * - https://googleapis.dev/nodejs/logging/latest/Entry.html
 * - https://cloud.google.com/logging/docs/structured-logging
 *
 * @param {object} options - see `psoxy-test-logs.js`
 * @param {string} options.projectId
 * @param {string} options.functionName
 * @return {Array<Object>} - array of serialized log entries
 */
async function getLogs(options = {}) {
  const logging = new Logging();
  const log = logging.log('cloudfunctions.googleapis.com%2Fcloud-functions');
  const [entries] = await log.getEntries({
    filter: `resource.labels.function_name=${options.functionName}`,
    resourceNames: [`projects/${options.projectId}`],
    orderBy: 'timestamp asc',
  });
  return entries.map(entry => entry.toStructuredJSON());
}

/**
 * Parse GCP log entries and return a simplre format for our use-case:
 * display: timestamp, message, and severity
 *
 * @param {Array<Object>} entries
 * @returns {Array<Object>}
 */
function parseLogEntries(entries) {
  if (!Array.isArray(entries) || entries.length === 0) {
    return [];
  }

  // https://cloud.google.com/logging/docs/reference/v2/rest/v2/LogEntry#LogSeverity
  const LOG_LEVELS = ['WARNING', 'ERROR', 'CRITICAL', 'ALERT', 'EMERGENCY'];
  const dateRegex = /^\d{4}-\d{2}-\d{2} \d{2}:\d{2}:\d{2}.\d{3}/
  return entries.map(entry => {
    const result = {
      timestamp: new Date(entry.timestamp.seconds * 1000).toISOString(),
    }

    let message = entry.message;
    if (_.isObject(message)) {
      message = message.message;
    }
    result.message = _.isString(message) ?
      message.replace(dateRegex, '') : JSON.stringify(message);

    if (LOG_LEVELS.find(level => entry.severity === level)) {
      result.level = entry.severity;
    }

    return result;
  });
}

/**
 * Get Google Cloud Storage (GCS) client
 *
 * Refs:
 * - GCS docs: https://cloud.google.com/nodejs/docs/reference/storage/latest
 * - GCS API: https://googleapis.dev/nodejs/storage/latest/index.html
 *
 * @returns {Storage}
 */
function createStorageClient() {
  return new Storage();
}

/**
 * Only for testing: List of files' metadata in a bucket
 * Refs:
 * - https://googleapis.dev/nodejs/storage/latest/global.html#GetFileMetadataResponse
 *
 * @param {string} bucketName
 * @returns {Array<Object>}
 */
async function listFilesMetadata(bucketName) {
  if (_.isEmpty(bucketName)) {
    return null;
  }
  const client = createStorageClient();
  const [files] = await client.bucket(bucketName).getFiles();

  return await Promise.all(files.map(async (file) => {
    const [metadata] = await file.getMetadata();
    return metadata;
  }));
}

/**
 * Upload file to GCS;
 * by default the name of the local file will be used
 * https://googleapis.dev/nodejs/storage/latest/Bucket.html#upload
 *
 * @param {string} bucketName - destination GCS bucket
 * @param {string} filePath - local file path
 * @param {Storage} client
 * @param {string} filename - optional, destination file name
 * @returns {UploadResponse} - https://googleapis.dev/nodejs/storage/latest/global.html#UploadResponse
 */
async function upload(bucketName, filePath, client, filename) {
  if (!client) {
    client = createStorageClient();
  }
  return client.bucket(bucketName).upload(filePath, {
    destination: filename ?? path.basename(filePath),
  });
}

/**
 * Download file from GCS
 *
 * @param {string} bucketName
 * @param {string} fileName
 * @param {Storage} client
 * @param {Object} logger - winston instance
 * @returns {DownloadResponse} - https://googleapis.dev/nodejs/storage/latest/global.html#DownloadResponse
 */
async function download(bucketName, fileName, client, logger) {
  if (!client) {
    client = createStorageClient();
  }

  const downloadFunction = async () => client.bucket(bucketName).file(fileName)
    .download();
  const onErrorStop = (error) => error.code !== 404;

  const downloadResponse = await executeWithRetry(downloadFunction, onErrorStop,
    logger);

  if (downloadResponse === undefined) {
    throw new Error(`${fileName} not found after multiple attempts`);
  }

  return downloadResponse;
}

export default {
  call,
  createStorageClient,
  download,
  getIdentityToken,
  getLogs,
  isValidURL,
  parseLogEntries,
  listFilesMetadata,
  upload,
};
