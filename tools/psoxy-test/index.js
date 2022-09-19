import chalk from 'chalk';
import aws from './lib/aws.js';
import gcp from './lib/gcp.js';
import { saveRequestResultToFile } from './lib/utils.js';

/**
 * @typedef {Object} PsoxyResponse
 * @property {string} error - Error message if any
 * @property {number} status - HTTP request status code
 * @property {Object} data - HTTP request body (JSON)
 */

/**
 * Psoxy test call
 *
 * @param {Object} options
 * @param {string} options.url - Psoxy URL to call
 * @param {string} options.force - Force URL as AWS or GCP deploy
 * @param {string} options.impersonate - User to impersonate (Google Workspace API)
 * @param {string} options.token - Authorization token for GCP deploys
 * @param {string} options.role - AWS role to assume when calling the Psoxy (ARN format)
 * @param {boolean} options.skip - Whether to skip or not sanitization rules (only in DEV mode)
 * @param {boolean} options.gzip - Add Gzip compression headers
 * @param {boolean} options.verbose - Verbose ouput (default to console)
 * @param {boolean} options.saveToFile - Whether to save successful responses to a file (responses/[api-path]-[ISO8601 timestamp].json)
 * @return {PsoxyResponse}
 */
export default async function (options = {}) {
  let result = {};
  let url;

  try {
    url = new URL(options.url);
  } catch (err) {
    result.error = `"${options.url}" is not a valid URL`;
    return result;
  }

  const isAWS = aws.isValidURL(url);
  const isGCP = gcp.isValidURL(url);
  let psoxyCall;

  if (options.force && ['aws', 'gcp'].includes(options.force.toLowerCase())) {
    psoxyCall = options.force === 'aws' ? aws.call : gcp.call;
  } else if (!isAWS && !isGCP) {
    result.error = `"${options.url}" doesn't seem to be a valid endpoint: AWS or GCP`;
    return result;
  } else {
    psoxyCall = isAWS ? aws.call : gcp.call;
  }

  try {
    result = await psoxyCall(options);
    
    if (result.status === 200) {
      if (options.saveToFile) {
        try {
          await saveRequestResultToFile(url, result.data);
          console.log(chalk.green('Results saved to file'));
        } catch (err) {
          console.error(chalk.red('Unable to save results to file'), err);
        }        
      }

    } else {
      switch (result.error) {
        case 'BLOCKED_BY_RULES':
          result.error = 'Blocked by rules error: make sure URL path is correct';
          break;
        case 'CONNECTION_SETUP':
          result.error =
            'Connection setup error: make sure the data source is properly configured';
          break;
        case 'API_ERROR':
          result.error = 'API error: call to data source failed';
          break;
      }
    }
  } catch (err) {
    result.error = err.message;
  }

  if (result?.error) {
    console.error(chalk.bold.red(`${result.status}: ERROR`));
    console.error(chalk.bold.red(result.error));
  } else if (result.status === 200) {
    console.log(chalk.bold.green(`${result.status}: OK`));
    console.log(result.data);
  }

  if (options.verbose) {
    console.log(`Response headers:\n ${result.headers}`);
  }

  return result;
}
