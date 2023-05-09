#!/usr/bin/env node
import { createRequire } from 'module';
import { Command, Option } from 'commander';
import chalk from 'chalk';
import psoxyTestCall from './psoxy-test-call.js';
import { callDataSourceEndpoints } from './data-sources/runner.js';
import getLogger from './lib/logger.js';

const require = createRequire(import.meta.url);
const { version } = require('./package.json');

(async function () {
  const program = new Command();

  program
    .name('cli-call.js')
    .version(version)
    .description('Test a Worklytics Psoxy instance via HTTP calls')
    .requiredOption('-u, --url <url>', 'URL to call')
    .option('-f, --force <type>', 'Force deploy type: AWS or GCP')
    .option('-i, --impersonate <user>', 'User to impersonate, needed for certain connectors')
    .option('-r, --role <arn>', 'ARN of AWS role to assume; if omitted, AWS CLI must be authenticated as a principal with perms to invoke the function directly')
    .option('-s, --save-to-file', 'Save test results to file', false)
    .option('--skip',
      'Skip sanitization rules, only works if function deployed in development mode',
      false
    )
    .option('-t, --token <token>', 'Authorization token for GCP')
    .option('-v, --verbose', 'Verbose output', false)
    .option('-z, --gzip', 'Add gzip compression header', false)
    .option('--health-check', 'Health Check call: check Psoxy deploy is running')
    .addOption(new Option('-d, --data-source <name>',
      'Data source to test all available endpoints').choices([
        'asana',
        'azure-ad',
        'dropbox-business',
        'gcal',
        'gdrive',
        'gdirectory',
        'gmail',
        'google-chat',
        'google-meet',
        'slack-discovery-api',
        'outlook-cal',
        'outlook-mail',
        'zoom'
      ]))
    .addOption(new Option('-m, --method <HTTP method>',
      'HTTP method used when calling URL', 'GET').choices(['GET', 'POST']))
    .configureOutput({
      outputError: (str, write) => write(chalk.bold.red(str)),
    });

  program.addHelpText(
    'after',
    `Example calls:
      AWS: node cli-call.js -u https://url-to-psoxy-function/path-to-api -r arn:aws:iam::id:myRole
      GCP: node cli-call.js -u https://url-to-psoxy-function/path-to-api -t foo
    `
  );

  program.parse(process.argv);
  const options = program.opts();
  const logger = getLogger(options.verbose);

  let result;
  try {
    if (options.dataSource) {
      result = await callDataSourceEndpoints(options);
    } else {
      result = await psoxyTestCall(options);
    }
  } catch (error) {
    logger.error(error.statusMessage || error.message);
    process.exitCode = 1;
  }
  return result;
})();
