import { execSync } from 'child_process';
import aws4 from 'aws4';
import fetch from 'node-fetch';
import { getCommonHTTPHeaders } from './utils.js';

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
  const result = execSync(command).toString();
  return JSON.parse(result);
}

/**
 * Fetch AWS endpoint
 *
 * @param {Object} options - see `../index.js`
 * @param {Object} credentials
 * @returns {Promise}
 */
async function fetchAWS(options = {}, credentials = {}) {
  const awsURL = new URL(options.url);
  console.log('Calling psoxy...');
  console.log(`Request: ${options.url}`);

  // aws4 is not able to parse a full URL:
  // https://[id].lambda-url.us-east-1.on.aws/
  // the result is missing `service` and `region` which are mandatory
  const signed = aws4.sign(
    {
      host: awsURL.host,
      path: awsURL.pathname,
      service: 'lambda',
      region: awsURL.host.split('.')[2],
    },
    {
      accessKeyId: credentials?.Credentials.AccessKeyId,
      secretAccessKey: credentials?.Credentials.SecretAccessKey,
      sessionToken: credentials?.Credentials.SessionToken,
    }
  );

  const headers = {
    ...getCommonHTTPHeaders(options),
    ...signed.headers,
  };

  console.log('Waiting response...');

  if (options.verbose) {
    console.log('Options:');
    console.log(options);
    console.log('Request headers:');
    console.log(headers);
  }

  return await fetch(options.url, { headers: headers });
}

/**
 * Helper: check url deploy type
 *
 * @param {String|URL} url
 * @return {boolean}
 */
function isAWS(url) {
  if (typeof url === 'string') {
    url = new URL(url);
  }
  return url.hostname.endsWith('.on.aws');
}

/**
 * Psoxy test
 *
 * @param {Object} options
 * @returns {Promise}
 */
async function test(options = {}) {
  if (!options.role) {
    throw new Error('Role is a required option for AWS');
  }
  const credentials = assumeRole(options.role);
  return await fetchAWS(options, credentials);
}

export default {
  isAWS: isAWS,
  test: test,
};
