#!/usr/bin/env node
import chalk from 'chalk';
import { Command } from 'commander';
import { createRequire } from 'module';
import { fetchEndpoint, inferSchema, removeRequired, parseBody } from './lib/schema.js';

const require = createRequire(import.meta.url);
const { version } = require('./package.json');

const log = {
  info:    (msg) => console.error(chalk.bold.blue(msg)),
  success: (msg) => console.error(chalk.bold.green(msg)),
  error:   (msg) => console.error(chalk.bold.red(msg)),
  verbose: (msg, opts) => opts?.verbose && console.error(msg),
};

(async function () {
  const program = new Command();

  program
    .name('cli-schema.js')
    .version(version)
    .description('Infer the JSON schema of an HTTP API endpoint response')
    .requiredOption('-e, --endpoint <url>', 'Endpoint URL to call')
    .requiredOption('-a, --auth <token>', 'Bearer token for authentication')
    .option('--raw', 'Print raw response body instead of inferred schema', false)
    .option('--skip-headers', 'Exclude response headers from output', false)
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

  let url;
  try {
    url = new URL(options.endpoint);
  } catch {
    log.error(`"${options.endpoint}" is not a valid URL`);
    process.exitCode = 1;
    return;
  }

  try {
    const result = await fetchEndpoint(url, options.auth);

    log.verbose(`Response status: ${result.status} ${result.statusMessage}`, options);
    log.verbose(`Response headers:\n${JSON.stringify(result.headers, null, 2)}`, options);

    if (result.status >= 200 && result.status < 300) {
      if (options.raw) {
        console.log(result.body);
        return;
      }

      let parsed;
      try {
        parsed = parseBody(result.body);
      } catch (err) {
        log.error(`Response is not valid JSON or JSONL: ${err.message}`);
        process.exitCode = 1;
        return;
      }

      log.info(`Schema for ${options.endpoint}:`);
      const output = options.skipHeaders
        ? { schema: removeRequired(inferSchema(parsed)) }
        : { headers: result.headers, schema: removeRequired(inferSchema(parsed)) };
      console.log(JSON.stringify(output, null, 2));
    } else {
      log.error(`HTTP ${result.status}: ${result.statusMessage || 'Unknown error'}`);
      if (result.body) {
        try {
          console.error(JSON.stringify(JSON.parse(result.body), null, 2));
        } catch {
          console.error(result.body);
        }
      }
      process.exitCode = 1;
    }
  } catch (err) {
    log.error(`Request failed: ${err.message}`);
    process.exitCode = 1;
  }
})();
