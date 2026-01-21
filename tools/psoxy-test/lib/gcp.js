
import { Logging } from '@google-cloud/logging';
import { CloudSchedulerClient } from '@google-cloud/scheduler';
import { Storage } from '@google-cloud/storage';
import _ from 'lodash';
import getLogger from './logger.js';
import {
    executeCommand,
    executeWithRetry,
    getCommonHTTPHeaders,
    isGzipped,
    request,
    resolveHTTPMethod,
    signJwtWithGCPKMS,
    sleep,
} from './utils.js';


const GCP_CLOUD_FUNCTION_GEN1_DOMAIN='cloudfunctions.net';
const GCP_CLOUD_FUNCTION_GEN2_DOMAIN='run.app';

function isCloudFunctionGen1(url) {
  return url?.hostname.endsWith(GCP_CLOUD_FUNCTION_GEN1_DOMAIN);
}

function isCloudFunctionGen2(url) {
  return url?.hostname.endsWith(GCP_CLOUD_FUNCTION_GEN2_DOMAIN);
}

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
  return isCloudFunctionGen1(url) || isCloudFunctionGen2(url);
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

  let authorizationHeader;
  if (options.signingKey) {
    let signature;
    let claims = {
      iss: options.identityIssuer,
      sub: options.identitySubject,
      aud: options.identityIssuer,
      iat: Math.floor(Date.now() / 1000), // current time in seconds
      exp: Math.floor(Date.now() / 1000) + 60 * 60, // 1 hour
    }
    if (options.signingKey.startsWith('gcp-kms:')) {
      signature = await signJwtWithGCPKMS(claims, options.signingKey.replace('gcp-kms:', ''));
    }

    authorizationHeader = `Bearer ${signature}`;
  } else {
    authorizationHeader = `Bearer ${options.token}`;
  }

  let headers = {
    ...getCommonHTTPHeaders(options),
    Authorization: authorizationHeader,
  };

  logger.info(`Calling Psoxy and waiting response: ${options.url}`);
  logger.verbose('Request Options:', { additional: options });
  logger.verbose('Request Headers:', { additional: headers });

  const url = new URL(options.url);
  const method = options.method || resolveHTTPMethod(url.pathname, options);

  return await request(url, method, headers, options.body);
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
  const logging = new Logging({ projectId: options.projectId });
  
  // Support both Gen 1 (cloud_function) and Gen 2 (cloud_run_revision)
  // Gen 2 logs usually appear under run.googleapis.com/stdout or stderr, but resource.labels.service_name identify the function
  const filter = `
    (resource.type="cloud_function" AND resource.labels.function_name="${options.functionName}")
    OR 
    (resource.type="cloud_run_revision" AND resource.labels.service_name="${options.functionName}")
  `;

  const [entries] = await logging.getEntries({
    filter: filter,
    resourceNames: [`projects/${options.projectId}`],
    orderBy: 'timestamp desc', // Get newest first
    pageSize: 50,
  });
  
  // Return in chronological order
  return entries.reverse().map(entry => entry.toStructuredJSON());
}

/**
 * Example:
 * https://console.cloud.google.com/functions/details/us-central1/my-cloud-function?project=my-projectd&tab=logs
 * Tries to parse region without zone
 * ref: https://cloud.google.com/compute/docs/regions-zones
 *
 * @param {string} cloudFunctionURL
 * @returns {string} - URL to logs
 */
