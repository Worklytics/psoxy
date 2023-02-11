import { execSync } from 'child_process';
import { fileURLToPath } from 'url';
import { promises as fs } from 'fs';
import aws4 from 'aws4';
import https from 'https';
import path from 'path';
import _ from 'lodash';
import spec from '../data-sources/spec.js';
import { constants as httpCodes } from 'http2';

const __dirname = path.dirname(fileURLToPath(import.meta.url));

/**
 * Save data to file.
 *
 * @param {string} dirname
 * @param {string} filename
 * @param {string} data
 */
async function saveToFile(dirname = __dirname, filename, data) {
  let dirExists = false;

  try {
    await fs.access(dirname);
    dirExists = true;
  } catch {}

  if (!dirExists) {
    await fs.mkdir(dirname);
  }

  return fs.writeFile(`${dirname}/${filename}`, data, 'utf-8');
}

/**
 * Get a file name from a URL object; default: replaces `/` by `-`, example:
 * `[psoxy-function]-[api-path]-[ISO8601 timestamp].json`
 * `psoxy-gcal-calendar-v3-calendars-primary-2022-09-02T10:15:00.000Z.json`
 * @param {URL} url
 * @param {boolean} timestamp
 * @param {string} extension
 * @return {string}
 */
function getFileNameFromURL(url, timestamp = true, extension = '.json') {
  // Strip out 1st '/'
  const pathPart = url.pathname.substring(1).replaceAll('/', '-');
  const timestampPart = timestamp ? `-${new Date().toISOString()}` : '';
  return pathPart + timestampPart + extension;
}

/**
 * Get common HTTP request headers for Psoxy requests.
 *
 * @param {Object} options - see `../index.js`
 */
function getCommonHTTPHeaders(options = {}) {
  const headers = {
    'Accept-Encoding': options.gzip ? 'gzip' : '*',
    'X-Psoxy-Skip-Sanitizer': options.skip?.toString() || 'false',
  };
  if (options.impersonate) {
    headers['X-Psoxy-User-To-Impersonate'] = options.impersonate;
  }
  if (options.healthCheck) {
    // option presence is enough, since Psoxy doesn't check header value
    headers['X-Psoxy-Health-Check'] = 'true';
  }

  return headers;
}

/**
 * Wrapper for requests using Node.js HTTP interfaces: focused on
 * Psoxy use-case (*)
 *
 * @param {String|URL} url
 * @param {Object} headers
 * @param {String} method
 * @return {Promise}
 */
function requestWrapper(url, method = 'GET', headers) {
  url = typeof url === 'string' ? new URL(url) : url;
  const params = url.searchParams.toString();

  return new Promise((resolve, reject) => {
    const req = https.request(
      {
        hostname: url.host,
        port: 443,
        path: url.pathname + (params !== '' ? `?${params}` : ''),
        method: method,
        headers: headers,
      },
      (res) => {
        const result = {
          status: res.statusCode,
          statusMessage: res.statusMessage,
          headers: res.headers,
        };

        if (res.statusCode !== httpCodes.HTTP_STATUS_OK) {
          resolve(result);
        } else {
          let responseData = '';
          res.on('data', (data) => (responseData += data));
          res.on('end', () => {
            resolve({...result, data: responseData});
          });
        }
      }
    );
    req.on('error', (error) => {
      reject({ status: error.code, statusMessage: error.message });
    });
    req.end();
  });
}

/**
 * Simple wrapper around `aws4` to ease testing.
 *
 * Ref: https://github.com/mhart/aws4#api
 *
 * @param {URL} url
 * @param {String} method
 * @param {Object} credentials
 * @return {Object}
 */
function signAWSRequestURL(url, method = 'GET', credentials) {
  // According to aws4 docs, search params should be part of the "path"
  const params = url.searchParams.toString();

  const requestOptions = {
    host: url.host,
    path: url.pathname + (params !== '' ? `?${params}` : ''),
    method: method,
  };

  // Closer look at aws4 source code: region and service are calculated from
  // URL's host, but for Lambda functions it doesn't translate the URL part
  // to the name of the service; API Gateway use-case works OK
  const serviceHostPart = url.host.split('.')[1];
  if (serviceHostPart === 'lambda-url') {
    requestOptions.service = 'lambda';
  } else {
    requestOptions.service = serviceHostPart;
  }

  return aws4.sign(requestOptions, {
    accessKeyId: credentials?.AccessKeyId,
    secretAccessKey: credentials?.SecretAccessKey,
    sessionToken: credentials?.SessionToken,
  });
}

/**
 * Simple wrapper around node's `execSync` to ease testing.
 *
 * @param {string} command
 * @return {string}
 */
function executeCommand(command) {
  return execSync(command).toString();
}

/**
 * Transform endpoint's path and params based on previous calls responses
 *
 * @param {Object} spec - see `../data-sources/spec.js`
 * @param {Object} res - data source API response
 * @returns
 */
function transformSpecWithResponse(spec = {}, res = {}) {
  (spec?.endpoints || [])
    .filter((endpoint) => endpoint.refs !== undefined)
    .forEach((endpoint) => {
      endpoint.refs.forEach((ref) => {
        const target = spec.endpoints.find((endpoint) => endpoint.name === ref.name);

        if (target && ref.accessor) {
          const valueReplacement = _.get(res, ref.accessor);

          if (valueReplacement) {
            // 2 possible replacements: path or param
            if (ref.pathReplacement) {
              target.path = target.path.replace(ref.pathReplacement, valueReplacement);
            }

            if (ref.paramReplacement) {
              target.params[ref.paramReplacement] = valueReplacement;
            }
          }
        }
      });
    });
  return spec;
}

/**
 * Resolve HTTP method based on known API paths (defined in spec module)
 *
 * @param {string} path - path to inspect
 * @returns {string}
 */
function resolveHTTPMethod(path = '') {
  const endpointMatch = Object.values(spec)
    .reduce((acc, value) => acc.concat(value.endpoints), [])
    .find(endpoint => endpoint.path === path);
  return endpointMatch?.method || 'GET';
}

/**
 * "retry" helper function, execute "fn" until returning value is not undefined
 *
 * @param {Function} fn
 * @param {Function} onErrorStop
 * @param {Object} logger - winston instance
 * @param {number} maxAttempts
 * @param {number} delay
 * @param {string} progressMessage
 * @returns
 */
async function executeWithRetry(fn, onErrorStop, logger, maxAttempts = 60,
  delay = 1000, progressMessage = 'Downloading...') {

  let result;
  let attempts = 0;
  while(_.isUndefined(result) && attempts <= maxAttempts) {
    try {
      result = await fn();
    } catch (error) {
      if (onErrorStop(error)) {
        throw error;
      }
    }
    attempts++;
    if (_.isUndefined(result)) {
      // Wait "delay" ms before retry;
      // Psoxy bulk use-case: in theory, only if this is the first
      // operation  after Psoxy deployment, we'd need a max of 60' until it
      // processes the file and puts it in the output bucket
      clearTimeout(await new Promise(resolve => setTimeout(resolve, delay)));

      if (logger) {
        logger.info(progressMessage);
      }
    }
  }

  return result;
}

export {
  executeCommand,
  executeWithRetry,
  getCommonHTTPHeaders,
  getFileNameFromURL,
  requestWrapper as request,
  saveToFile,
  signAWSRequestURL,
  resolveHTTPMethod,
  transformSpecWithResponse,
};
