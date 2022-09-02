import { promises as fs } from 'fs';
import path from 'path';
import { fileURLToPath } from 'url';

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
    'Accept-encoding': options.gzip ? 'gzip' : 'none',
    'X-Psoxy-Skip-Sanitizer': options.skip?.toString() || 'false',
  }
  if (options.impersonate) {
    headers['X-Psoxy-User-To-Impersonate'] = options.impersonate;
  }
  return headers;
}

export {
  saveRequestResultToFile,
  getCommonHTTPHeaders,
};