import psoxyTestCall from '../psoxy-test-call.js';
import chalk from 'chalk';
import _ from 'lodash';
import spec from './spec.js';
import { transformSpecWithResponse } from '../lib/utils.js';
import getLogger from '../lib/logger.js';

const DEFAULT_MAX_PAGES = 3;
const DEFAULT_PAGE_SIZE = 1;

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
 * Merge the `value` arrays of every fetched page into a single object, so an
 * accessor like `value[0].id` still resolves even when an early page comes
 * back with an empty `value` but a valid `@odata.nextLink` (a known OData
 * paging quirk) - otherwise refs chaining would only ever look at page 1.
 *
 * @param {Object[]} pages - results from psoxyTestCall, one per page
 * @returns {Object}
 */
function mergePagesForRefs(pages) {
  if (pages.length <= 1) {
    return pages[0]?.data;
  }
  return pages.reduce((merged, page) => {
    const value = page?.data?.value;
    if (Array.isArray(value)) {
      merged.value = (merged.value || []).concat(value);
    }
    return merged;
  }, { ...pages[0]?.data });
}

/**
 * Run multiple psoxy test calls depending on options.dataSource spec
 *
 * @param {Object} options - see `../psoxy-test-call.js`
 * @param {boolean} [options.paginate] - opt-in: for endpoints with `pagination: true`, add
 *   `$top` and follow `@odata.nextLink`. Off by default - endpoints run as a single call.
 * @param {number} [options.pageSize] - with `paginate`, page size ($top) requested per call (default 1)
 * @param {number} [options.maxPages] - with `paginate`, max pages to follow per endpoint (default 3)
 * @returns {Object}
 */
async function callDataSourceEndpoints(options) {
  const logger = getLogger(options.verbose);
  const paginate = !!options.paginate;
  const pageSize = options.pageSize ?? DEFAULT_PAGE_SIZE;
  const maxPages = options.maxPages ?? DEFAULT_MAX_PAGES;

  const dataSourceSpec = spec[options.dataSource];
  if (!dataSourceSpec) {
    throw new Error(`Unknown data source: ${options.dataSource}`);
  }

  // Pagination mechanics (page-size query param, next-page cursor field) vary per API
  // family, so they're declared on the data source itself, not assumed by the runner.
  const pageSizeParam = dataSourceSpec.pageSizeParam;
  const nextLinkAccessor = dataSourceSpec.nextLinkAccessor;

  const results = {};

  for (const endpoint of dataSourceSpec.endpoints) {
    const shouldPaginate = paginate && !!endpoint.pagination;

    const params = { ...endpoint.params };
    if (shouldPaginate && pageSizeParam) {
      params[pageSizeParam] = pageSize;
    }

    let paramsString = '';
    if (Object.keys(params).length > 0) {
      const urlParams = new URLSearchParams();
      for (const [key, value] of Object.entries(params)) {
        urlParams.append(key, value);
      }
      paramsString = `?${urlParams.toString()}`;
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

      const nextLink = (shouldPaginate && nextLinkAccessor)
        ? _.get(pageResult.data, nextLinkAccessor)
        : undefined;
      currentUrl = nextLink ? nextLinkToProxyUrl(nextLink, options.url) : undefined;

      if (nextLink) {
        if (pageCount < maxPages) {
          logger.info(`${chalk.blue(dataSourceSpec.name)}, ${endpoint.name} endpoint -> following ${nextLinkAccessor} (page ${pageCount + 1}/${maxPages})`);
        } else {
          logger.info(`${chalk.blue(dataSourceSpec.name)}, ${endpoint.name} endpoint -> ${nextLinkAccessor} present but max pages (${maxPages}) reached, stopping`);
        }
      }
    } while (currentUrl && pageCount < maxPages);

    const result = pages[0];
    results[endpoint.name] = pages.length > 1 ? { ...result, pages } : result;

    if (endpoint.refs) {
      transformSpecWithResponse(endpoint.name, mergePagesForRefs(pages), dataSourceSpec);
    }
  }

  return results;
}

export { callDataSourceEndpoints };
