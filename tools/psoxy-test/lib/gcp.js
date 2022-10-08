import { getCommonHTTPHeaders, fetch, executeCommand } from './utils.js';

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
  if (!options.token) {
    options.token = getIdentityToken();
  }

  const headers = {
    ...getCommonHTTPHeaders(options),
    Authorization: `Bearer ${options.token}`,
  };

  console.log('Calling psoxy...');
  console.log(`Request: ${options.url}`);
  console.log('Waiting response...');

  if (options.verbose) {
    console.log('Options:');
    console.log(options);
    console.log('Request headers:');
    console.log(headers);
  }

  return await fetch(options.url, { headers: headers });
}

export default {
  isValidURL: isValidURL,
  getIdentityToken: getIdentityToken,
  call: call,
};
