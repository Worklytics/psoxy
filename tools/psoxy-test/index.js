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
 * @param {boolean} options.file - Whether to save successful responses to a file (responses/[api-path]-[ISO8601 timestamp].json)
 * @return {PsoxyResponse}
 */
export default async function (options = {}) {
  const result = {};
  let url;

  try {
    url = new URL(options.url);
  } catch (err) {
    result.error = `"${options.url}" is not a valid URL`;
    return result;
  }

  const isAWS = aws.isAWS(url);
  const isGCP = gcp.isGCP(url);
  let test;

  if (options.force && ['aws', 'gcp'].includes(options.force.toLowerCase())) {
    test = options.force === 'aws' ? aws.test : gcp.test;
  } else if (!isAWS && !isGCP) {
    result.error = `"${options.url}" doesn't seem to be a valid endpoint: AWS or GCP`;
    return result;
  } else {
    test = isAWS ? aws.test : gcp.test;
  }

  try {
    const response = await test(options);
    
    if (response.status === 200) {
      const data = await response.json();

      result.response = {
        status: response.status,
        data: data,
      };

      if (options.file) {
        try {
          await saveRequestResultToFile(url, data);
          console.log('Results saved to file');
        } catch (err) {
          console.error('Unable to save results', err);
        }        
      }

    } else {
      const psoxyError = response.headers.get('x-psoxy-error');

      if (psoxyError) {
        switch (psoxyError) {
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
          default:
            result.error = psoxyError;
        }
      } else {
        result.error = response.statusText;
      }
    }
  } catch (err) {
    result.error = err.message;
  }

  return result;
}
