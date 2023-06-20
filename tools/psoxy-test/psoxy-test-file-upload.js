import { constants as httpCodes } from 'http2';
import { execSync } from 'child_process';
import { fileURLToPath } from 'url';
import { saveToFile, parseBucketOption } from './lib/utils.js';
import aws from './lib/aws.js';
import chalk from 'chalk';
import fs from 'fs';
import gcp from './lib/gcp.js';
import getLogger from './lib/logger.js';
import path from 'path';
import _ from 'lodash';

const __dirname = path.dirname(fileURLToPath(import.meta.url));

const FILE_EXTENSION = '.csv';
/**
 * AWS upload/download
 *
 * @param {Object} options - see default export
 * @param {Object} logger
 * @returns {string} - downloaded file contents
 */
async function testAWS(options, logger) {
  const filename = path.basename(options.file, FILE_EXTENSION);
  // Add timestamp to filename to make sure download process doesn't get
  // the wrong file
  const filenameWithTimestamp = `${filename}-${Date.now()}${FILE_EXTENSION}`;

  if (options.role) {
    logger.verbose(`Assuming role ${options.role}`);
  }
  const client = await aws.createS3Client(options.role, options.region);


  const parsedBucketInputOption = parseBucketOption(options.input);
  // not destructuring to avoid variable name collision with path module
  const inputBucket = parsedBucketInputOption.bucket;
  const inputPath = parsedBucketInputOption.path;
  const inputKey = inputPath + filenameWithTimestamp;
  logger.info(`Uploading "${inputPath + filename}" to input bucket: ${inputBucket}`);

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
  const filename = path.basename(options.file, FILE_EXTENSION);
  const filenameWithTimestamp = `${filename}-${Date.now()}${FILE_EXTENSION}`;
  const client = gcp.createStorageClient();

  const parsedBucketInputOption = parseBucketOption(options.input);
  const inputBucket = parsedBucketInputOption.bucket;
  const inputPath = parsedBucketInputOption.path;
  const inputKey = inputPath + filenameWithTimestamp;
  logger.info(`Uploading "${filename}" to input bucket: ${inputBucket}`);
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

  const outputFilename = `${path.parse(options.file).name}-sanitized${path.extname(options.file)}`;
  // Always saving it to be able to easily "diff" input and output/sanitized;
  // delete the sanitized one later
  await saveToFile(__dirname, outputFilename, downloadResult);

  try {
    logger.info('Comparing input and sanitized output:\n');
    logger.info(execSync(`diff ${options.file} ${__dirname}/${outputFilename}`));
  } catch(error) {
    // if files are different `diff` will end with exit code 1, so print results
    logger.info(error.stdout.toString());
  }

  fs.unlinkSync(`${__dirname}/${outputFilename}`);

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
