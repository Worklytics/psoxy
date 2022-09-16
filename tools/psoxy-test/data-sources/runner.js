import psoxyTest from '../index.js';
import chalk from 'chalk';
import spec from './spec.js';
import { transformSpecWithResponse } from '../lib/utils.js';

/**
 * Run multiple psoxy test calls depending on options.dataSource spec
 *
 * @param {Object} options - see `../index.js`
 * @returns {Object}
 */
async function callDataSourceEndpoints(options) {
  const dataSourceSpec = spec[options.dataSource];
  if (!dataSourceSpec) {
    console.error(chalk.bold.red('Unknown data source'));
    return;
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
    console.log(`
      ${chalk.blue(dataSourceSpec.name)}, fetching ${chalk.blue(endpoint.name)}: ${url}
    `);

    const result = await psoxyTest({
      ...options,
      url: url,
    });
    results[endpoint] = result;

    if (endpoint.refs) {
      transformSpecWithResponse(dataSourceSpec, result.data);
    }
  }

  return results;
}

export { callDataSourceEndpoints };
