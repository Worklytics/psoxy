import { execSync } from 'child_process';
import { fileURLToPath } from 'url';
import { promises as fs } from 'fs';
import aws4 from 'aws4';
import fetch from 'node-fetch';
import path from 'path';
import _ from 'lodash';

// Since we're using ESM modules, we need to make `__dirname` available 
const __dirname = path.dirname(fileURLToPath(import.meta.url));

const RESPONSES_DIR = '/responses';

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
  } catch {};

  if (!dirExists){
    await fs.mkdir(dirname);
  }

  return fs.writeFile(`${dirname}/${filename}`, data, 'utf-8');
};

/**
 * Get a file name from a URL object; default: replaces `/` by `-` 
 * 
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
};

/**
 * Helper to save URL responses to a file. 
 * File names follow this format:
 * `/responses/[psoxy-function]-[api-path]-[ISO8601 timestamp].json`, example:
 * `/responses/psoxy-gcal-calendar-v3-calendars-primary-2022-09-02T10:15:00.000Z.json`
 * 
 * @param {URL} url 
 * @param {Object} data 
 */
async function saveRequestResultToFile(url, data) {
  const filename = getFileNameFromURL(url);
  return saveToFile(path.resolve(__dirname, '..') + RESPONSES_DIR, filename, 
    JSON.stringify(data, undefined, 4));
};

/**
 * Get common HTTP requests for Psoxy requests.
 * 
 * @param {Object} options - see `../index.js`
 */
function getCommonHTTPHeaders(options = {}) {
  const headers = {
    'Accept-Encoding': options.gzip ? 'gzip' : '*',
    'X-Psoxy-Skip-Sanitizer': options.skip?.toString() || 'false',
  }
  if (options.impersonate) {
    headers['X-Psoxy-User-To-Impersonate'] = options.impersonate;
  }
  return headers;
}

/**
 * Simple wrapper around `node-fetch` to ease testing.
 * 
 * @param {URL} url 
 * @param {Object} options 
 * @return {Object}
 */
async function fetchWrapper(url, options) {
  const response = await fetch(url, options);

  const responseHeaders = {};
  response.headers.forEach((key, value) => responseHeaders[key] = value);

  const result = {
    status: response.status,
    headers: JSON.stringify(responseHeaders, undefined, 2),
  };
  
  if (response.status === 200) {
    result.data = await response.json();
  } else {
    result.error = response.headers.get('x-psoxy-error') || response.statusText;
  }

  return result;
}

/**
 * Simple wrapper around `aws4` (*) to ease testing.
 * 
 * (*) aws4 is not able to parse a full URL:
 * https://[id].lambda-url.us-east-1.on.aws/
 * the result is missing `service` and `region` which are mandatory
 * 
 * @param {URL} url 
 * @param {Object} credentials 
 * @return {Object}
 */
function signAWSRequestURL(url, credentials) {
  // According to aws4 docs, search params should be part of the "path"
  const params = url.searchParams.toString();

  return aws4.sign({
    host: url.host,
    path: url.pathname + (params !== '' ? `?${params}` : '') ,
    service: 'lambda',
    region: url.host.split('.')[2],
  },
  {
    accessKeyId: credentials?.Credentials.AccessKeyId,
    secretAccessKey: credentials?.Credentials.SecretAccessKey,
    sessionToken: credentials?.Credentials.SessionToken,
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
    .filter(endpoint => endpoint.refs !== undefined)
    .forEach(endpoint => {
      endpoint.refs.forEach(ref => {
        const target = spec.endpoints
          .find(endpoint => endpoint.name === ref.name);

        if (target && ref.accessor) {
          const valueReplacement = _.get(res, ref.accessor);
          
          if (valueReplacement) {
            // 2 possible replacements: path or param
            if (ref.pathReplacement) {
              target.path = target.path
                .replace(ref.pathReplacement, valueReplacement);
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

export {
  executeCommand,
  fetchWrapper as fetch,
  getCommonHTTPHeaders,
  saveRequestResultToFile,
  signAWSRequestURL,
  transformSpecWithResponse,
};