function getLogsURL(cloudFunctionURL = '') {
  try {
    if (!isValidURL(cloudFunctionURL)) {
      return;
    }
  } catch (error) {
    return;
  }

  const url = new URL(cloudFunctionURL);
  let region = '';
  let functionName = '';
  let projectId = '';

  if (isCloudFunctionGen1(url)) {
    const re = /^([a-z]+(?:-[a-z]+)*\d)-([a-z0-9-]+)\.cloudfunctions\.net$/;
    const parts = url.hostname.match(re);
    if (!_.isEmpty(parts)) {
      region = parts[1];
      projectId = parts[2];
      functionName = url.pathname.replace(/^\/+/, '')
    }
  } else if (isCloudFunctionGen2(url)) {  // best guess hack of these ...
    // host is something like 'https://psoxy-dev-gcal-boff2f476q-uc.a.run.app'
    const regionMapping = {
      uc: 'us-central1',
      uw: 'us-west1',
      ue: 'us-east1',
    };
    const re = /^(.+)-([a-z0-9]+)-([a-z]{2})\.a\.run\.app$/;
    const parts = url.hostname.match(re);
    if (!_.isEmpty(parts)) {
      const [, service, hash, shortCode] = parts;
      region = regionMapping[shortCode];
      functionName = service;
    }
  }

  let googleConsoleURL = 'https://console.cloud.google.com';
  if (!_.isEmpty(region) && !_.isEmpty(functionName)) {
    googleConsoleURL += `/run/detail/${region}/${functionName}/logs`;
    // TODO
    //  should pass `projectId` somehow and append to the resulting URL as query param `project`
    //  in gen 2 use-case
    if (!_.isEmpty(projectId)) {
      googleConsoleURL += `?project=${projectId}`;
    }
  }

  return googleConsoleURL;
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

  // https://cloud.google.com/logging/docs/reference/v2/rest/v2/entries/list#LogEntry
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
 * @returns {Promise} - https://googleapis.dev/nodejs/storage/latest/global.html#UploadResponse
 */
async function upload(bucketName, filePath, client, filename) {
  if (!client) {
    client = createStorageClient();
  }

  const uploadOptions = {
    destination: filename ?? path.basename(filePath),
  }

  if (await isGzipped(filePath)) {
    uploadOptions.metadata = { contentEncoding: 'gzip' };
  }

  return client.bucket(bucketName).upload(filePath, uploadOptions);
}

/**
 * Delete file from GCS
 * https://googleapis.dev/nodejs/storage/latest/File.html#delete
 * @param {string} bucketName
 * @param {string} filename - name of the file to delete (includes "path")
 * @param {Storage} client
 * @returns {Promise} - https://googleapis.dev/nodejs/storage/latest/global.html#DeleteFileResponse
 */
async function deleteFile(bucketName, filename, client) {
  if (!client) {
    client = createStorageClient();
  }
  return client.bucket(bucketName).file(filename).delete();
}

/**
 * Download file from GCS and saves it to `destination`
 * Ref: https://googleapis.dev/nodejs/storage/latest/global.html#DownloadResponse
 *
 * @param {string} bucketName
 * @param {string} fileName
 * @param {string} destination - local path and filename
 * @param {Storage} client
 * @param {Object} logger - winston instance
 * @returns {Object} downloadResponse
 * @returns {Object} downloadResponse.content - DownloadResponse (see ref)
 * @returns {Object} downloadResponse.metadata - file metadata
 */
async function download(bucketName, fileName, destination, client, logger) {
  if (!client) {
    client = createStorageClient();
  }

  const downloadFunction = async () => {
    const file = client.bucket(bucketName).file(fileName)

    const [metadata] = await file.getMetadata();
    const content = await file
      .download({ destination: destination, decompress: true});

    return {
      content,
      metadata,
    }
  };
  const onErrorStop = (error) => error.code !== 404;

  const downloadResponse = await executeWithRetry(downloadFunction, onErrorStop, logger);

  if (downloadResponse === undefined) {
    throw new Error(`${fileName} not found after multiple attempts`);
  }

  return downloadResponse;
}

/**
 * Triggers a Cloud Scheduler job to run immediately.
 *
 * @param {string} jobName
 * @param {Object} logger
 */
async function triggerScheduler(jobName, logger) {
  logger.info(`Triggering Cloud Scheduler job: ${jobName}`);
  const client = new CloudSchedulerClient();
  const [response] = await client.runJob({ name: jobName });
  logger.success(`Cloud Scheduler job triggered: ${response.name}`);
}

/**
 * Verifies that a file containing the expected content appears in the bucket after startTime.
 *
 * @param {string} bucketName
 * @param {string} expectedContent
 * @param {number} startTime - timestamp in ms
 * @param {Object} logger
 */
async function verifyBucket(bucketName, expectedContent, startTime, logger) {
  const timeout = 60000; // 60 seconds
  const pollInterval = 5000; // 5 seconds
  const endTime = Date.now() + timeout;

  logger.info(`Verifying content in bucket: ${bucketName}. Will wait up to ${timeout / 1000}s`);

  const client = createStorageClient();
  const bucket = client.bucket(bucketName);
  
  while (Date.now() < endTime) {
    const elapsed = Math.round((Date.now() - startTime) / 1000); // approx
    logger.info(`Waiting for content to appear in bucket... [${Math.max(0, elapsed)}s elapsed]`);
    
    // Check for new files
    const [files] = await bucket.getFiles();
    
    // Sort files by creation time, newest first
    const newFiles = files.filter(f => new Date(f.metadata.timeCreated).getTime() > startTime)
                          .sort((a, b) => new Date(b.metadata.timeCreated).getTime() - new Date(a.metadata.timeCreated).getTime());

    if (newFiles.length > 0) {
        // Stop polling as soon as we find a file (expecting only one)
        const file = newFiles[0];
        logger.info(`New file found: ${file.name} (Created: ${new Date(file.metadata.timeCreated).toISOString()})`);
        
        const [content] = await file.download();
        const contentStr = content.toString();
        logger.info(`Found Content: ${contentStr}`);
        
        // Parse found content
        let items = [];
        try {
            const jsonContent = JSON.parse(contentStr);
            if (Array.isArray(jsonContent)) {
                items = jsonContent;
            } else if (_.isPlainObject(jsonContent)) {
                items = [jsonContent];
            }
        } catch (e) {
            logger.error(`Failed to parse file content: ${e.message}`);
            throw new Error(`Verification failed: Invalid JSON in file ${file.name}`);
        }

        if (items.length > 0) {
             let expectedJson;
             if (typeof expectedContent === 'string') {
                 expectedJson = JSON.parse(expectedContent);
             } else {
                 expectedJson = expectedContent;
             }
             
             logger.info(`Expected Content: ${JSON.stringify(expectedJson)}`);

             const found = items.some(item => {
                 // 1. Try strict equality first
                 if (_.isEqual(item, expectedJson)) return true;
    
                 // 2. Try relaxed equality (ignoring actor.id for pseudonymization)
                 const itemNoId = _.cloneDeep(item);
                 if (itemNoId.actor) delete itemNoId.actor.id;
                 
                 const expectedNoId = _.cloneDeep(expectedJson);
                 if (expectedNoId.actor) delete expectedNoId.actor.id;
                 
                 if (_.isEqual(itemNoId, expectedNoId)) {
                     logger.info('Match found with differing actor.id (likely pseudonymized)');
                     return true;
                 }
                 
                 return false;
             });
    
             if (found) {
                logger.success(`Verification Successful: Content matches.`);
                return;
             } else {
                 logger.error(`Verification Failed: Content does not match.`);
                 throw new Error(`Verification failed: Content mismatch in file ${file.name}`);
             }
        } else {
             logger.warn(`File is empty or contains no items.`);
             throw new Error(`Verification failed: Empty file ${file.name}`);
        }
    }
    
    await sleep(pollInterval);
  }

  logger.error('No new files found in bucket within timeout.');
  throw new Error('Verification failed: Expected content not found in bucket.');
}

/**
 * End-to-end verification of webhook collection in GCP.
 *
 * @param {Object} options
 * @param {string} options.verifyCollection - bucket name
 * @param {string} options.schedulerJob - optional, job name
 * @param {string} options.url - function URL
 * @param {string} options.body - original POST body
 * @param {number} options.startTime - timestamp when test started
 * @param {Object} logger
 */

async function verifyCollection(options, logger) {
  const bucketName = options.verifyCollection;
  
  // 1. Trigger Scheduler
  let jobName = options.schedulerJob;
  
  if (jobName) {
      await triggerScheduler(jobName, logger);
  } else {
      logger.info('Skipping Cloud Scheduler trigger (no job name provided). Waiting for scheduled run...');
  }

  // 2. Verify Bucket
  await verifyBucket(bucketName, options.body, options.startTime, logger);
}


export default {
  call,
  createStorageClient,
  deleteFile,
  download,
  getIdentityToken,
  getLogs,
  getLogsURL,
  isValidURL,
  parseLogEntries,
  listFilesMetadata,
  upload,
  verifyCollection,
};

