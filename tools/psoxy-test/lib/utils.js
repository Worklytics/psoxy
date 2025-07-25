import { KMSClient, SignCommand } from '@aws-sdk/client-kms';
import { GetObjectCommand, S3Client } from '@aws-sdk/client-s3';
import {
  fromNodeProviderChain,
  fromTemporaryCredentials
} from "@aws-sdk/credential-providers";
import { KeyManagementServiceClient } from '@google-cloud/kms';
import { Storage } from '@google-cloud/storage';
import isgzipBuffer from '@stdlib/assert-is-gzip-buffer';
import aws4 from 'aws4';
import chalk from 'chalk';
import { execSync } from 'child_process';
import { createReadStream, createWriteStream } from 'fs';
import https from 'https';
import _ from 'lodash';
import crypto from 'node:crypto';
import fs from 'node:fs/promises';
import { pipeline } from 'node:stream/promises';
import zlib from 'node:zlib';
import path from 'path';
import { fileURLToPath } from 'url';
import spec from '../data-sources/spec.js';
import getLogger from './logger.js';

const __dirname = path.dirname(fileURLToPath(import.meta.url));
// In case Psoxy is slow to respond (Lambda can take up to 20s+ to bootstrap),
// Node.js request doesn't have a default since v13
const REQUEST_TIMEOUT_MS = 25000;
const DEFAULT_DURATION_TEMP_CREDENTIALS = 900; // 15 minutes
const DEFAULT_ROLE_SESSION_NAME = 'lambda_test';

/**
 * Save data to file.
 *
 * @param {string} dirname
 * @param {string} filename
 * @param {string} data
 */
async function saveToFile(dirname = __dirname, filename, data) {
  let dirExists = false;

  try {
    await fs.access(dirname);
    dirExists = true;
  } catch {}

  if (!dirExists) {
    await fs.mkdir(dirname);
  }

  return fs.writeFile(`${dirname}/${filename}`, data, 'utf-8');
}

/**
 * Get a file name from a URL object; default: replaces `/` by `-`, example:
 * `[psoxy-function]-[api-path]-[ISO8601 timestamp].json`
 * `psoxy-gcal-calendar-v3-calendars-primary-2022-09-02T10:15:00.000Z.json`
 * @param {URL} url
 * @param {boolean} timestamp
 * @param {string} extension
 * @return {string}
 */
function getFileNameFromURL(url, timestamp = true, extension = '.json') {
  // Strip out 1st '/'
  const pathPart = url.pathname.substring(1).replaceAll('/', '-');
  const timestampPart = timestamp ? `-${new Date().toISOString()}` : '';
  return pathPart + timestampPart + extension;
}

/**
 * Get common HTTP request headers for Psoxy requests.
 *
 * @param {Object} options - see `../index.js`
 */
function getCommonHTTPHeaders(options = {}) {
  const headers = {
    'Accept-Encoding': options.gzip ? 'gzip,deflate' : '*',
    'X-Psoxy-Skip-Sanitizer': options.skip?.toString() || 'false',
    'User-Agent': 'psoxy-test (gzip)', // w/o (gzip) here, GCP Cloud functions don't compress response
  };
  if (options.impersonate) {
    headers['X-Psoxy-User-To-Impersonate'] = options.impersonate;
  }
  if (options.healthCheck) {
    // option presence is enough, since Psoxy doesn't check header value
    headers['X-Psoxy-Health-Check'] = 'true';
  }
  if (options.requestNoResponse) {
    headers['X-Psoxy-No-Response-Body'] = 'true';
  }
  if (options.async) {
    headers['Prefer'] = 'respond-async';
  }
  if (options.body) {
    headers['Content-Type'] = 'application/json';
  }

  return headers;
}

/**
 * Wrapper for requests using Node.js HTTP interfaces: focused on
 * Psoxy use-case (*)
 *
 * @param {String|URL} url
 * @param {Object} headers
 * @param {String} method
 * @param {Object} body
 * @return {Promise}
 */
