import { constants as httpCodes } from 'http2';
import { execFileSync } from 'child_process';
import { fileURLToPath } from 'url';
import { saveToFile, parseBucketOption, addFilenameSuffix } from './lib/utils.js';
import aws from './lib/aws.js';
import chalk from 'chalk';
import fs from 'fs';
import gcp from './lib/gcp.js';
import getLogger from './lib/logger.js';
import path from 'path';
import _ from 'lodash';

const __dirname = path.dirname(fileURLToPath(import.meta.url));

const TIMESTAMP = Date.now();

/**
 * AWS upload/download
 *
 * @param {Object} options - see default export
 * @param {Object} logger
 * @returns {string} - downloaded file contents
 */
async function testAWS(options, logger) {
  const parsedPath = path.parse(options.file);

  // Add timestamp to filename to make sure download process doesn't get the wrong file
  const filenameWithTimestamp = addFilenameSuffix(parsedPath.name, TIMESTAMP);

  if (options.role) {
    logger.verbose(`Assuming role ${options.role}`);
  }
  const client = await aws.createS3Client(options.role, options.region);


  const parsedBucketInputOption = parseBucketOption(options.input);
  // not destructuring to avoid variable name collision with path module
  const inputBucket = parsedBucketInputOption.bucket;
  const inputPath = parsedBucketInputOption.path;
  const inputKey = inputPath + filenameWithTimestamp;
  logger.info(`Uploading "${inputPath + parsedPath.base}" to input bucket: ${inputBucket}`);

  const uploadResult = await aws.upload(inputBucket, inputKey, options.file, {
      role: options.role,
      region: options.region,
    }, client);

  if (uploadResult['$metadata'].httpStatusCode !== httpCodes.HTTP_STATUS_OK) {
    throw new Error('Unable to upload file', { cause: uploadResult });
  }

  logger.success('File uploaded');
  logger.verbose('Upload result: ', { additional: uploadResult });

  const parsedBucketOutputOption = parseBucketOption(options.output)
  const outputBucket = parsedBucketOutputOption.bucket;
  const outputPath = parsedBucketOutputOption.path;
  const outputKey = outputPath + filenameWithTimestamp;
  logger.info(`Downloading sanitized file from output bucket: ${outputBucket}`);

  const downloadResult = await aws.download(outputBucket, outputKey, {
      role: options.role,
      region: options.region,
    }, client, logger);

  logger.success('File downloaded');

  if (options.deleteSanitizedFile) {
    logger.verbose(`Deleting sanitized file from output bucket: ${outputBucket}`);
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

  return downloadResult;
}

/**
 * GCP upload/download
 *
 * @param {Object} options - see default export
 * @param {Object} logger
 * @returns {string} - downloaded file contents
 */
async function testGCP(options, logger) {
  const parsedPath = path.parse(options.file);
  const filenameWithTimestamp = addFilenameSuffix(parsedPath.base, TIMESTAMP);

  const client = gcp.createStorageClient();

  const parsedBucketInputOption = parseBucketOption(options.input);
  const inputBucket = parsedBucketInputOption.bucket;
  const inputPath = parsedBucketInputOption.path;
  const inputKey = inputPath + filenameWithTimestamp;
  logger.info(`Uploading "${parsedPath.base}" to input bucket: ${inputBucket}`);
  const [, uploadResult] = await gcp.upload(inputBucket, options.file,
    client, inputKey);

  logger.success(`File uploaded -> ${uploadResult.mediaLink}`);
  logger.verbose('Upload result:', { additional: uploadResult });

  const parsedBucketOutputOption = parseBucketOption(options.output);
  const outputBucket = parsedBucketOutputOption.bucket;
  const outputPath = parsedBucketOutputOption.path;
  const outputKey = outputPath + filenameWithTimestamp;
  logger.info(`Downloading sanitized file from output bucket: ${outputBucket}`);
  const [downloadResult] = await gcp.download(outputBucket, outputKey, client,
    logger);
  logger.success('File downloaded');

  const fileContents = downloadResult.toString('utf8');
  logger.verbose('Download result:', { additional: fileContents });

  if (options.deleteSanitizedFile) {
    logger.verbose(`Deleting sanitized file from output bucket: ${outputBucket}`);
    try {
      await gcp.deleteFile(outputBucket, outputKey, client);
    } catch (error) {
      logger.error(`Error deleting sanitized file: ${error.message}`);
    }
  }

  return fileContents;
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
 * @param {boolean} options.deleteSanitizedFile - Whether to delete sanitized file or not (from
 *  output bucket, after test completion)
 * @returns {string}
 */
export default async function (options = {}) {
  const logger = getLogger(options.verbose);

  let downloadResult;
  if (options.deploy === 'AWS') {
    downloadResult = await testAWS(options, logger);
  } else {
    downloadResult = await testGCP(options, logger);
  }

  const parsedPath = path.parse(options.file);
  const outputFilename = addFilenameSuffix(parsedPath.base, 'sanitized');
  // Always saving it to be able to easily "diff" input and output/sanitized;
  // delete the sanitized one later
  await saveToFile(__dirname, outputFilename, downloadResult);

  let outputFilePath = `${__dirname}/${outputFilename}`;

  try {
    logger.info('Comparing input and sanitized output:\n');
    logger.info(execFileSync('diff', [ options.file, outputFilePath ]));
  } catch(error) {
    // if files are different `diff` will end with exit code 1, so print results
    logger.info(error.stdout.toString());
  }

  fs.unlinkSync(outputFilePath);

  if (options.saveSanitizedFile) {
    // save file to same location as input
    let outputDir = path.parse(options.file).dir;
    if (_.isEmpty(outputDir)) {
      // fallback to current exe path
      outputDir = process.cwd();
    }
    logger.info(`Sanitized file written at ${chalk.yellow(new Date().toISOString())}: ${outputDir}/${outputFilename}`);
    await saveToFile(outputDir, outputFilename, downloadResult);
  }

  return downloadResult;
}
