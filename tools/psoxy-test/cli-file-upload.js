#!/usr/bin/env node
import { createRequire } from 'module';
import { Command, Option } from 'commander';
import chalk from 'chalk';
import psoxyTestFileUpload from './psoxy-test-file-upload.js';
import getLogger from './lib/logger.js';
import _ from 'lodash';

const require = createRequire(import.meta.url);
const { version } = require('./package.json');

(async function () {
  const program = new Command();

  program
    .name('cli-file-upload.js')
    .version(version)
    .description(`
      Psoxy Test: Bulk instance
      Upload a CSV file containing some PII to the "input" bucket associated
      to a Psoxy Bulk instance (AWS or GCP) and get the processed file back
      from the "output" bucket with all PII removed.
    `)
    .addOption(
      new Option('-d, --deploy <type>', 'Deploy type')
        .choices(['AWS', 'GCP'])
        .makeOptionMandatory()
    )
    .requiredOption('-i, --input <bucketName>', 'Input bucket\'s name')
    .requiredOption('-f, --file <path/to/file>', 'Path of the file to be processed')
    .requiredOption('-o, --output <bucketName>', 'Output bucket\'s name')
    .option('-r, --role <arn>', 'ARN of AWS role to assume; if omitted, AWS CLI must be authenticated as a principal with perms to write to input bucket and read from output bucket')
    .option('-re, --region <region>', 'AWS region of the buckets (input/output)',
      'us-east-1')
    .option('-v, --verbose', 'Verbose output', false)
    .option('-s, --save-sanitized-file', 'Save sanitized file to disk', false)
    .option('-k, --keep-sanitized-file', 'Keep sanitized file from output bucket', false)
    .configureOutput({
      outputError: (str, write) => write(chalk.bold.red(str)),
    });

  program.addHelpText(
    'after',
    `
      AWS example call: node cli-file-upload.js -d AWS -i my-input-bucket -o my-output-bucket -f /path/to/file -r arn:aws:iam::id:myRole -re us-east-1
      GCP example call: node cli-file-upload.js -d GCP -i my-input-bucket -o my-output-bucket -f /path/to/file
    `
  );

  program.parse(process.argv);
  const options = program.opts();

  const logger = getLogger(options.verbose);

  if (!options.keepSanitizedFile) {
    logger.info(chalk.yellow(`
      Sanitized file will be deleted from the output bucket after test comparison.
      Use the "--keep-sanitized-file" option to keep the sanitized file.
    `));
  }

  // For async errors on 3rd party libraries
  process.on('uncaughtException', (error) => {
    logger.error(error.message);
    process.exit(1);
  });

  let result;
  try {
    result = await psoxyTestFileUpload(options);
  } catch (error) {
    logger.error(error.message);
    process.exitCode = 1;
  }
  return result;
})();