function requestWrapper(url, method = 'GET', headers, body = {}) {
  url = typeof url === 'string' ? new URL(url) : url;
  const params = url.searchParams.toString();
  const responseBody = [];
  const requestOptions = {
    hostname: url.host,
    port: 443,
    path: url.pathname + (params !== '' ? `?${params}` : ''),
    method: method,
    headers: headers,
    timeout: REQUEST_TIMEOUT_MS,
  }

  return new Promise((resolve, reject) => {
    const req = https.request(requestOptions,
      (res) => {
        res.on('data', (data) => {
          responseBody.push(data);
        });
        res.on('end', () => {
          const contentEncoding = res.headers['content-encoding'];
          if (['gzip', 'deflate'].includes(contentEncoding)) {
            const data = Buffer.concat(responseBody);
            const callback = (error, decompressed) => {
              if (error) {
                reject({ statusMessage: 'Unable to decompress Psoxy response' });
              } else {
                resolve({
                  status: res.statusCode,
                  statusMessage: res.statusMessage,
                  headers: res.headers,
                  data: decompressed.toString(),
                });
              }
            }
            if (contentEncoding === 'gzip') {
              zlib.gunzip(data, callback);
            } else {
              zlib.inflate(data, callback);
            }
          } else {
            resolve({
              status: res.statusCode,
              statusMessage: res.statusMessage,
              headers: res.headers,
              data: responseBody.join(''),
            });
          }
        });
      }
    );
    if (!_.isEmpty(body)) {
      req.write(JSON.stringify(body));
    }

    req.on('timeout', () => {
      req.destroy();
      reject({ statusMessage: 'Psoxy is taking too long to respond' });
    });
    req.on('error', (error) => {
      reject({ status: error.code, statusMessage: error.message });
    });
    req.end();
  });
}

/**
 * Simple wrapper around `aws4` to ease testing.
 *
 * TODO aws4 is not able to resolve region nor service (see how we try to
 *  resolve the "service" here) from the URL for our use cases, so we need to
 *  improve the resolution of those values
 *
 * Ref: https://github.com/mhart/aws4#api
 *
 * @param {URL} url
 * @param {String} method
 * @param {Object} body
 * @param {Credentials} credentials
 * @param {String} region
 * @return {Object}
 */
function signAWSRequestURL(url, method = 'GET', body = {}, credentials, region) {
  // According to aws4 docs, search params should be part of the "path"
  const params = url.searchParams.toString();

  const requestOptions = {
    host: url.host,
    path: url.pathname + (params !== '' ? `?${params}` : ''),
    method: method,
    region: region,
  };

  if (method === 'POST' && !_.isEmpty(body)) {
    requestOptions.body = JSON.stringify(body);
    // `aws4` will infer the rest
    requestOptions.headers = {
      'content-type': 'application/json',
    }
  }

  // Closer look at aws4 source code: region and service are calculated from
  // URL's host, but for Lambda functions it doesn't translate the URL part
  // to the name of the service; API Gateway use-case works OK
  const serviceHostPart = url.host.split('.')[1];
  if (serviceHostPart === 'lambda-url') {
    requestOptions.service = 'lambda';
  } else {
    requestOptions.service = serviceHostPart;
  }

  return aws4.sign(requestOptions, credentials);
}

/**
 * Check environment: as of 2024-08 deprecation warning messages are expected for Node.js v21 due
 * to @google-cloud/storage dependency.
 * Ref: https://github.com/googleapis/nodejs-storage/issues/1907#issuecomment-1817620435
 *
 * @param logger
 */
function environmentCheck(logger = getLogger()) {
  const [major] = process.versions.node.split('.').map(Number);

  if (major === 21) {
    const deprecationWarningMessage = `
      Your Node.js version may display ${chalk.yellow("deprecation warnings")} related to an
      official Google dependency used by this tool. These warnings are not
      the direct responsibility of this tool and ${chalk.green('do not compromise its functionality')}.
      `;
    logger.info(deprecationWarningMessage);
  }
}


