#!/usr/bin/env node
import { createRequire } from 'module';
import { Command } from 'commander';
import chalk from 'chalk';
import psoxyTestLogs from './psoxy-test-logs.js';
import getLogger from './lib/logger.js';

const require = createRequire(import.meta.url);
const { version } = require('./package.json');

(async function () {
  const program = new Command();

  program
    .name('cli-logs.js')
    .version(version)
    .description(`
      Psoxy Test: display logs
      Get CloudWatch logs of a Worklytics Psoxy instance on AWS
    `)
    .requiredOption('-l, --log-group-name <logGroupName>', 'Log group to display')
    .option('-r, --role <arn>', 'AWS role to assume, use its ARN')
    .requiredOption('-re, --region <region>', 'AWS region of Psoxy instance', 
      'us-east-1')
    .option('-v, --verbose', 'Verbose output', false)
    .configureOutput({
      outputError: (str, write) => write(chalk.bold.red(str)),
    });

  program.addHelpText(
    'after',
    `Example call: node cli-logs.js -l \"/aws/lambda/psoxy-name\" -r \"arn:aws:iam::id:myRole\" -re us-east-1`
  );

  program.parse(process.argv);
  const options = program.opts();
  const logger = getLogger(options.verbose);

  let result;
  try {
    result = await psoxyTestLogs(options);
  } catch (error) {
    logger.error(error.message);
    process.exitCode = 1;
  }
  return result;
})();
