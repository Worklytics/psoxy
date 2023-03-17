import {
  request,
  getCommonHTTPHeaders,
  signAWSRequestURL,
  executeCommand,
  executeWithRetry,
  resolveHTTPMethod
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
import path from 'path';

/**
 * Call AWS cli to get temporary security credentials.
 *
 * Refs:
 * - https://awscli.amazonaws.com/v2/documentation/api/latest/reference/sts/assume-role.html
 * - https://docs.aws.amazon.com/IAM/latest/UserGuide/reference_identifiers.html#identifiers-arns
 *
 * @param {String} role AWS IAM ARN format
 * @returns {Object} AWS credentials for role
 */
function assumeRole(role) {
  // one-liner for simplicity
  const command = `aws sts assume-role --role-arn ${role} --duration 900 --role-session-name lambda_test`;
  return JSON.parse(executeCommand(command)).Credentials;
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

  if (!options.role) {
    throw new Error('Role is a required option for AWS');
  }

  logger.verbose(`Assuming role ${options.role}`);
  let credentials;
  try {
    credentials = assumeRole(options.role);
  } catch (error) {
    throw new Error(`Unable to assume ${options.role}`, { cause: error });
  }

  const url = new URL(options.url);
  const method = options.method || resolveHTTPMethod(url.pathname);

  logger.verbose('Signing request');
  const signed = signAWSRequestURL(url, method, credentials);
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
function createS3Client(role, region = 'us-east-1') {
  const options = {
    region: region,
  }
  if (role) {
    let credentials;
    try {
      credentials = assumeRole(role);
    } catch (error) {
      throw new Error(`Unable to assume ${role}`, { cause: error });
    }

    options.credentials = {
      // AWS CLI command will return credentials with 1st letter upper-case.
      // However, S3 client expects different capitalization
      ...Object.keys(credentials).reduce((memo, key) => {
        memo[key.charAt(0).toLowerCase() + key.slice(1)] = credentials[key];
        return memo;
      }, {}),
    }
  }

  return new S3Client(options);
}

/**
 * Create CloudWatchLogs client with appropriate credentials
 *
 * @param {string} role
 * @param {string} region
 * @returns {CloudWatchLogsClient}
 */
function createCloudWatchClient(role, region = 'us-east-1') {
  const options = {
    region: region,
  }
  if (role) {
    let credentials;
    try {
      credentials = assumeRole(role);
    } catch (error) {
      throw new Error(`Unable to assume ${role}`, { cause: error });
    }

    options.credentials = {
      // AWS CLI command will return credentials with 1st letter upper-case.
      // However, S3 client expects different capitalization
      ...Object.keys(credentials).reduce((memo, key) => {
        memo[key.charAt(0).toLowerCase() + key.slice(1)] = credentials[key];
        return memo;
      }, {}),
    }
  }

 return new CloudWatchLogsClient(options);
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
  const client = createS3Client(options.role, options.region);
  return await client.send(new ListBucketsCommand({}));
}

/**
 * Only for testing: List all objects in a bucket
 * Ref: https://docs.aws.amazon.com/AWSJavaScriptSDK/v3/latest/clients/client-s3/classes/listobjectsv2command.html
 * Req: options.role requires "s3:ListBucket" permissions
 */
async function listObjects(bucket, options) {
  const client = createS3Client(options.role, options.region);
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
 * @param {string} file - path to file
 * @param {object} options
 * @param {string} options.filename - optional filename to use for S3 object
 * @param {string} options.role - role to assume
 * @param {string} options.region - region to use
 * @param {S3Client} client
 * @returns
 */
async function upload(bucket, file, options, client) {
  if (!client) {
    client = createS3Client(options.role, options.region);
  }

  const uploadParams = {
    Bucket: bucket,
    Key: options.filename ?? path.basename(file),
    Body: fs.createReadStream(file),
  }

  return await client.send(new PutObjectCommand(uploadParams));
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
 * @param {string} filename
 * @param {Object} options
 * @param {string} options.role
 * @param {string} options.region
 * @param {number} options.delay - ms to wait between retries
 * @param {number} options.attempts - max number of download attempts
 * @param {S3Client} client
 * @param {Object} logger - winston instance
 * @returns {Promise} resolves with contents of file
 */
async function download(bucket, filename, options, client, logger) {
  if (!client) {
    client = createS3Client(options.role, options.region);
  }

  const downloadFunction = async () => await client.send(new GetObjectCommand({
      Bucket: bucket,
      Key: filename,
    }));
  const onErrorStop = (error) => {
    return error.Code !== 'NoSuchKey'
  };

  const downloadResponse = await executeWithRetry(downloadFunction, onErrorStop,
    logger, options.attempts, options.delay);

  if (downloadResponse === undefined) {
    throw new Error(`${filename} not found after multiple attempts`);
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