/**
 * Simple wrapper around node's `execSync` to ease testing.
 *
 * @param {string} command
 * @return {string}
 */
function executeCommand(command) {
  return execSync(command).toString();
}

/**
 * Transform endpoint's path and params based on previous calls responses
 *
 * @param {string} endpointName - name of the endpoint
 * @param {object} sourceData - endpoint's data source API response
 * @param {object} spec - see `../data-sources/spec.js` (filtered by source)
 * @returns
 */
function transformSpecWithResponse(endpointName = '', sourceData = {}, spec = {}) {

  const refs = (spec?.endpoints ?? [])
    .find(endpoint => endpoint.name === endpointName)?.refs ?? [];

  refs.forEach((ref) => {
    const target = spec.endpoints.find((endpoint) => endpoint.name === ref.name);

    if (target && ref.accessor) {
      const valueReplacement = _.get(sourceData, ref.accessor);

      if (valueReplacement) {
        // 2 possible replacements: path or param
        if (ref.pathReplacement) {
          target.path = target.path.replace(ref.pathReplacement, valueReplacement);
        }

        if (ref.paramReplacement) {
          target.params[ref.paramReplacement] = valueReplacement;
        }
      }
    }
  })

  return spec;
}

/**
 * Resolve HTTP method based on known API paths (defined in spec module)
 *
 * @param {string} path - path to inspect
 * @param {object} options - see `../index.js`
 * @returns {string}
 */
function resolveHTTPMethod(path = '', options = {}) {
  let method = 'GET';
  if (!_.isEmpty(options.body)) {
    method = 'POST';
  } else {
    const endpointMatch = Object.values(spec)
    .reduce((acc, value) => acc.concat(value.endpoints), [])
    .find(endpoint => endpoint.path === path);
    method = endpointMatch?.method || method;
  }

  return method;
}

/**
 * Resolve region from URL (3rd part of hostname, defaults to `us-east-1`)
 * @param {URL} url
 * @returns {string}
 */
function resolveAWSRegion(url) {
  let region = 'us-east-1';
  // for regional endpoints
  // {id}.{service}.{region}.(amazonaws.com|on.aws)
  const hostParts = url.hostname?.split('.');
  if (!_.isEmpty(hostParts) && hostParts.length > 2) {
    region = hostParts[2];
  }
  return region;
}

/**
 * "retry" helper function, execute "fn" until returning value is not undefined
 *
 * @param {Function} fn
 * @param {Function} onErrorStop
 * @param {Object} logger - winston instance
 * @param {number} maxAttempts
 * @param {number} delay
 * @param {string} progressMessage
 * @returns
 */
async function executeWithRetry(fn, onErrorStop, logger, maxAttempts = 60,
  delay = 5000, progressMessage = 'Waiting for sanitized output...') {

  const start = Date.now();
  let result;
  let attempts = 0;
  while(_.isUndefined(result) && attempts <= maxAttempts) {
    try {
      result = await fn();
    } catch (error) {
      if (onErrorStop(error)) {
        throw error;
      }
    }
    attempts++;
    if (_.isUndefined(result)) {
      // Wait "delay" ms before retry;
      // Psoxy bulk use-case: in theory, only if this is the first
      // operation  after Psoxy deployment, we'd need a max of 60' until it
      // processes the file and puts it in the output bucket
      clearTimeout(await new Promise(resolve => setTimeout(resolve, delay)));

      if (logger) {
        const elapsedMs = Date.now() - start;
        const elapsedSeconds = Math.floor(elapsedMs / 1000);
        const elapsedMinutes = Math.floor(elapsedSeconds / 60);
        const elapsed = elapsedMinutes > 0 ?
          `${elapsedMinutes}m${elapsedSeconds % 60}s` :
          `${elapsedSeconds}s`;
        logger.info(`${progressMessage} [${elapsed} elapsed]`);
      }
    }
  }

  return result;
}

