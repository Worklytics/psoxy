import { constants as httpCodes } from 'http2';
import { execFileSync } from 'child_process';
import {
  addFilenameSuffix,
  environmentCheck,
  isGzipped,
  parseBucketOption,
  unzip,
} from './lib/utils.js';
import aws from './lib/aws.js';
import fs from 'fs';
import gcp from './lib/gcp.js';
import getLogger from './lib/logger.js';
import path from 'path';

const TIMESTAMP = Date.now();
const SANITIZED_FILE_SUFFIX = 'sanitized';

/**
 * AWS upload/download
 *
 * @param {Object} options - see default export
 * @param {Object} logger
 * @returns {Object} paths
 * @returns {string} paths.original
 * @returns {string} paths.sanitized
 */
async function testAWS(options, logger) {
  const parsedPath = path.parse(options.file);
  const filenameWithTimestamp = addFilenameSuffix(parsedPath.base, TIMESTAMP);

  if (options.role) {
    logger.verbose(`Assuming role ${options.role}`);
  }
  const client = await aws.createS3Client(options.role, options.region);

  const parsedBucketInputOption = parseBucketOption(options.input);
  // not destructuring to avoid variable name collision with path module
  const inputBucket = parsedBucketInputOption.bucket;
  const inputPath = parsedBucketInputOption.path;
  const inputKey = inputPath + filenameWithTimestamp;
  logger.info(`Uploading "${inputPath + parsedPath.base}" as "${inputKey}" to input bucket: ${inputBucket}`);

  const uploadResult = await aws.upload(inputBucket, inputKey, options.file, {
      role: options.role,
      region: options.region,
    }, client);

  if (uploadResult['$metadata'].httpStatusCode !== httpCodes.HTTP_STATUS_OK) {
    throw new Error('Unable to upload file', { cause: uploadResult });
  }
  logger.success('File uploaded');

  const parsedBucketOutputOption = parseBucketOption(options.output)
  const outputBucket = parsedBucketOutputOption.bucket;
  const outputPath = parsedBucketOutputOption.path;
  const outputKey = outputPath + filenameWithTimestamp;
  logger.info(`Downloading sanitized file from output bucket: ${outputBucket}`);

  const sanitizedFilename = addFilenameSuffix(outputKey, SANITIZED_FILE_SUFFIX);
  const destination = `./${sanitizedFilename}`;

  const file = await aws.download(outputBucket, outputKey, destination, {
      role: options.role,
      region: options.region,
    }, client, logger);
  logger.success('File downloaded');

  if (file?.metadata) {
    logger.verbose('File metadata:', { additional: file.metadata });
  }

  if (!options.keepSanitizedFile) {
    logger.info(`Deleting sanitized file from output bucket: ${outputBucket}`);
    try {
      // Note:
      // We don't use bucket versioning. The S3 client will attempt to delete
      // the default "null" version of the object, but:
      // > If there isn't a null version, Amazon S3 does not remove any objects
      //   but will still respond that the command was successful.
      await aws.deleteObject(outputBucket, outputKey, options, client);
    } catch (error) {
      logger.error(`Error deleting sanitized file: ${error.message}`, {
        additional: error,
      });
    }
  }

  return {
    original: options.file,
    sanitized: destination,
  }
}

/**
 * GCP upload/download
 *
 * @param {Object} options - see default export
 * @param {Object} logger
 * @returns {Object} paths
 * @returns {string} paths.original
 * @returns {string} paths.sanitized
 */
async function testGCP(options, logger) {
  const parsedPath = path.parse(options.file);
  // Add timestamp to filename to make sure download process doesn't get the
  // wrong file
  const filenameWithTimestamp = addFilenameSuffix(parsedPath.base, TIMESTAMP);

  const client = gcp.createStorageClient();

  const parsedBucketInputOption = parseBucketOption(options.input);
  const inputBucket = parsedBucketInputOption.bucket;
  const inputPath = parsedBucketInputOption.path;
  const inputKey = inputPath + filenameWithTimestamp;
  logger.info(`Uploading "${inputPath + parsedPath.base}" as "${inputKey}" to input bucket: ${inputBucket}`);
  const [, uploadResult] = await gcp.upload(inputBucket, options.file,
    client, inputKey);

  logger.success(`File uploaded -> ${uploadResult.mediaLink}`);
  logger.verbose('Upload result:', { additional: uploadResult });

  const parsedBucketOutputOption = parseBucketOption(options.output);
  const outputBucket = parsedBucketOutputOption.bucket;
  const outputPath = parsedBucketOutputOption.path;
  const outputKey = outputPath + filenameWithTimestamp;
  logger.info(`Downloading sanitized file from output bucket: ${outputBucket}`);

  // where to save the sanitized file; the file in the output bucket will have
  // {original filename}-{timestamp} as filename, so we save it locally as
  // {original filename}-{timestamp}-{sanitized} to minimize the chance of
  // modifying files in the system
  const sanitizedFilename = addFilenameSuffix(outputKey, SANITIZED_FILE_SUFFIX);
  const destination = `./${sanitizedFilename}`;

  const file = await gcp.download(outputBucket, outputKey, destination, client, logger);
  logger.success('File downloaded');

  if (file?.metadata) {
    logger.verbose('File metadata:', { additional: file.metadata });
  }

  if (!options.keepSanitizedFile) {
    logger.info(`Deleting sanitized file from output bucket: ${outputBucket}`);
    try {
      await gcp.deleteFile(outputBucket, outputKey, client);
    } catch (error) {
      logger.error(`Error deleting sanitized file: ${error.message}`);
    }
  }

  return {
    original: options.file,
    sanitized: destination,
  }
}

/**
 * Test HRIS files:
 * 1. Upload file (then Psoxy processes it and puts it in the output bucket)
 * 2. Download file (retry if necessary)
 * 3. Diff input and output
 *
 * @param {Object} options
 * @param {boolean} options.verbose
 * @param {string} options.deploy - required: AWS|GCP
 * @param {string} options.file
 * @param {string} options.input
 * @param {string} options.output
 * @param {string} options.region - AWS: buckets region
 * @param {string} options.role - AWS: role to assume (ARN format; optional)
 * @param {boolean} options.saveSanitizedFile - Whether to save sanitized file or not
 * @param {boolean} options.keepSanitizedFile - Whether to delete sanitized file or not (from
 *  output bucket, after test completion)
 * @returns {string}
 */
export default async function (options = {}) {
  const logger = getLogger(options.verbose);
  environmentCheck(logger);

  const deploymentTypeFn = options.deploy === 'AWS' ? testAWS : testGCP;
  const { original, sanitized } = await deploymentTypeFn(options, logger);

  let originalDiffPath = original;
  let sanitizedDiffPath = sanitized;
  const isOriginalGzipped = await isGzipped(original);
  if (isOriginalGzipped) {
    originalDiffPath = await unzip(original);
  }

  let diff;
  try {
    logger.info('Comparing input and sanitized output:\n');
    const diff = execFileSync('diff', [ originalDiffPath, sanitizedDiffPath ]);
    logger.info(diff);
  } catch (error) {
    // if files are different `diff` will end with exit code 1, so print results
    diff = error.stdout.toString();
    logger.info(diff);
  }

  if (isOriginalGzipped) {
    // delete unzipped files
    [originalDiffPath, sanitizedDiffPath]
      .forEach(filePath => fs.unlinkSync(filePath));
  }

  if (!options.saveSanitizedFile) {
    // delete sanitized file
    fs.unlinkSync(sanitized);
  } else {
    logger.info(`Sanitized file saved to ${path.resolve(sanitized)}`);
  }

  return diff;
}
