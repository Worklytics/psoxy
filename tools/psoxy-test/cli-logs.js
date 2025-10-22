#!/usr/bin/env node
import chalk from 'chalk';
import { Command } from 'commander';
import { createRequire } from 'module';
import getLogger from './lib/logger.js';
import psoxyTestLogs from './psoxy-test-logs.js';

const require = createRequire(import.meta.url);
const { version } = require('./package.json');

(async function () {
  const program = new Command();

  program
    .name('cli-logs.js')
    .version(version)
    .description(`
      Psoxy Test: display logs
      CloudWatch logs of AWS deploys or runtime logs of GCP deploys
    `)
    .option('-p --project-id <projectId>', 'GCP: Name of the project that hosts the cloud function (Psoxy instance)')
    .option('-f --function-name <functionName>', 'GCP: Name of the cloud function from which to list entries')
    .option('-l, --log-group-name <logGroupName>', 'AWS: Log group to display')
    .option('-r, --role <arn>', 'AWS: ARN of IAM role to assume; if omitted, AWS CLI must be authenticated as a principal with perms to read from log group')
    .option('--region <region>', 'AWS: region of your Psoxy instance',
      'us-east-1')
    .option('-v, --verbose', 'Verbose output', false)
    .configureOutput({
      outputError: (str, write) => write(chalk.bold.red(str)),
    });

  program.addHelpText(
    'after',
    `
      AWS example call: node cli-logs.js -l \"/aws/lambda/psoxy-name\" -r \"arn:aws:iam::id:myRole\" --region us-east-1\n
      GCP example call: node cli-logs.js -p \"psoxy-project-name\" -f \"psoxy-function-name\"
    `
  );

  program.parse(process.argv);
  const options = program.opts();
  const logger = getLogger(options.verbose);

  let result;
  try {
    result = await psoxyTestLogs(options, logger);
  } catch (error) {
    logger.error(error.message);
    process.exitCode = 1;
  }
  return result;
})();
