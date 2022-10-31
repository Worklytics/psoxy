import {
  request,
  getCommonHTTPHeaders,
  signAWSRequestURL,
  executeCommand,
  resolveHTTPMethod
} from './utils.js';
import getLogger from './logger.js';

/**
 * Call AWS cli to get temporary security credentials.
 *
 * Refs:
 * - https://awscli.amazonaws.com/v2/documentation/api/latest/reference/sts/assume-role.html
 * - https://docs.aws.amazon.com/IAM/latest/UserGuide/reference_identifiers.html#identifiers-arns
 *
 * @param {String} role AWS IAM ARN format
 * @returns {Object} AWS credentials for role
 */
function assumeRole(role) {
  // one-liner for simplicity
  const command = `aws sts assume-role --role-arn ${role} --duration 900 --role-session-name lambda_test`;
  return JSON.parse(executeCommand(command)).Credentials;
}

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
  return url.hostname.endsWith('.on.aws');
}

/**
 * Psoxy test
 *
 * @param {Object} options - see `../index.js`
 * @returns {Promise}
 */
async function call(options = {}) {
  const logger = getLogger(options.verbose);

  if (!options.role) {
    throw new Error('Role is a required option for AWS');
  }

  logger.verbose(`Assuming role ${options.role}`);
  let credentials;
  try {
    credentials = assumeRole(options.role);
  } catch (error) {
    throw new Error(`Unable to assume ${options.role}`, { cause: error });
  }

  const url = new URL(options.url);
  const method = options.method || resolveHTTPMethod(url.pathname);

  logger.verbose('Signing request');

  const signed = signAWSRequestURL(url, method, credentials);
  const headers = {
    ...getCommonHTTPHeaders(options),
    ...signed.headers,
  };

  logger.info(`Calling Psoxy and waiting response: ${options.url.toString()}`);
  logger.verbose('Request Options:', { additional: options });
  logger.verbose('Request Headers: ', { additional: headers })

  return await request(url, method, headers);
}

export default {
  isValidURL: isValidURL,
  call: call,
};
