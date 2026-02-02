#!/usr/bin/env node
import chalk from 'chalk';
import { Command, Option } from 'commander';
import _ from 'lodash';
import { createRequire } from 'module';
import { callDataSourceEndpoints } from './data-sources/runner.js';
import aws from './lib/aws.js';
import gcp from './lib/gcp.js';
import getLogger from './lib/logger.js';
import psoxyTestCall from './psoxy-test-call.js';

const require = createRequire(import.meta.url);
const { version } = require('./package.json');
// Basic regexp to capture ARNs in AWS error messages (colorize ARNs);
const AWS_ACCESS_DENIED_EXCEPTION_REGEXP = new RegExp(/(?<arn>arn:aws:iam::\d+:\w+\/\S+)/g);

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
    .option('--region <region>', 'AWS: region of your Psoxy instance')
    .option('-s, --save-to-file', 'Save test results to file', false)
    .option('--skip',
      'Skip sanitization rules, only works if function deployed in development mode',
      false
    )
    .option('-t, --token <token>', 'Authorization token for GCP')
    .option('-v, --verbose', 'Verbose output', false)
    .option('-z, --gzip [type]', 'Add gzip compression header (default: true, "-z false" to remove)', true)
    .option('--health-check', 'Health Check call: check Psoxy deploy is running')
    .option('--signing-key <key-ref>', 'Signing key reference to use for signing requests (e.g. "aws-kms:arn:aws:kms:us-east-1:123456789012:key/abcd1234-12ab-34cd-56ef-1234567890ab")')
    .option('--identity-issuer <issuer>', 'issuer of JWT (iss claim)')
    .option('--identity-subject <sub>', 'subject (sub) claim to include in JWT claims to be signed')
    .option('--request-no-response', "Request 'No response body' back from proxy (tests side-output case)", false)
    .option('--async', 'Process request asynchronously (adds X-Psoxy-Process-Async header)', false)
    .option('-b, --body <body>', 'Body to send in request (it expects a JSON string)')
    .option('--verify-collection <bucket>', 'Verify that the posted data appears in the specified bucket (GCS/S3)')
    .option('--scheduler-job <name>', 'GCP: Cloud Scheduler job name to trigger batch processing')
    .addOption(new Option('-d, --data-source <name>',
      'Data source to test all available endpoints').choices([
        //TODO: pull this list from terraform console or something??
        'asana',
        'azure-ad',
        'dropbox-business',
        'gcal',
        'gdrive',
        'gdirectory',
        'gmail',
        'google-chat',
        'google-meet',
        'msft-copilot',
        'msft-teams',
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
  if (_.isString(options.gzip)) {
    options.gzip = !(options.gzip === 'false');
  }

  const logger = getLogger(options.verbose);

  let result;
  try {
    const startTime = Date.now();
    if (options.dataSource) {
      result = await callDataSourceEndpoints(options);
    } else {
      result = await psoxyTestCall(options);
    }

    if (options.verifyCollection && result.status === 200) {
        // Delegate based on cloud provider logic
        const url = new URL(options.url);

       
       const isGcp = options.force?.toLowerCase() === 'gcp' || (options.force?.toLowerCase() !== 'aws' && gcp.isValidURL(url));
       const isAws = options.force?.toLowerCase() === 'aws' || (!isGcp && aws.isValidURL(url));

       if (isGcp) {
          await gcp.verifyCollection({
              ...options,
              bucketName: options.verifyCollection,
              startTime: startTime
          }, logger);
       } else {
          // Assume AWS or fallback
          await aws.verifyCollection({
              verifyCollection: options.verifyCollection,
              url: options.url,
              body: options.body,
              startTime: startTime,
              role: options.role,
              region: options.region,
          }, logger);
       }
    }

  } catch (error) {
    if (error?.name === 'AccessDenied' && error.message &&
      AWS_ACCESS_DENIED_EXCEPTION_REGEXP.test(error.message)) {
      const errorMessage = error.message.replace(
        AWS_ACCESS_DENIED_EXCEPTION_REGEXP, chalk.bold.red('$<arn>'));
      const fixErrorHint = chalk.blue('Make sure your AWS (MFA) session is not expired and that the ARN is included in the `caller_aws_arns` list of your "terraform.tfvars" (run `terraform apply` again if necessary).');
      logger.error(`${errorMessage}\n${fixErrorHint}`);
    } else {
      logger.error(error.statusMessage || error.message);
    }

    process.exitCode = 1;
  }
  return result;
})();
