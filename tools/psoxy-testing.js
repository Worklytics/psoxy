#!/usr/bin/env node

import { createRequire } from 'module';
const require = createRequire(import.meta.url);
const { name, version, description } = require('./package.json');
import { Command } from 'commander';
import chalk  from 'chalk';
import aws from './aws.js';
import gcp from './gcp.js';

const theme = {
  error: chalk.bold.red,
  warning: chalk.yellow,
  success: chalk.green,
};
(async function() {
  const program = new Command()

  program
    .name(name)
    .version(version)
    .description(description)
    .requiredOption('-u, --url <url>', 'URL to call')
    .option('-f, --force <type>', 'Force deploy type: AWS or GCP')
    .option('-i, --impersonate', 'User to impersonate, needed for certain connectors')
    .option('-r, --role <arn>', 'AWS role to impersonate, use its ARN')
    .option('-s, --skip', 'Skip sanitization rules, only works if function deployed in development mode', false)
    .option('-t, --token <token>', 'Authorization token for GCP')
    .option('-v, --verbose', 'Verbose output', false)
    .option('-z, --gzip', 'Add gzip compression header', false)
    .configureOutput({
      outputError: (str, write) => write(theme.error(str))
    });
    
  program.addHelpText('after', `
  Example call: ${name} -u https://url-to-psoxy-function/path-to-api
  `);

  program.parse(process.argv)
  const options = program.opts();

  let url;
  try {
    url = new URL(options.url);
  } catch (err) {
    console.error(theme.error(`"${options.url}" is not a valid URL`));
    return;
  }

  const isAWS = aws.isAWS(url);
  const isGCP = gcp.isGCP(url);
  let test;

  if (['AWS', 'GCP'].includes(options.force)) {
    test = options.force === 'AWS' ? aws.test : gcp.test;
  } else if (!isAWS && !isGCP) {
    console.error(theme.error(`"${options.url}" doesn't seem to be a valid endpoint: AWS or GCP`));
    return;
  } else {
    test = isAWS ? aws.test : gcp.test;
  }

  try {
    const response = await test(options);

    if (response.status === 200) {
      console.log(`Result: ${theme.success('OK')}`);
      const data = await response.json();
      console.log(JSON.stringify(data, undefined, 4));
    } else {
      console.warn(`Result: ${theme.warning(response.statusText)}`);
      console.warn(`Status: ${theme.warning(response.status)}`);

      const psoxyError = response.headers.get('x-psoxy-error');

      if (psoxyError) {
        let msg;
        switch(psoxyError) {
          case 'BLOCKED_BY_RULES':
            msg = 'Blocked by rules error: make sure URL path is correct';
            break;
          case 'CONNECTION_SETUP':
            msg = 'Connection setup error: make sure the data source is properly configured';
            break;
          case 'API_ERROR':
            msg = 'API error: call to data source failed';
            break;
          default:
            msg = psoxyError;
        }
        console.error(theme.error(msg));
      }
    }

  } catch (err) {
    console.error(theme.error(err.message));
  }
})();