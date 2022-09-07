import psoxyTest from '../index.js';
import chalk from 'chalk';
import dotenv from 'dotenv';
import spec from './spec.js';
import { transformSpecWithResponse } from '../lib/utils.js';
dotenv.config();

const base = process.env.PSOXY_URL_GDRIVE//process.env.PSOXY_URL;
const options = {
  role: process.env.PSOXY_ROLE,
  token: process.env.PSOXY_TOKEN,
  impersonate: process.env.PSOXY_IMPERSONATE,
  saveToFile: process.env.PSOXY_SAVE_TO_FILE === 'true',
  verbose: process.env.PSOXY_VERBOSE === 'true',
};

(async function() {
  const dataSource = spec[process.argv.slice(2)[0]];
  if (!dataSource) {
    console.error(chalk.bold.red('Unknown data source'));
    return;
  }

  for (const endpoint of dataSource.endpoints) {
    let result;
    let paramsString = '';
    if (endpoint.params) {
      const params = new URLSearchParams();
      for(const [key, value] of Object.entries(endpoint.params)) {
        params.append(key, value);
      }
      paramsString = `?${params.toString()}`;
    }

    const url = base + endpoint.path + paramsString;
    console.log(`${chalk.blue(dataSource.name)}, fetching ${chalk.blue(endpoint.name)}: ${url}`);
    result = await psoxyTest({
      ...options,
      url: url,
    })

    if (result?.error) {
      console.error(chalk.bold.red(result.error));
    } else if (result.status === 200) {
      console.log(chalk.green('OK'));
      console.log(result.data);
    }

    if (endpoint.refs) {
      transformSpecWithResponse(dataSource, result.data);
    }
  }
})();
