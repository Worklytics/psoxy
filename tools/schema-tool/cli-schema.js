#!/usr/bin/env node
import chalk from 'chalk';
import { Command } from 'commander';
import { createRequire } from 'module';
import { fetchEndpoint, inferSchema } from './lib/schema.js';
import getLogger from './lib/logger.js';

const require = createRequire(import.meta.url);
const { version } = require('./package.json');

(async function () {
  const program = new Command();

  program
    .name('cli-schema.js')
    .version(version)
    .description('Infer the JSON schema of an HTTP API endpoint response')
    .requiredOption('-e, --endpoint <url>', 'Endpoint URL to call')
    .requiredOption('-a, --auth <token>', 'Bearer token for authentication')
    .option('--raw', 'Print raw response body instead of inferred schema', false)
    .option('-v, --verbose', 'Verbose output', false)
    .configureOutput({
      outputError: (str, write) => write(chalk.bold.red(str)),
    });

  program.addHelpText(
    'after',
    `
Example calls:
  node cli-schema.js -e https://api.example.com/v1/users -a my-bearer-token
  node cli-schema.js --endpoint "https://api.example.com/v1/items?limit=10" --auth my-token --raw
    `
  );

  program.parse(process.argv);
  const options = program.opts();
  const logger = getLogger(options.verbose);

  let url;
  try {
    url = new URL(options.endpoint);
  } catch {
    logger.error(`"${options.endpoint}" is not a valid URL`);
    process.exitCode = 1;
    return;
  }

  try {
    const result = await fetchEndpoint(url, options.auth);

    logger.verbose(`Response status: ${result.status} ${result.statusMessage}`);
    logger.verbose(`Response headers:\n${JSON.stringify(result.headers, null, 2)}`);

    if (result.status >= 200 && result.status < 300) {
      if (options.raw) {
        console.log(result.body);
        return;
      }

      let parsed;
      try {
        parsed = JSON.parse(result.body);
      } catch (err) {
        logger.error(`Response is not valid JSON: ${err.message}`);
        process.exitCode = 1;
        return;
      }

      const schema = inferSchema(parsed);
      logger.info(`Schema for ${options.endpoint}:`);
      console.log(JSON.stringify(schema, null, 2));
    } else {
      logger.error(`HTTP ${result.status}: ${result.statusMessage || 'Unknown error'}`);
      if (result.body) {
        try {
          const errorBody = JSON.parse(result.body);
          logger.error('Error details:', { additional: errorBody });
        } catch {
          logger.error(result.body);
        }
      }
      process.exitCode = 1;
    }
  } catch (err) {
    logger.error(`Request failed: ${err.message}`);
    process.exitCode = 1;
  }
})();
