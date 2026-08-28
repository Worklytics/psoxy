import psoxyTestCall from '../psoxy-test-call.js';
import chalk from 'chalk';
import _ from 'lodash';
import spec from './spec.js';
import { transformSpecWithResponse } from '../lib/utils.js';
import getLogger from '../lib/logger.js';

const DEFAULT_MAX_PAGES = 3;
// Microsoft Graph pagination cursor; endpoints opt-in via `pagination: true` in spec.js
const NEXT_LINK_ACCESSOR = '@odata.nextLink';

/**
 * A `@odata.nextLink` is an *absolute* graph.microsoft.com URL. To keep
 * following pagination through the Psoxy proxy (rather than calling Graph
 * directly), swap its host for the proxy's base URL and keep its path/query.
 *
 * @param {string} nextLink
 * @param {string} baseUrl - Psoxy deploy base URL (no API path)
 * @returns {string}
 */
function nextLinkToProxyUrl(nextLink, baseUrl) {
  const { pathname, search } = new URL(nextLink);
  return `${baseUrl.replace(/\/$/, '')}${pathname}${search}`;
}

/**
 * Run multiple psoxy test calls depending on options.dataSource spec
 *
 * @param {Object} options - see `../psoxy-test-call.js`
 * @param {number} [options.maxPages] - max pages to follow for endpoints with `pagination: true` (default 3)
 * @returns {Object}
 */
async function callDataSourceEndpoints(options) {
  const logger = getLogger(options.verbose);
  const maxPages = options.maxPages ?? DEFAULT_MAX_PAGES;

  const dataSourceSpec = spec[options.dataSource];
  if (!dataSourceSpec) {
    throw new Error(`Unknown data source: ${options.dataSource}`);
  }

  const results = {};

  for (const endpoint of dataSourceSpec.endpoints) {
    let paramsString = '';
    if (endpoint.params) {
      const params = new URLSearchParams();
      for (const [key, value] of Object.entries(endpoint.params)) {
        params.append(key, value);
      }
      paramsString = `?${params.toString()}`;
    }

    const url = options.url + endpoint.path + paramsString;
    logger.info(`${chalk.blue(dataSourceSpec.name)}, ${endpoint.name} endpoint -> ${endpoint.path}${paramsString}`);

    const pages = [];
    let currentUrl = url;
    let pageCount = 0;

    do {
      const pageResult = await psoxyTestCall({ ...options, url: currentUrl });
      pages.push(pageResult);
      pageCount++;

      const nextLink = endpoint.pagination
        ? _.get(pageResult.data, NEXT_LINK_ACCESSOR)
        : undefined;
      currentUrl = nextLink ? nextLinkToProxyUrl(nextLink, options.url) : undefined;

      if (nextLink) {
        if (pageCount < maxPages) {
          logger.info(`${chalk.blue(dataSourceSpec.name)}, ${endpoint.name} endpoint -> following ${NEXT_LINK_ACCESSOR} (page ${pageCount + 1}/${maxPages})`);
        } else {
          logger.info(`${chalk.blue(dataSourceSpec.name)}, ${endpoint.name} endpoint -> ${NEXT_LINK_ACCESSOR} present but max pages (${maxPages}) reached, stopping`);
        }
      }
    } while (currentUrl && pageCount < maxPages);

    // refs chaining always keys off the first page's data
    const result = pages[0];
    results[endpoint.name] = pages.length > 1 ? { ...result, pages } : result;

    if (endpoint.refs) {
      transformSpecWithResponse(endpoint.name, result.data, dataSourceSpec);
    }
  }

  return results;
}

export { callDataSourceEndpoints };
