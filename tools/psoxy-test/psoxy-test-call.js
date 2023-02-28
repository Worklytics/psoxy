import chalk from 'chalk';
import aws from './lib/aws.js';
import gcp from './lib/gcp.js';
import getLogger from './lib/logger.js';
import path from 'path';
import _ from 'lodash';
import { constants as httpCodes } from 'http2';
import { fileURLToPath } from 'url';
import { saveToFile, getFileNameFromURL } from './lib/utils.js';

// Since we're using ESM modules, we need to make `__dirname` available
const __dirname = path.dirname(fileURLToPath(import.meta.url));

/**
 * @typedef {Object} PsoxyResponse
 * @property {string} statusMessage - Error message if any
 * @property {number} status - HTTP request status code
 * @property {Object} headers - HTTP response headers
 * @property {Object} data - HTTP request body
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
 * @param {boolean} options.verbose - Verbose ouput
 * @param {boolean} options.saveToFile - Whether to save successful responses to a file (responses/[api-path]-[ISO8601 timestamp].json)
 * @param {string} options.method - HTTP request method
 * @param {boolean} options.healthCheck - Run "Health Check" call against Psoxy deploy
 * @return {PsoxyResponse}
 */
export default async function (options = {}) {
  const logger = getLogger(options.verbose);
  let result = {};
  let url;

  try {
    url = new URL(options.url);
  } catch (error) {
    throw new Error(`"${error.input}" is not a valid URL`, { cause: error });
  }

  const isAWS = aws.isValidURL(url);
  const isGCP = gcp.isValidURL(url);
  let psoxyCall;

  if (!_.isEmpty(options.force)) {
    const psoxyCallMethods = {
      'aws': aws.call,
      'gcp': gcp.call,
    }
    psoxyCall = psoxyCallMethods[options.force.toLowerCase()];
  } else if (isAWS) {
    psoxyCall = aws.call;
  } else if (isGCP) {
    psoxyCall = gcp.call;
  }

  if (_.isUndefined(psoxyCall)) {
    const message = `"${options.url}" doesn't seem to be a valid endpoint: AWS or GCP`;
    const tip = 'Use "-f" option if you\'re certain it\'s a valid deploy';
    throw new Error(`${message}\n${tip}`);
  }

  result = await psoxyCall(options);

  if (result.status === httpCodes.HTTP_STATUS_OK) {
    let successMessagePrefix = options.healthCheck ? 'Health Check result:' :
      'Call result:'
    let jsonCallResult;

    if (!_.isEmpty(result.data)) {

      try {
        jsonCallResult = JSON.parse(result.data)
      } catch(error) {
        logger.error(error);
      }

      if (options.saveToFile) {
        const filename = getFileNameFromURL(url);
        await saveToFile(__dirname, filename, JSON.stringify(jsonCallResult, undefined, 2));
        logger.success(`Results saved to: ${__dirname}/${filename}`);
      } else {
        // Response potentially long, let's remind to check logs for complete results
        logger.success(`Check out run log to see complete results: ${__dirname}/run.log`);
      }
    }

    logger.success(`${successMessagePrefix} ${result.statusMessage} - ${result.status}`,
      { additional: jsonCallResult });

  } else {
    let errorMessage = result.statusMessage || 'Unknown';

    if (result.headers) {
      const psoxyError = result.headers['x-psoxy-error'];
      // Give more details for WKS errors and try to catch "per deploy" specific
      // errors: although headers are shown in "verbose mode", let's make sure
      // we highlight the main error cause
      if (psoxyError) {
        switch (psoxyError) {
          case 'BLOCKED_BY_RULES':
            errorMessage = 'Blocked by rules error: make sure URL path is correct';
            break;
          case 'CONNECTION_SETUP':
            errorMessage =
              'Connection setup error: make sure the data source is properly configured';
            break;
          case 'API_ERROR':
            errorMessage = 'API error: call to data source failed';
            break;
        }
      } else if (result.headers['x-amzn-errortype']) {
        errorMessage += `: AWS ${result.headers['x-amzn-errortype']}`;
      } else if (result.headers['www-authenticate']) {
        errorMessage += `: GCP ${result.headers['www-authenticate']}`
      }

      logger.verbose(`Response headers:\n ${JSON.stringify(result.headers, null, 2)}`);
    }

    logger.error(`${chalk.bold.red(result.status)}\n${chalk.bold.red(errorMessage)}`);
    if ([httpCodes.HTTP_STATUS_INTERNAL_SERVER_ERROR,
      httpCodes.HTTP_STATUS_BAD_GATEWAY].includes(result.status)) {
      logger.info('This looks like an internal error in the Proxy; please review the logs.')
    }
  }

  return result;
}
