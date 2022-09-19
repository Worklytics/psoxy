import { getCommonHTTPHeaders, fetch } from './utils.js';

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
 * Psoxy test
 *
 * @param {Object} options - see `../index.js`
 * @returns {Promise}
 */
async function call(options = {}) {
  if (!options.token) {
    throw new Error('Authorization token is required for GCP endpoints');
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
  call: call,
};
