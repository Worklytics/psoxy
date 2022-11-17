import { getCommonHTTPHeaders, request, executeCommand, resolveHTTPMethod } from './utils.js';
import { Logging } from '@google-cloud/logging';
import getLogger from './logger.js';
import _ from 'lodash';

/**
 * Helper: check url deploy type
 *
 * @param {String|URL} url
 * @return {boolean}
 */
function isValidURL(url) {
  if (typeof url === 'string') {
    url = new URL(url);
  }
  return url.hostname.endsWith('cloudfunctions.net');
}

/**
 * Get identity token for current gcloud account.
 *
 * Refs:
 * - https://cloud.google.com/sdk/gcloud/reference/auth/login
 * - https://cloud.google.com/sdk/gcloud/reference/auth/print-identity-token
 *
 * @returns {String} identity token
 */
function getIdentityToken() {
  const command = 'gcloud auth print-identity-token';
  return executeCommand(command).trim();
}

/**
 * Psoxy test
 *
 * @param {Object} options - see `../index.js`
 * @returns {Promise}
 */
async function call(options = {}) {
  const logger = getLogger(options.verbose);
  if (!options.token) {
    logger.verbose('Getting Google Cloud identity token');
    options.token = getIdentityToken();
  }

  const headers = {
    ...getCommonHTTPHeaders(options),
    Authorization: `Bearer ${options.token}`,
  };

  logger.info(`Calling Psoxy and waiting response: ${options.url}`);
  logger.verbose('Request Options:', { additional: options });
  logger.verbose('Request Headers:', { additional: headers });

  const url = new URL(options.url);
  const method = options.method || resolveHTTPMethod(url.pathname);

  return await request(url, method, headers);
}

/**
 * Google Cloud Logging: get logs for a cloud function
 * Refs:
 * - https://cloud.google.com/functions/docs/monitoring/logging#using_the_logging_api
 * - resource names and filter format: https://cloud.google.com/logging/docs/reference/v2/rest/v2/entries/list
 * - https://googleapis.dev/nodejs/logging/latest/Entry.html
 * - https://cloud.google.com/logging/docs/structured-logging
 * 
 * @param {object} options - see `psoxy-test-logs.js`
 * @param {string} options.projectId
 * @param {string} options.functionName
 * @return {Array<Object>} - array of serialized log entries
 */
async function getLogs(options = {}) {
  const logging = new Logging();
  const log = logging.log('cloudfunctions.googleapis.com%2Fcloud-functions');
  const [entries] = await log.getEntries({
    filter: `resource.labels.function_name=${options.functionName}`,
    resourceNames: [`projects/${options.projectId}`],
  });
  return entries.map(entry => entry.toStructuredJSON());
}

/**
 * Parse GCP log entries and return a simplre format for our use-case:
 * display: timestamp, message, and severity
 * 
 * @param {Array<Object>} entries 
 * @returns {Array<Object>}
 */
function parseLogEntries(entries) {
  if (!Array.isArray(entries) || entries.length === 0) {
    return [];
  }

  // https://cloud.google.com/logging/docs/reference/v2/rest/v2/LogEntry#LogSeverity
  const LOG_LEVELS = ['WARNING', 'ERROR', 'CRITICAL', 'ALERT', 'EMERGENCY'];
  const dateRegex = /^\d{4}-\d{2}-\d{2} \d{2}:\d{2}:\d{2}.\d{3}/
  return entries.map(entry => {
    const result = {
      timestamp: new Date(entry.timestamp.seconds * 1000).toISOString(),
    }

    let message = entry.message;
    if (_.isObject(message)) {
      message = message.message;
    }
    result.message = _.isString(message) ? 
      message.replace(dateRegex, '') : JSON.stringify(message);

    if (LOG_LEVELS.find(level => entry.severity === level)) {
      result.level = entry.severity;
    }

    return result;
  });
}

export default {
  call,
  getIdentityToken,
  getLogs,
  isValidURL,
  parseLogEntries,
};
