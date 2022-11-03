import aws from './lib/aws.js';
import { saveToFile } from './lib/utils.js';
import { execSync } from 'child_process';
import path from 'path';
import getLogger from './lib/logger.js';

/**
 * Test HRIS files:
 * 1. Upload file (then Psoxy processes it and puts it in the output bucket)
 * 2. Download file (retry if necessary)
 * 3. Diff input and output
 * 
 * @param {Object} options 
 * @param {boolean} options.verbose
 * @param {string} options.file
 * @param {string} options.input
 * @param {string} options.output
 * @param {string} options.region
 * @param {string} options.role
 * @returns 
 */
export default async function (options = {}) {
  const logger = getLogger(options.verbose);
  const filename = path.basename(options.file);
  
  if (options.role) {
    logger.verbose(`Assuming role ${options.role}`);
  }
  let client = aws.createS3Client(options.role, options.region);

  logger.info(`Uploading ${filename} to input bucket: ${options.input}`);
  const uploadResult = await aws.upload(options.input, options.file, {
    role: options.role,
    region: options.region
  }, client);

  if (uploadResult['$metadata'].httpStatusCode !== 200) {
    throw new Error('Unable to upload file', { cause: uploadResult });
  }

  logger.success('File uploaded');
  logger.verbose('Upload result: ', { additional: uploadResult });  

  logger.info(`Downloading processed file from output bucket: ${options.output}`);
  const downloadResult = await aws.download(options.output, filename, {
    role: options.role,
    region: options.region
  }, client);
  logger.success('File downloaded');

  const outputDir = path.parse(options.file).dir;
  const outputFilename = `${path.parse(options.file).name}-proccessed${path.extname(options.file)}`;
  logger.info(`Saving processed file to: ${outputDir}/${outputFilename}`);
  saveToFile(outputDir, outputFilename, downloadResult);

  try {
    logger.info('Comparing files:\n');
    logger.info(execSync(`diff ${options.file} ${outputDir}/${outputFilename}`));
  } catch(error) {
    // if files are different `diff` will end with exit code 1, so print results
    logger.info(error.stdout.toString());
  }
  
  return downloadResult;
}
