import aws from './lib/aws.js';
import getLogger from './lib/logger.js';

/**
 * Test Psoxy logs
 * Display latest log events from a log group associated to a Psoxy instance;
 * AWS - CloudWatch
 * 
 * @param {Object} options
 * @param {boolean} options.verbose
 * @param {string} options.logGroupName
 * @param {string} options.region
 * @param {string} options.role
 * @returns
 */
export default async function (options = {}) {
  const logger = getLogger(options.verbose);
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
      throw new Error(`Unable to get log events for stream ${logEventsResult}`, {
        additional: logEventsResult,
      });
    }

    logger.success(JSON.stringify(logEventsResult, undefined, 2));
  }

  return logEventsResult || logStreamsResult;
}
