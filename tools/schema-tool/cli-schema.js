#!/usr/bin/env node
import chalk from 'chalk';
import { Command } from 'commander';
import { readFile } from 'fs/promises';
import { createRequire } from 'module';
import { fetchEndpoint, inferSchema, removeRequired, parseBody } from './lib/schema.js';

const require = createRequire(import.meta.url);
const { version } = require('./package.json');

const log = {
  info:    (msg) => console.error(chalk.bold.blue(msg)),
  success: (msg) => console.error(chalk.bold.green(msg)),
  warn:    (msg) => console.error(chalk.bold.yellow(msg)),
  error:   (msg) => console.error(chalk.bold.red(msg)),
  verbose: (msg, opts) => opts?.verbose && console.error(msg),
};

/** Read all data from stdin as a UTF-8 string. */
function readStdin() {
  return new Promise((resolve, reject) => {
    let data = '';
    process.stdin.setEncoding('utf8');
    process.stdin.on('data', (chunk) => (data += chunk));
    process.stdin.on('end', () => resolve(data));
    process.stdin.on('error', reject);
  });
}

/**
 * Resolve the value of --input to a string of JSON/JSONL content.
 *
 * Resolution order:
 *   1. '-'        → read from stdin
 *   2. file path  → read the file (any readable path)
 *   3. otherwise  → treat the value itself as inline JSON/JSONL content
 *
 * This means you can pass either a file path or a raw JSON string directly
 * without any extra quoting or flags.
 */
async function readInputContent(pathOrDashOrJson) {
  if (pathOrDashOrJson === '-') return readStdin();
  try {
    return await readFile(pathOrDashOrJson, 'utf8');
  } catch (err) {
    if (err.code === 'ENOENT') {
      // Not a file path — treat the value itself as inline content.
      return pathOrDashOrJson;
    }
    throw err;
  }
}

(async function () {
  const program = new Command();

  program
    .name('cli-schema.js')
    .version(version)
    .description('Infer the JSON schema of an HTTP API endpoint response or local JSON/JSONL content')
    .option('-e, --endpoint <url>', 'Endpoint URL to fetch (mutually exclusive with --input)')
    .option('-a, --auth <token>', 'Bearer token for authentication (required with --endpoint)')
    .option('-i, --input <file|json|->', 'JSON/JSONL source: a file path, an inline JSON string, or - for stdin (mutually exclusive with --endpoint)')
    .option('--raw', 'Print raw response body / file content instead of inferring a schema', false)
    .option('--skip-headers', 'Exclude response headers from output (endpoint mode only)', false)
    .option('-v, --verbose', 'Verbose output', false)
    .configureOutput({
      outputError: (str, write) => write(chalk.bold.red(str)),
    });

  program.addHelpText(
    'after',
    `
Example calls:
  node cli-schema.js -e "https://api.example.com/v1/users" -a my-bearer-token
  node cli-schema.js --endpoint "https://api.example.com/v1/items?limit=10" --auth my-token --raw
  node cli-schema.js --input response.json
  node cli-schema.js --input response.jsonl
  cat response.json | node cli-schema.js --input -
    `
  );

  program.parse(process.argv);
  const options = program.opts();

  // ── Validate flag combinations ───────────────────────────────────────────

  if (options.input && options.endpoint) {
    log.error('--input and --endpoint are mutually exclusive');
    process.exitCode = 1;
    return;
  }

  if (!options.input && !options.endpoint) {
    log.error('Either --endpoint (-e) or --input (-i) is required');
    process.exitCode = 1;
    return;
  }

  if (options.endpoint && !options.auth) {
    log.error('--auth (-a) is required when using --endpoint');
    process.exitCode = 1;
    return;
  }

  // ── Input mode (no HTTP request) ─────────────────────────────────────────

  if (options.input) {
    let content;
    try {
      content = await readInputContent(options.input);
    } catch (err) {
      log.error(`Could not read input: ${err.message}`);
      process.exitCode = 1;
      return;
    }

    if (options.raw) {
      // Pass-through: just print the raw content unchanged.
      console.log(content);
      return;
    }

    let parsed;
    try {
      parsed = parseBody(content);
    } catch (err) {
      log.error(`Content is not valid JSON or JSONL: ${err.message}`);
      process.exitCode = 1;
      return;
    }

    const label = options.input === '-' ? 'stdin' : options.input;
    log.info(`Schema for ${label}:`);
    console.log(JSON.stringify({ schema: removeRequired(inferSchema(parsed)) }, null, 2));
    return;
  }

  // ── Endpoint mode (HTTP request) ─────────────────────────────────────────

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

      // Redirects (3xx) never carry a parseable schema — show the Location so
      // the caller knows where to go instead.
      if (result.status >= 300 && result.status < 400) {
        const location = result.headers['location'];
        if (location) {
          log.warn(`Redirect location: ${location}`);
        }
        log.warn('This tool does not follow redirects. Re-run with the redirect URL.');
      }

      // Always print response headers for non-2xx so callers can diagnose the
      // failure (e.g. WWW-Authenticate on 401, Retry-After on 429, Location on 3xx).
      console.error('Response headers:');
      console.error(JSON.stringify(result.headers, null, 2));

      if (result.body) {
        console.error('Response body:');
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
