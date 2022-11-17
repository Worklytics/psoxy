import chalk from 'chalk';
import aws from './lib/aws.js';
import gcp from './lib/gcp.js';
import getLogger from './lib/logger.js';
import _ from 'lodash';

/**
 * Get (and display using logger passed as param) the latest log events from a 
 * log group associated to a Psoxy instance; AWS - CloudWatch
 *
 * @param {Object} options
 * @param {boolean} options.verbose
 * @param {string} options.logGroupName
 * @param {string} options.region
 * @param {string} options.role
 * @param {Object} logger - winston logger instance
 * @returns
 */
async function getAWSLogs(options = {}, logger) {
  if (options.role) {
    logger.verbose(`Assuming role ${options.role}`);
  }
  const client = aws.createCloudWatchClient(options.role, options.region);

  logger.verbose(`Getting logs for ${options.logGroupName}`);

  const logStreamsResult = await aws.getLogStreams(options, client);
  if (logStreamsResult['$metadata'].httpStatusCode !== 200) {
    throw new Error(`Unable to get logs for ${options.logGroupName}`, {
      additional: logStreamsResult,
    });
  }

  let logEventsResult;
  if (logStreamsResult.logStreams.length === 0) {
    logger.info(`${options.logGroupName} seems to be set correctly, but no logs were found`);
    logger.verbose(JSON.stringify(logStreamsResult, undefined, 2));
  } else {
    // Get first log stream found (by `getLogStreams` defaults, it should be the latest)
    const logStreamName = logStreamsResult.logStreams[0].logStreamName;
    logger.info(`Getting log events for stream: ${logStreamName}`);
    logEventsResult = await aws.getLogEvents(
      {
        ...options,
        logStreamName: logStreamName,
      },
      client
    );
    
    if (logEventsResult['$metadata'].httpStatusCode !== 200) {
      throw new Error(`Unable to get log events for stream ${logEventsResult}`, 
        { additional: logEventsResult });
    }

    const events = aws.parseLogEvents(logEventsResult.events);
    if (events.length === 0) {
      logger.info(`No events were found in stream ${logStreamName}`);
    } else {
      logger.success(`Displaying logs for stream ${logStreamName}`);
      events.forEach((event) => {
        let messagePrefix = `${chalk.blue(event.timestamp)}`;
        let message = event.message;
        if (event.level) {
          messagePrefix += `${chalk.bold.red(event.level)}: `;
        } else if (event.highlight) {
          message = chalk.red(message);
        }
        logger.entry(`${messagePrefix}\n${message}`);
      });
    }
  }

  return logEventsResult || logStreamsResult;
}

/**
 * Get GCP runtime logs
 * 
 * @param {Object} options 
 * @param {string} options.functionName
 * @param {string} options.projectId
 * @param {Object} logger 
 * @returns 
 */
async function getGCPLogs(options = {}, logger) {
  logger.verbose(`Getting logs, function: ${options.functionName}`);
  const entries = await gcp.getLogs(options);

  entries.forEach((entry) => {
    logger.info('Entry', { additional: entry });
  });

  return entries;
}

/**
 * Get Psoxy logs: Cloudwatch for AWS deploys, runtime logs for GCP deploys
 *
 * @param {Object} options
 * @param {boolean} options.verbose
 * @param {string} options.logGroupName - AWS: Cloudwatch log group to display
 * @param {string} options.region - AWS: region
 * @param {string} options.role - AWS: role to assume (ARN format)
 * @param {string} options.functionName - GCP: Name of the cloud function from
 * which to list entries
 * @param {string} options.projectId - GCP: Name of the project that hosts the
 * cloud function (Psoxy instance)
 * @param {object} logger - winston instance
 * @returns
 */
export default async function (options = {}, logger) {
  const isGCP = _.every([options.functionName, options.projectId], 
    _.negate(_.isEmpty));
  const isAWS = _.every([options.logGroupName, options.role], 
    _.negate(_.isEmpty));

  if (!isGCP && !isAWS) {
    throw new Error('Invalid options: make sure either all GCP or AWS options are present');
  }

  if (!logger) {
    logger = getLogger(options.verbose);
  }
  
  return isGCP ? getGCPLogs(options, logger) : getAWSLogs(options, logger);
}
