import { 
  request,
  getCommonHTTPHeaders, 
  signAWSRequestURL, 
  executeCommand, 
  resolveHTTPMethod
} from './utils.js';
import {
  S3Client,
  GetObjectCommand,
  PutObjectCommand,
  ListBucketsCommand,
  ListObjectsV2Command
} from '@aws-sdk/client-s3';
import getLogger from './logger.js';

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
  return url.hostname.endsWith('.on.aws');
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
    const credentials = assumeRole(role);

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
 * @param {*} bucket 
 * @param {*} file 
 * @returns 
 */
async function upload(bucket, file, options) {
  const client = createS3Client(options.role, options.region);

  const uploadParams = {
    Bucket: bucket,
    Key: path.basename(file),
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
 * @returns {Promise} resolves with contents of file
 */
async function download(bucket, filename, options) {
  const client = createS3Client(options.role, options.region);

  // Create a helper function to convert a ReadableStream to a string.
  const streamToString = (stream) =>
    new Promise((resolve, reject) => {
      const chunks = [];
      stream.on('data', (chunk) => chunks.push(chunk));
      stream.on('error', reject);
      stream.on('end', () => resolve(Buffer.concat(chunks).toString('utf8')));
    });

  let data;
  let attempts = 0;
  const MAX_ATTEMPTS = 10;
  while (data === undefined && attempts < MAX_ATTEMPTS) {
    try {
      data = await client.send(new GetObjectCommand({
        Bucket: bucket,
        Key: filename,
      }));
    } catch (error) {
      if (error.Code !== 'NoSuchKey') {
        throw(error);
      }
    }
    attempts++;

    // Wait 1' before retry; in theory, only if this is the first operation 
    // after Psoxy deployment, we'd need a max of 60' until it processes the 
    // file and puts it in the output bucket
    clearTimeout(await new Promise(resolve => 
      setTimeout(resolve, 1000)));    
  }

  if (data === undefined) {
    throw new Error(`${filename} not found after ${attempts} attempts`);
  }
  
  return streamToString(data.Body);
}

export default {  
  call,
  download,
  isValidURL,
  listBuckets,
  listObjects,
  upload,
}

