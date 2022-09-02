#!/usr/bin/env node
import { createRequire } from 'module';
import { Command } from 'commander';
import chalk from 'chalk';
import psoxyTest from './index.js';

const require = createRequire(import.meta.url);
const { name, version, description } = require('./package.json');

const theme = {
  error: chalk.bold.red,
  success: chalk.green,
};

(async function () {
  const program = new Command();

  program
    .name(name)
    .version(version)
    .description(description)
    .requiredOption('-u, --url <url>', 'URL to call')
    .option('-f, --force <type>', 'Force deploy type: AWS or GCP')
    .option('-i, --impersonate <user>', 'User to impersonate, needed for certain connectors')
    .option('-r, --role <arn>', 'AWS role to assume, use its ARN')
    .option(
      '-s, --skip',
      'Skip sanitization rules, only works if function deployed in development mode',
      false
    )
    .option('-t, --token <token>', 'Authorization token for GCP')
    .option('-v, --verbose', 'Verbose output', false)
    .option('-z, --gzip', 'Add gzip compression header', false)
    .option('-f, --file', 'Save test results to file', false)
    .configureOutput({
      wirteOut: undefined,
      writeErr: undefined,
    });

  program.addHelpText(
    'after',
    `Example call: ${name} -u https://url-to-psoxy-function/path-to-api`
  );

  program.parse(process.argv);
  const options = program.opts();

  const result = await psoxyTest(options);

  if (result.error) {
    console.error(`${theme.error(result.error)}`);
  } else {
    console.log(`${theme.success('OK')}`);
    console.log(JSON.stringify(result.response.data, undefined, 4));
  }
})();
