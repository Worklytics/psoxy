import { 
  request,
  getCommonHTTPHeaders, 
  signAWSRequestURL, 
  executeCommand, 
  resolveHTTPMethod
} from './utils.js';

/**
 * Call AWS cli to get temporary security credentials.
 *
 * Refs:
 * - https://docs.aws.amazon.com/cli/latest/reference/sts/assume-role.html
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
  if (!options.role) {
    throw new Error('Role is a required option for AWS');
  }
 
  console.log(`Assuming role ${options.role}`);
  let credentials;
  try {
    credentials = assumeRole(options.role);
  } catch (err) {
    throw new Error(`Unable to assume ${options.role}: ${err}`);
  }
  
  
  console.log('Signing request');

  const url = new URL(options.url);
  const method = options.method || resolveHTTPMethod(url.pathname);
  const signed = signAWSRequestURL(url, method, credentials);
  const headers = {
    ...getCommonHTTPHeaders(options),
    ...signed.headers,
  };

  console.log('Calling psoxy and waiting response...');

  if (options.verbose) {
    console.log('Request options:', options);
    console.log('Request headers:', headers);
  }

  return await request(url, method, headers);
}

export default {
  isValidURL: isValidURL,
  call: call,
};
