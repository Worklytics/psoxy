import {
  executeWithRetry,
  getAWSCredentials,
  getCommonHTTPHeaders,
  request,
  resolveHTTPMethod,
  resolveAWSRegion,
  signAWSRequestURL
} from './utils.js';
import {
  S3Client,
  GetObjectCommand,
  PutObjectCommand,
  ListBucketsCommand,
  ListObjectsV2Command
} from '@aws-sdk/client-s3';
import {
  CloudWatchLogsClient,
  DescribeLogStreamsCommand,
  GetLogEventsCommand,
} from '@aws-sdk/client-cloudwatch-logs';
import fs from 'fs';
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
  return url.hostname.endsWith('.on.aws') ||
    url.hostname.endsWith('.amazonaws.com');
}

/**
 * Psoxy test
 *
 * @param {Object} options - see `../index.js`
 * @returns {Promise}
 */
async function call(options = {}) {
  const logger = getLogger(options.verbose);
  const url = new URL(options.url);
  const method = options.method || resolveHTTPMethod(url.pathname);

  if (!_.isEmpty(options.role)) {
    logger.verbose(`Assuming role ${options.role}`);
  }

  if (_.isEmpty(options.region)) {
    options.region = resolveAWSRegion(url);
  }

  const credentials = await getAWSCredentials(options.role, options.region);

  logger.verbose('Signing request');

  const signed = signAWSRequestURL(url, method, credentials, options.region);
  const headers = {
    ...getCommonHTTPHeaders(options),
    ...signed.headers,
  };

  logger.info(`Calling Psoxy and waiting response: ${options.url.toString()}`);
  logger.verbose('Request Options:', { additional: options });
  logger.verbose('Request Headers: ', { additional: headers })

  return await request(url, method, headers);
}

/**
 * Create S3 client with appropriate credentials
 *
 * @param {string} role
 * @param {string} region
 * @returns {S3Client}
 */
async function createS3Client(role, region = 'us-east-1') {
  const credentials = await getAWSCredentials(role, region);
  return new S3Client({
    region: region,
    credentials: credentials,
  });
}

/**
 * Create CloudWatchLogs client with appropriate credentials
 *
 * @param {string} role
 * @param {string} region
 * @returns {CloudWatchLogsClient}
 */
async function createCloudWatchClient(role, region = 'us-east-1') {
  const credentials = await getAWSCredentials(role, region);
  return new CloudWatchLogsClient({
    region: region,
    credentials: credentials,
  });
}

/**
 * Get log streams for `options.logGroupName` (sort by last event time, limit 10)
 *
 * @param {object} options
 * @param {string} options.logGroupName
 * @param {string} options.role
 * @param {string} options.region
 * @param {CloudWatchLogsClient} client
 * @returns
 */
async function getLogStreams(options, client) {
  if (!client) {
    client = createCloudWatchClient(options.role, options.region);
  }

  return await client.send(new DescribeLogStreamsCommand({
    descending: true,
    logGroupName: options.logGroupName,
    orderBy: 'LastEventTime',
    limit: 10,
  }));
}

/**
 * Get log events for `options.logStreamName`
 *
 * @param {object} options
 * @param {string} options.logGroupName
 * @param {string} options.logStreamName
 * @param {string} options.role
 * @param {string} options.region
 * @param {CloudWatchLogsClient} client
 * @returns {Promise}
 */
async function getLogEvents(options, client) {
  if (!client) {
    client = createCloudWatchClient(options.role, options.region);
  }

  return await client.send(new GetLogEventsCommand({
    logGroupName: options.logGroupName,
    logStreamName: options.logStreamName,
  }));
}

/**
 * Parse CloudWatch log events and return a simpler format focused on
 * our use-case: display results via shell
 *
 * Refs:
 * - Command output: https://docs.aws.amazon.com/AWSJavaScriptSDK/v3/latest/clients/client-cloudwatch-logs/interfaces/getlogeventscommandoutput.html
 * - Events format: https://docs.aws.amazon.com/AmazonCloudWatchLogs/latest/APIReference/API_GetLogEvents.html#API_GetLogEvents_ResponseSyntax
 *
 * @param {Array} logEvents
 * @returns {Array<Object>}
 */
