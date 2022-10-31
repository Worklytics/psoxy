import aws from './lib/aws.js';
import { execSync } from 'child_process';
import path from 'path';
import fs from 'fs';

/**
 * Test HRIS files:
 * 1. Upload file (then Psoxy processes it and puts it in the output bucket)
 * 2. Download file (retry if necessary)
 * 3. Diff input and output
 * 
 * @param {Object} options 
 * @param {string} options.role
 * @param {string} options.input
 * @param {string} options.output
 * @param {string} options.file
 * @param {string} options.region
 * @returns 
 */
export default async function (options = {}) {
  const filename = path.basename(options.file);
  
  const client = aws.createS3Client(options.role, options.region);

  const uploadResult = await aws.upload(options.input, options.file, {
    role: options.role,
    region: options.region
  }, client);
  console.log('File upload:\n', uploadResult);

  const downloadResult = await aws.download(options.output, filename, {
    role: options.role,
    region: options.region
  }, client);
  
  const processed = `${path.parse(options.file).dir}/anonymized-${filename}`;
  fs.writeFileSync(processed, downloadResult, 'utf-8');

  // TODO improve "diff" mechanism
  try {
    console.log(execSync(`diff ${options.file} ${processed}`));
  } catch(error) {
    // if files are different `diff` will end with exit code 1, so print results
    console.log(error.stdout.toString());
  }
  
  return downloadResult;
}