/**
 * For psoxy-test-file-upload;
 * bucket option should support `/` being a delimiter to split a bucket name
 * from a path within the bucket
 *
 * @param {string} bucketOption - bucket name or bucket name + path
 * @returns {Object} - { bucket: string, path: string }
 */
function parseBucketOption(bucketOption) {
  let bucket = bucketOption;
  let path = '';

  if (!_.isEmpty(bucketOption) && bucketOption.includes('/')) {
    bucket = bucketOption.substring(0, bucketOption.indexOf('/'));
    path = bucketOption.substring(bucketOption.indexOf('/') + 1);
  }

  return {
    bucket:  bucket,
    path: path,
  };
}

/**
 * @typedef {Object} Credentials
 * @property {string} accessKeyId
 * @property {string} secretAccessKey
 * @property {string} sessionToken
 */

/**
 * Get AWS credentials for AWS SDK clients and Psoxy call signing
 *
 * Refs:
 * - https://awscli.amazonaws.com/v2/documentation/api/latest/reference/sts/assume-role.html
 * - https://docs.aws.amazon.com/IAM/latest/UserGuide/reference_identifiers.html#identifiers-arns
 * - https://www.npmjs.com/package/@aws-sdk/credential-providers
 *
 * @param {String} role AWS IAM ARN format
 * @param {String} region
 * @returns {Credentials}
 */
async function getAWSCredentials(role, region) {
  const logger = getLogger();
  let credentials;
  let credentialsProvider;
  let callerIdentity;

  if (!_.isEmpty(role)) {
    logger.verbose(`Assuming role ${role}`);
    const temporaryCredentialsOptions = {
      params: {
        RoleArn: role,
        RoleSessionName: DEFAULT_ROLE_SESSION_NAME,
        DurationSeconds: DEFAULT_DURATION_TEMP_CREDENTIALS,
      }
    };
    if (!_.isEmpty(region)) {
      temporaryCredentialsOptions.clientConfig = { region: region };
    }
    credentialsProvider = fromTemporaryCredentials(temporaryCredentialsOptions);

    try {
      credentials = await credentialsProvider();
    } catch (e) {
      throw new Error(`Unable to get AWS credentials: ${e.message}\nMake sure your AWS CLI is configured correctly: https://docs.aws.amazon.com/cli/latest/userguide/cli-chap-authentication.html`);
    }

    try {
      callerIdentity = JSON.parse(
        executeCommand('aws sts get-caller-identity').trim());
    } catch (e) {
      // It shouldn't happen if credentials are valid
      logger.verbose(`Unable to get caller identity: ${e.message}`);
    }

    logger.info(`Using temporary credentials: ${callerIdentity.Arn},
      access key ID -> ${credentials.accessKeyId}`);
  } else {
    // Look up credentials; expected sources depending on use case:
    // - Environment variables
    // - Shared credentials file `.aws/credentials`
    // - EC2 Instance Metadata Service
    credentialsProvider = fromNodeProviderChain();
    credentials = await credentialsProvider();
    logger.info(`Credentials found, access key ID: ${credentials.accessKeyId}`);
  }
  return credentials;
}

