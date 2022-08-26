/**
 * Helper: check url deploy type
 * 
 * @param {String|URL} url 
 * @return {boolean}
 */
 function isGCP(url) {
  if (typeof url === 'string') {
    url = new URL(url);
  }
  return url.hostname.endsWith('cloudfunctions.net');
}

/**
 * Psoxy test
 * 
 * @param {Object} options 
 * @returns {Promise}
 */
async function test(options = {}) {
  if (!options.token) {
    throw new Error('Authorization token is required for GCP endpoints');
  }

  const headers = {
    '-Haccept-encoding': options.gzip ? 'gzip' : 'none',
    '-HX-Psoxy-Skip-Sanitizer': options.skip.toString(),
    '-HAuthorization': `Bearer ${options.token}`,
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

  return await fetch(options.url, {headers: headers});
}

export default {
  isGCP: isGCP,
  test: test,
}