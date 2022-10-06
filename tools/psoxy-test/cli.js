#!/usr/bin/env node
import { createRequire } from 'module';
import { Command, Option } from 'commander';
import chalk from 'chalk';
import psoxyTest from './index.js';
import { callDataSourceEndpoints } from './data-sources/runner.js';

const require = createRequire(import.meta.url);
const { name, version, description } = require('./package.json');

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
    .option('-s, --save-to-file', 'Save test results to file', false)
    .option('--skip', 
      'Skip sanitization rules, only works if function deployed in development mode',
      false
    )
    .option('-t, --token <token>', 'Authorization token for GCP')
    .option('-v, --verbose', 'Verbose output', false)
    .option('-z, --gzip', 'Add gzip compression header', false)
    .addOption(new Option('-d, --data-source <name>', 
      'Data source to test all available endpoints').choices([
        'gcal', 
        'gdrive', 
        'gdirectory',
        'google-chat', 
        'google-meet', 
        'slack-discovery-api',
        'zoom'
      ]))
    .configureOutput({
      outputError: (str, write) => write(chalk.bold.red(str)),
    });

  program.addHelpText(
    'after',
    `Example call: ${name} -u https://url-to-psoxy-function/path-to-api`
  );

  program.parse(process.argv);
  const options = program.opts();

  if (options.dataSource) {
    await callDataSourceEndpoints(options);
  } else {
    await psoxyTest(options);
  }
})();
