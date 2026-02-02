import {
    CloudWatchLogsClient,
    DescribeLogStreamsCommand,
    GetLogEventsCommand,
} from '@aws-sdk/client-cloudwatch-logs';
import {
    DeleteObjectCommand,
    GetObjectCommand,
    ListBucketsCommand,
    ListObjectsV2Command,
    PutObjectCommand,
    S3Client
} from '@aws-sdk/client-s3';
import {
    compareContent,
    executeWithRetry,
    getAWSCredentials,
    getCommonHTTPHeaders,
    isGzipped,
    request,
    resolveAWSRegion,
    resolveHTTPMethod,
    signAWSRequestURL,
    signJwtWithAWSKMS,
    sleep,
} from './utils.js';

import fs from 'fs';
import _ from 'lodash';
import zlib from 'node:zlib';
import path from 'path';
import getLogger from './logger.js';


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
      iss: options.identityIssuer,
      sub: options.identitySubject,
      aud: options.identityIssuer,
      iat: Math.floor(Date.now() / 1000), // current time in seconds
      exp: Math.floor(Date.now() / 1000) + 60 * 60, // 1 hour
    }
    if (options.signingKey.startsWith('aws-kms:')) {
      signature = await signJwtWithAWSKMS(claims, options.signingKey.replace('aws-kms:', ''), credentials, options.region);
    }

    headers['Authorization'] = `Bearer ${signature}`;

    // possibly we'll need this for fallback, if target service has auth layer that consumes 'Authorization' header and doesn't pass it on
    // but API Gateway v2 appears to be passing it as well as verifying it, so think we're OK
    //headers['x-psoxy-authorization'] = signature;
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
/**
 * Verifies that a file containing the expected content appears in the bucket after startTime.
 *
 * @param {Object} options
 * @param {string} options.verifyCollection - bucket name
 * @param {string} options.body - expected content
 * @param {number} options.startTime - timestamp in ms
 * @param {string} options.role
 * @param {string} options.region
 * @param {Object} logger
 */
async function verifyCollection(options, logger) {
    const bucketName = options.verifyCollection;
    const expectedContent = options.body;
    const startTime = options.startTime;
    const timeout = 90000; // 90 seconds
    const pollInterval = 5000; // 5 seconds
    const endTime = Date.now() + timeout;

    logger.info(`Verifying content in bucket: ${bucketName}. Will wait up to ${timeout / 1000}s`);

    const client = await createS3Client(options.role, options.region);

    while (Date.now() < endTime) {
        const elapsed = Math.round((Date.now() - startTime) / 1000);
        logger.info(`Waiting for content to appear in bucket... [${Math.max(0, elapsed)}s elapsed]`);

        // List objects
        // We might want to list only recent objects or just list all and filter.
        // AWS S3 ListObjectsV2 returns up to 1000 keys.
        const command = new ListObjectsV2Command({
            Bucket: bucketName
        });
        const response = await client.send(command);
        
        const files = response.Contents || [];
        
        // Filter by LastModified > startTime
        const newFiles = files.filter(f => f.LastModified && new Date(f.LastModified).getTime() > startTime)
                              .sort((a, b) => new Date(b.LastModified).getTime() - new Date(a.LastModified).getTime());

        if (newFiles.length > 0) {
             const file = newFiles[0];
             logger.info(`New file found: ${file.Key} (Created: ${new Date(file.LastModified).toISOString()})`);
             
             // Download content
             const getObjCmd = new GetObjectCommand({
                 Bucket: bucketName,
                 Key: file.Key
             });
             const getResponse = await client.send(getObjCmd);
             
             let contentStr = '';
             if (getResponse.Body) {
                  const chunks = [];
                  for await (const chunk of getResponse.Body) {
                      chunks.push(chunk);
                  }
                  const buffer = Buffer.concat(chunks);
                  
                  // Check for gzip
                  const isGzippedContent = (await isGzipped(buffer)) || getResponse.ContentEncoding === 'gzip';
                   if (isGzippedContent) {
                       contentStr = (await new Promise((resolve, reject) => {
                           zlib.gunzip(buffer, (err, res) => {
                               if (err) reject(err);
                               else resolve(res);
                           });
                       })).toString();
                   } else {
                       contentStr = buffer.toString();
                   }
             }

             logger.info(`Found Content: ${contentStr}`);

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
                 throw new Error(`Verification failed: Invalid JSON in file ${file.Key}`);
             }

            if (items.length > 0) {
                const matchFound = compareContent(items, expectedContent, logger);
                if (matchFound) {
                  logger.success(`Verification Successful: Content matches.`);
                  return;
                } else {
                  logger.error(`Verification Failed: Content does not match.`);
                  throw new Error(`Verification failed: Content mismatch in file ${file.Key}`);
                }
            } else {
                logger.info(`File is empty or contains no items.`);
                throw new Error(`Verification failed: Empty file ${file.Key}`);
            }
        }

        await sleep(pollInterval);
    }
    
    logger.error('No new files found in bucket within timeout.');
    throw new Error('Verification failed: Expected content not found in bucket.');
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
  verifyCollection,
}
