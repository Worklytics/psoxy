import { 
  fetch,
  getCommonHTTPHeaders, 
  signAWSRequestURL, 
  executeCommand 
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
  console.log(`Assuming role ${role}`);
  // one-liner for simplicity
  const command = `aws sts assume-role --role-arn ${role} --duration 900 --role-session-name lambda_test`;
  return JSON.parse(executeCommand(command));
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

  const url = new URL(options.url);
 
  console.log(`Assuming role ${options.role}`);
  const credentials = assumeRole(options.role);
  
  console.log(`Signing request: ${options.url}`);
  const signed = signAWSRequestURL(url, credentials);

  const headers = {
    ...getCommonHTTPHeaders(options),
    ...signed.headers,
  };

  console.log('Calling psoxy...');
  console.log('Waiting response...');

  if (options.verbose) {
    console.log('Options:');
    console.log(options);
    console.log('Request headers:');
    console.log(headers);
  }

  return await fetch(url.toString(), { headers: headers });
}

export default {
  isValidURL: isValidURL,
  call: call,
};
