import aws from './lib/aws.js';
import gcp from './lib/gcp.js';
import { constants as httpCodes } from 'http2';
import { saveToFile } from './lib/utils.js';
import { execSync } from 'child_process';
import path from 'path';
import getLogger from './lib/logger.js';

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
  let client = aws.createS3Client(options.role, options.region);

  logger.info(`Uploading "${filename}" to input bucket: ${options.input}`);
  const uploadResult = await aws.upload(options.input, options.file, {
    role: options.role,
    region: options.region,
    filename: filenameWithTimestamp,
  }, client);

  if (uploadResult['$metadata'].httpStatusCode !== httpCodes.HTTP_STATUS_OK) {
    throw new Error('Unable to upload file', { cause: uploadResult });
  }

  logger.success('File uploaded');
  logger.verbose('Upload result: ', { additional: uploadResult });

  logger.info(`Downloading processed file from output bucket: ${options.output}`);
  const downloadResult = await aws.download(options.output, 
    filenameWithTimestamp, {
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

  logger.info(`Uploading "${filename}" to input bucket: ${options.input}`);
  const [, uploadResult] = await gcp.upload(options.input, options.file,
    client, filenameWithTimestamp);

  logger.success(`File uploaded -> ${uploadResult.mediaLink}`);
  logger.verbose('Upload result:', { additional: uploadResult });

  logger.info(`Downloading processed file from output bucket: ${options.output}`);
  const [downloadResult] = await gcp.download(options.output, 
    filenameWithTimestamp, client, logger);
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
 * @param {string} options.role - AWS: role to assume (ARN format)
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

  const outputDir = path.parse(options.file).dir;
  const outputFilename = `${path.parse(options.file).name}-proccessed${path.extname(options.file)}`;
  logger.info(`Saving processed file to: ${outputDir}/${outputFilename}`);
  await saveToFile(outputDir, outputFilename, downloadResult);

  try {
    logger.info('Comparing files:\n');
    logger.info(execSync(`diff ${options.file} ${outputDir}/${outputFilename}`));
  } catch(error) {
    // if files are different `diff` will end with exit code 1, so print results
    logger.info(error.stdout.toString());
  }

  return downloadResult;
}