function parseLogEvents(logEvents) {
  if (!Array.isArray(logEvents) || logEvents.length === 0) {
    return [];
  }
  const LOG_LEVELS = ['SEVERE', 'WARNING'];
  return logEvents.map(event => {
    const result = {
      timestamp: new Date(event.timestamp).toISOString(),
      message: event.message,
    }

    const level = LOG_LEVELS.find(level => event.message.startsWith(level));
    if (typeof level !== 'undefined') {
      result.message = result.message.replace(`${level}:`, '');
      result.level = level;
      result.highlight = true;
    } else if (event.message.toLowerCase().startsWith('error')) {
      result.highlight = true;
    }

    return result;
  });
}

/**
 * Only for testing: List all available buckets
 * Ref: https://docs.aws.amazon.com/AWSJavaScriptSDK/v3/latest/clients/client-s3/classes/listbucketscommand.html
 * Req: options.role requires "s3:ListAllMyBuckets" permissions
 * @param {object} options
 */
async function listBuckets(options) {
  const client = await createS3Client(options.role, options.region);
  return await client.send(new ListBucketsCommand({}));
}

/**
 * Only for testing: List all objects in a bucket
 * Ref: https://docs.aws.amazon.com/AWSJavaScriptSDK/v3/latest/clients/client-s3/classes/listobjectsv2command.html
 * Req: options.role requires "s3:ListBucket" permissions
 */
async function listObjects(bucket, options) {
  const client = await createS3Client(options.role, options.region);
  return await client.send(new ListObjectsV2Command({
    Bucket: bucket,
  }));
}

/**
 * Upload file to S3
 * Ref: https://docs.aws.amazon.com/AWSJavaScriptSDK/v3/latest/clients/client-s3/classes/putobjectcommand.html
 * Reqs: "s3:PutObject" permissions
 *
 * @param {string} bucket
 * @param {string} key - Object's key (filename in S3)
 * @param {string} file - File to upload
 * @param {object} options
 * @param {string} options.role - role to assume
 * @param {string} options.region - region to use
 * @param {S3Client} client - optional
 * @returns
 */
async function upload(bucket, key, file, options, client) {
  if (!client) {
    client = await createS3Client(options.role, options.region);
  }

  return await client.send(new PutObjectCommand({
    Bucket: bucket,
    Key: key,
    Body: fs.createReadStream(file),
  }));
}

/**
 * Only for standard S3 storage (others such as Glacier need to restore object first)
 * Ref: https://docs.aws.amazon.com/AWSJavaScriptSDK/v3/latest/clients/client-s3/classes/getobjectcommand.html
 * Reqs: "s3:ListBucket" (404 if request object doesn't exit, 403 if no perms)
 *
 * This will retry the download if we get a 404; use-case: Psoxy hasn't
 * processed the file yet...
 *
 * TODO check if "@aws-sdk/middleware-retry" could help with "download" retries
 * https://github.com/aws/aws-sdk-js-v3/issues/3611
 *
 * @param {string} bucket
 * @param {string} key - Object's key (filename in S3)
 * @param {Object} options
 * @param {string} options.role
 * @param {string} options.region
 * @param {number} options.delay - ms to wait between retries
 * @param {number} options.attempts - max number of download attempts
 * @param {S3Client} client
 * @param {Object} logger - winston instance
 * @returns {Promise} resolves with contents of file
 */
async function download(bucket, key, options, client, logger) {
  if (!client) {
    client = await createS3Client(options.role, options.region);
  }

  const downloadFunction = async () => await client.send(new GetObjectCommand({
      Bucket: bucket,
      Key: key,
    }));
  const onErrorStop = (error) => {
    return error.Code !== 'NoSuchKey'
  };

  const downloadResponse = await executeWithRetry(downloadFunction, onErrorStop,
    logger, options.attempts, options.delay);

  if (downloadResponse === undefined) {
    throw new Error(`${key} not found after multiple attempts`);
  }
  return downloadResponse.Body.transformToString();
}

export default {
  call,
  createCloudWatchClient,
  createS3Client,
  download,
  getLogEvents,
  getLogStreams,
  isValidURL,
  listBuckets,
  listObjects,
  parseLogEvents,
  upload,
}

