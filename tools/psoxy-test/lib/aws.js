import {
  executeWithRetry,
  getAWSCredentials,
  getCommonHTTPHeaders,
  isGzipped,
  request,
  resolveHTTPMethod,
  resolveAWSRegion,
  signAWSRequestURL
} from './utils.js';
import {
  S3Client,
  GetObjectCommand,
  DeleteObjectCommand,
  PutObjectCommand,
  ListBucketsCommand,
  ListObjectsV2Command
} from '@aws-sdk/client-s3';
import {
  CloudWatchLogsClient,
  DescribeLogStreamsCommand,
  GetLogEventsCommand,
} from '@aws-sdk/client-cloudwatch-logs';
import {
   KMSClient,
   SignCommand
} from '@aws-sdk/client-kms';

import fs from 'fs';
import getLogger from './logger.js';
import path from 'path';
import _ from 'lodash';
import zlib from 'node:zlib';
import crypto from 'node:crypto';


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

function base64url(input) {
  return input.toString('base64')
    .replace(/=/g, '')
    .replace(/\+/g, '-')
    .replace(/\//g, '_');
}

async function signJwtWithKMS(claims, keyId, credentials, region) {
  const client = new KMSClient({
    region: region,
    credentials: credentials,
  });

  const encodedHeader = base64url(Buffer.from(JSON.stringify({
    "alg": "RS256",
    "typ": "JWT",
  })));
  const encodedPayload = base64url(Buffer.from(JSON.stringify(claims)));
  const signingInput = `${encodedHeader}.${encodedPayload}`;

  const hash = crypto.createHash('sha256').update(signingInput).digest();

  const command = new SignCommand({
    KeyId: keyId,
    SigningAlgorithm: 'RSASSA_PKCS1_V1_5_SHA_256',
    Message: hash,
    MessageType: 'DIGEST' // 🟢 explicitly indicate pre-hashed input
  });

  const response = await client.send(command);

  const signature = base64url(Buffer.from(response.Signature));
  return `${signingInput}.${signature}`;
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
  const method = options.method || resolveHTTPMethod(url.pathname, options);

  if (_.isEmpty(options.region)) {
    options.region = resolveAWSRegion(url);
  }

  const credentials = await getAWSCredentials(options.role, options.region);

  logger.verbose('Signing request');

  const signed = signAWSRequestURL(url, method, options.body, credentials,
     options.region);
  const headers = {
    ...getCommonHTTPHeaders(options),
    ...signed.headers,
  };

  if (options.signingKey) {

    let signature;
    let claims = {
      iss: options.signingKey, // silly?
      sub: options.identityToSign,
      aud: url.toString(),
      iat: Math.floor(Date.now() / 1000), // current time in seconds
      exp: Math.floor(Date.now() / 1000) + 60 * 60, // 1 hour
    }
    if (options.signingKey.startsWith('aws-kms:')) {
      signature = await signJwtWithKMS(claims, options.signingKey.replace('aws-kms:', ''), credentials, options.region);
    }

    headers['x-psoxy-authorization'] = signature;
    console.log(signature);
  }


  logger.info(`Calling Psoxy and waiting response: ${options.url.toString()}`);
  logger.verbose('Request Options:', { additional: options });
  logger.verbose('Request Headers: ', { additional: headers });

  return await request(url, method, headers, options.body);
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
 * Get CloudWatch logs Home URL
 * (lamdba name, nor log group name are available)
 * @param {object} options
 * @returns {string} URL
 */
function getLogsURL(options) {
  return `https://${options.region}.console.aws.amazon.com/cloudwatch/home`
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
 * Ref: https://docs.aws.amazon.com/AWSJavaScriptSDK/v3/latest/client/s3/command/PutObjectCommand/
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

  const commandOptions = {
    Bucket: bucket,
    Key: key,
    Body: fs.createReadStream(file),
  }

  if (await isGzipped(file)) {
    commandOptions.ContentEncoding = 'gzip';
  }

  if(path.extname(key)?.toLowerCase() === '.csv') {
    commandOptions.ContentType = 'text/csv';
  }

  return await client.send(new PutObjectCommand(commandOptions));
}

/**
 * Only for standard S3 storage (others such as Glacier need to restore object first)
 * Ref: https://docs.aws.amazon.com/AWSJavaScriptSDK/v3/latest/client/s3/command/GetObjectCommand/
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
 * @param {string} destination - local path and filename
 * @param {Object} options
 * @param {string} options.role
 * @param {string} options.region
 * @param {number} options.delay - ms to wait between retries
 * @param {number} options.attempts - max number of download attempts
 * @param {S3Client} client
 * @param {Object} logger - winston instance
 * @returns {Object} downloadResponse
 * @returns {Object} downloadResponse.content - stream
 * @returns {Object} downloadResponse.metadata - object metadata
 */
async function download(bucket, key, destination, options, client, logger) {
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

  // save file locally
  await new Promise((resolve, reject) => {
    let stream = downloadResponse.Body;
    if (downloadResponse.ContentEncoding?.toLowerCase() === 'gzip') {
      stream = stream.pipe(zlib.createGunzip());
    }
    stream
      .pipe(fs.createWriteStream(destination))
      .on('error', err => reject(err))
      .on('close', () => resolve())
  })

  return {
    content: downloadResponse.Body,
    metadata: {
      "ChecksumCRC32": downloadResponse?.ChecksumCRC32,
      "ChecksumCRC32C": downloadResponse?.ChecksumCRC32C,
      "ChecksumSHA1": downloadResponse?.ChecksumSHA1,
      "ChecksumSHA256": downloadResponse?.ChecksumSHA256,
      "ContentEncoding": downloadResponse?.ContentEncoding,
      "ContentLength": downloadResponse?.ContentLength,
      "ContentType": downloadResponse?.ContentType,
      "ETag": downloadResponse?.ETag,
      "LastModified": downloadResponse?.LastModified,
      ...downloadResponse?.Metadata
    },
  };
}

/**
 * Delete object from S3;
 * ref: https://docs.aws.amazon.com/AWSJavaScriptSDK/v3/latest/clients/client-s3/classes/deleteobjectcommand.html
 * @param {string} bucket
 * @param {string} key - Object's key (filename in S3)
 * @param {object} options
 * @param {string} options.role - role to assume
 * @param {string} options.region - region to use
 * @param {S3Client} client - optional
 * @returns {Promise}
 */
async function deleteObject(bucket, key, options, client) {
  if (!client) {
    client = await createS3Client(options.role, options.region);
  }
  return await client.send(new DeleteObjectCommand({
    Bucket: bucket,
    Key: key,
    // BypassGovernanceRetention: true,
  }));
}

export default {
  call,
  createCloudWatchClient,
  createS3Client,
  deleteObject,
  download,
  getLogEvents,
  getLogStreams,
  getLogsURL,
  isValidURL,
  listBuckets,
  listObjects,
  parseLogEvents,
  upload,
}