function base64url(input) {
  return input.toString('base64')
    .replace(/=/g, '')
    .replace(/\+/g, '-')
    .replace(/\//g, '_');
}

/**
 * @param {object} claims - the usual JWT ones, iss, sub, etc. will be stringified
 * @param {string} keyArn
 * @param {Credentials} credentials
 * @param {string} region
 * @returns {Promise<string>}
 */
async function signJwtWithAWSKMS(claims, keyArn, credentials, region) {
  const client = new KMSClient({
    region: region,
    credentials: credentials,
  });

  // Extract keyId from keyArn, e.g. `arn:aws:kms:us-east-1:123456789012:key/abcd1234-56ef-78gh-90ij-klmnopqrstuv`
  //let keyId = keyArn.split(":")[5].split("/")[1];

  const encodedHeader = base64url(Buffer.from(JSON.stringify({
    "alg": "RS256",
    "kid": keyArn,
    "typ": "JWT",
  })));
  const encodedPayload = base64url(Buffer.from(JSON.stringify(claims)));
  const signingInput = `${encodedHeader}.${encodedPayload}`;

  const hash = crypto.createHash('sha256').update(signingInput).digest();

  const command = new SignCommand({
    KeyId: keyArn,
    SigningAlgorithm: 'RSASSA_PKCS1_V1_5_SHA_256',
    Message: hash,
    MessageType: 'DIGEST' // 🟢 explicitly indicate pre-hashed input
  });

  const response = await client.send(command);

  const signature = base64url(Buffer.from(response.Signature));
  return `${signingInput}.${signature}`;
}

/**
 * @param {object} claims - the usual JWT ones, iss, sub, etc. will be stringified
 * @param {string} keyId - GCP KMS key ID (e.g., "projects/my-project/locations/us-central1/keyRings/my-keyring/cryptoKeys/my-key")
 * @returns {Promise<string>}
 */
async function signJwtWithGCPKMS(claims, keyId) {
  const client = new KeyManagementServiceClient();

  const encodedHeader = base64url(Buffer.from(JSON.stringify({
    "alg": "RS256",
    "kid": keyId,
    "typ": "JWT",
  })));
  const encodedPayload = base64url(Buffer.from(JSON.stringify(claims)));
  const signingInput = `${encodedHeader}.${encodedPayload}`;

  const hash = crypto.createHash('sha256').update(signingInput).digest();

  // Get the latest enabled version of the key
  const [versions] = await client.listCryptoKeyVersions({
    parent: keyId,
    filter: 'state = ENABLED'
  });

  if (!versions || versions.length === 0) {
    throw new Error(`No enabled versions found for key: ${keyId}`);
  }

  // Sort by creation time to get the latest version
  const sortedVersions = versions.sort((a, b) => {
    const timeA = new Date(a.createTime);
    const timeB = new Date(b.createTime);
    return timeB - timeA; // Descending order (latest first)
  });

  const latestVersion = sortedVersions[0];
  const versionName = `${keyId}/cryptoKeyVersions/${latestVersion.name.split('/').pop()}`;

  const [result] = await client.asymmetricSign({
    name: versionName,
    digest: {
      sha256: hash
    }
  });

  const signature = base64url(Buffer.from(result.signature));
  return `${signingInput}.${signature}`;
}

/**
 * Append suffix to filename (before extension)
 * @param {string} filename
 * @param {string} suffix
 * @returns {string} {filename}-{suffix}{extension}
 */
function addFilenameSuffix(filename, suffix) {
  let result = '';
  if (!_.isEmpty(filename)) {
    const { name, ext } = path.parse(filename);
    result = `${name}-${suffix}${ext}`;
  }
  return result;
}

/**
 * Unzip file
 * @param {string} filePath
 * @returns {string} unzipped file path
 */
async function unzip(filePath) {
  const unzip = zlib.createUnzip();
  const input = createReadStream(filePath);
  const outputPath = `${filePath}.${Date.now()}.raw`
  const output = createWriteStream(outputPath);

  await pipeline(input, unzip, output);
  return outputPath
}

/**
 * Check if file is gzipped
 * @param {string} filePath
 * @returns {boolean}
 */
async function isGzipped(filePath) {
  return isgzipBuffer(await fs.readFile(filePath));
}

/**
 * Poll for async response at the given URL (S3 or GCS)
 * Checks every 10 seconds for up to 120 seconds
 * 
 * @param {string} locationUrl - S3 or GCS URL to poll
 * @param {Object} options - Options for authentication and polling
 * @param {string} options.role - AWS role ARN (for S3)
 * @param {string} options.region - AWS region (for S3)
 * @param {string} options.token - GCP token (for GCS)
 * @param {boolean} options.verbose - Verbose logging
 * @returns {Promise<string>} - Unzipped content as string
 */
async function pollAsyncResponse(locationUrl, options = {}) {
  const logger = getLogger(options.verbose);

  // Support s3:// and gs:// schemes as well as https://
  let url;
  let isS3 = false;
  let isGCS = false;
  let bucketName, keyOrFile;

  if (locationUrl.startsWith('s3://')) {
    isS3 = true;
    // s3://bucket/key
    const match = locationUrl.match(/^s3:\/\/([^\/]+)\/(.+)$/);
    if (!match) throw new Error(`Invalid S3 URL: ${locationUrl}`);
    bucketName = match[1];
    keyOrFile = match[2];
  } else if (locationUrl.startsWith('gs://')) {
    isGCS = true;
    // gs://bucket/file
    const match = locationUrl.match(/^gs:\/\/([^\/]+)\/(.+)$/);
    if (!match) throw new Error(`Invalid GCS URL: ${locationUrl}`);
    bucketName = match[1];
    keyOrFile = match[2];
  } else {
    url = new URL(locationUrl);
    isS3 = url.hostname.includes('s3.amazonaws.com') || url.hostname.includes('s3.');
    isGCS = url.hostname.includes('storage.googleapis.com');
    if (isS3) {
      // https://bucket.s3.region.amazonaws.com/key
      bucketName = url.hostname.split('.')[0];
      keyOrFile = url.pathname.substring(1);
    } else if (isGCS) {
      // https://storage.googleapis.com/bucket/file
      const pathParts = url.pathname.split('/');
      bucketName = pathParts[1];
      keyOrFile = pathParts.slice(2).join('/');
    }
  }

  if (!isS3 && !isGCS) {
    throw new Error(`Unsupported storage URL: ${locationUrl}. Only S3, GCS, and their HTTPS URLs are supported.`);
  }

  logger.info(`Polling for async response at: ${locationUrl}`);
  logger.info(`Polling every 10 seconds for up to 120 seconds...`);

  const maxAttempts = 12; // 120 seconds / 10 seconds
  let attempt = 0;

  while (attempt < maxAttempts) {
    attempt++;
    logger.verbose(`Polling attempt ${attempt}/${maxAttempts}...`);

    try {
      let content;
      let contentEncoding;
      
      if (isS3) {
        const credentials = await getAWSCredentials(options.role, options.region);
        const s3Client = new S3Client({
          region: options.region || 'us-east-1',
          credentials: credentials,
        });
        const response = await s3Client.send(new GetObjectCommand({
          Bucket: bucketName,
          Key: keyOrFile
        }));
        
        // Handle different types of response bodies from AWS SDK v3
        let contentBuffer;
        if (response.Body && typeof response.Body.transformToString === 'function') {
          // If it has transformToString, use it but get the raw bytes first
          const rawContent = await response.Body.transformToString('binary');
          contentBuffer = Buffer.from(rawContent, 'binary');
        } else if (response.Body && response.Body.pipe) {
          // If it's a readable stream
          const chunks = [];
          for await (const chunk of response.Body) {
            chunks.push(chunk);
          }
          contentBuffer = Buffer.concat(chunks);
        } else {
          // Fallback
          contentBuffer = Buffer.from(response.Body);
        }
        
        contentEncoding = response.ContentEncoding;
        logger.verbose(`S3 response Content-Encoding: ${contentEncoding}`);
        
        logger.success(`Content found after ${attempt * 10} seconds!`);
        
        // Check if content is gzipped using multiple methods
        const isGzippedByHeader = contentEncoding === 'gzip';
        const isGzippedByBuffer = isgzipBuffer(contentBuffer);
        
        if (isGzippedByHeader || isGzippedByBuffer) {
          logger.verbose('Content appears to be compressed, attempting decompression...');
          
          // Try different decompression methods
          try {
            const decompressed = await new Promise((resolve, reject) => {
              zlib.gunzip(contentBuffer, (err, result) => {
                if (err) {
                  logger.verbose(`Gunzip failed: ${err.message}, trying inflate...`);
                  // Try inflate as fallback (for deflate compression)
                  zlib.inflate(contentBuffer, (inflateErr, inflateResult) => {
                    if (inflateErr) {
                      reject(new Error(`Decompression failed: gunzip error: ${err.message}, inflate error: ${inflateErr.message}`));
                    } else {
                      resolve(inflateResult.toString());
                    }
                  });
                } else {
                  resolve(result.toString());
                }
              });
            });
            return decompressed;
          } catch (decompressError) {
            logger.error(`All decompression methods failed: ${decompressError.message}`);
            logger.verbose('Returning raw content as fallback');
            return contentBuffer.toString();
          }
        } else {
          logger.verbose('Content appears to be uncompressed, returning as-is');
          return contentBuffer.toString();
        }
      } else if (isGCS) {
        const storage = new Storage();
        const bucket = storage.bucket(bucketName);
        const file = bucket.file(keyOrFile);
        const [fileContent] = await file.download();
        content = fileContent.toString();
        // GCS doesn't return Content-Encoding in the same way, so we'll rely on buffer detection
        logger.verbose('GCS response - checking buffer for gzip signature');
        
        logger.success(`Content found after ${attempt * 10} seconds!`);
        
        // Check if content is gzipped using multiple methods
        const contentBuffer = Buffer.from(content, 'binary');
        const isGzippedByBuffer = isgzipBuffer(contentBuffer);
        
        // Check if content looks like JSON (starts with { or [)
        const contentStart = content.trim().substring(0, 1);
        const looksLikeJson = contentStart === '{' || contentStart === '[';
        
        // Only attempt decompression if we have strong indicators
        if (isGzippedByBuffer && !looksLikeJson) {
          logger.verbose('Content appears to be compressed, attempting decompression...');
          
          // Try different decompression methods
          try {
            const decompressed = await new Promise((resolve, reject) => {
              zlib.gunzip(contentBuffer, (err, result) => {
                if (err) {
                  logger.verbose(`Gunzip failed: ${err.message}, trying inflate...`);
                  // Try inflate as fallback (for deflate compression)
                  zlib.inflate(contentBuffer, (inflateErr, inflateResult) => {
                    if (inflateErr) {
                      reject(new Error(`Decompression failed: gunzip error: ${err.message}, inflate error: ${inflateErr.message}`));
                    } else {
                      resolve(inflateResult.toString());
                    }
                  });
                } else {
                  resolve(result.toString());
                }
              });
            });
            return decompressed;
          } catch (decompressError) {
            logger.error(`All decompression methods failed: ${decompressError.message}`);
            logger.verbose('Returning raw content as fallback');
            return content;
          }
        } else {
          logger.verbose('Content appears to be uncompressed, returning as-is');
          return content;
        }
      }
    } catch (error) {
      if (error.name === 'NoSuchKey' || error.code === 404) {
        logger.verbose(`File not ready yet (attempt ${attempt}/${maxAttempts})`);
      } else {
        logger.verbose(`Unexpected error during polling: ${error.message}`);
      }
    }

    if (attempt < maxAttempts) {
      await new Promise(resolve => setTimeout(resolve, 10000));
    }
  }

  throw new Error(`Timeout: File not available after ${maxAttempts * 10} seconds of polling`);
}

export {
  addFilenameSuffix, environmentCheck,
  executeCommand,
  executeWithRetry,
  getAWSCredentials,
  getCommonHTTPHeaders,
  getFileNameFromURL,
  isGzipped,
  parseBucketOption, pollAsyncResponse, requestWrapper as request,
  resolveAWSRegion,
  resolveHTTPMethod,
  saveToFile,
  signAWSRequestURL,
  signJwtWithAWSKMS,
  signJwtWithGCPKMS,
  transformSpecWithResponse, unzip
};

