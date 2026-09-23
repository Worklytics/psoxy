#!/usr/bin/env node
/**
 * Walk Zoom via psoxy-test HTTP helpers until a past meeting instance with
 * `has_meeting_summary: true` is found, then fetch `/meeting_summary`.
 *
 * Example (same flags as generated test-zoom.sh):
 *   node find-zoom-meeting-summary.js \
 *     -u https://35.190.95.41/psoxy-dev-erik-zoom \
 *     -f gcp --allow-insecure-tls
 */
import { Command, Option } from 'commander';
import chalk from 'chalk';
import aws from './lib/aws.js';
import gcp from './lib/gcp.js';
import getLogger from './lib/logger.js';
import { environmentCheck } from './lib/utils.js';

const REPORT_WINDOW_DAYS = 30;

function joinUrl(base, pathAndQuery) {
  return `${base.replace(/\/+$/, '')}${pathAndQuery.startsWith('/') ? pathAndQuery : `/${pathAndQuery}`}`;
}

/**
 * Zoom requires double-encoding when a UUID starts with `/` or contains `//`.
 * Always encode `+` / `=` so they survive the URL path.
 */
function encodeZoomUuid(uuid) {
  const value = String(uuid);
  const encoded = encodeURIComponent(value);
  if (value.startsWith('/') || value.includes('//')) {
    return encodeURIComponent(encoded);
  }
  return encoded;
}

function ymd(date) {
  return date.toISOString().slice(0, 10);
}

function addDays(date, days) {
  const next = new Date(date);
  next.setUTCDate(next.getUTCDate() + days);
  return next;
}

function reportWindows(lookbackDays) {
  const windows = [];
  const end = new Date();
  let windowEnd = end;
  const earliest = addDays(end, -lookbackDays);

  while (windowEnd > earliest) {
    const windowStart = addDays(windowEnd, -REPORT_WINDOW_DAYS);
    const from = windowStart < earliest ? earliest : windowStart;
    windows.push({ from: ymd(from), to: ymd(windowEnd) });
    windowEnd = from;
  }
  return windows;
}

function pickCaller(options) {
  if (options.force) {
    const methods = { aws: aws.call, gcp: gcp.call };
    const call = methods[options.force.toLowerCase()];
    if (!call) {
      throw new Error(`Unknown --force value "${options.force}" (use aws or gcp)`);
    }
    return call;
  }

  const url = new URL(options.url);
  if (aws.isValidURL(url)) {
    return aws.call;
  }
  if (gcp.isValidURL(url)) {
    return gcp.call;
  }

  throw new Error(
    `"${options.url}" doesn't look like an AWS or GCP Psoxy URL. Pass -f gcp or -f aws.`
  );
}

function queryString(params) {
  const search = new URLSearchParams();
  for (const [key, value] of Object.entries(params)) {
    if (value !== undefined && value !== null && value !== '') {
      search.set(key, String(value));
    }
  }
  const encoded = search.toString();
  return encoded ? `?${encoded}` : '';
}

async function main() {
  const program = new Command();
  program
    .name('find-zoom-meeting-summary.js')
    .description('Find a Zoom past meeting instance that has an AI Companion meeting summary')
    .requiredOption('-u, --url <url>', 'Psoxy Zoom function base URL (no API path)')
    .option('-f, --force <type>', 'Force deploy type: AWS or GCP')
    .option('-t, --token <token>', 'Authorization token for GCP')
    .option('-r, --role <arn>', 'ARN of AWS role to assume')
    .option('--region <region>', 'AWS region of the Psoxy instance')
    .option('--allow-insecure-tls', 'Allow self-signed TLS (PoC ALB)', false)
    .option('--cacert <path>', 'PEM file to trust as CA')
    .option('--lookback-days <n>', 'How far back to search report meetings', (v) => parseInt(v, 10), 90)
    .option('--page-size <n>', 'Zoom page size', (v) => parseInt(v, 10), 300)
    .option('--max-users <n>', 'Stop after this many users (0 = all)', (v) => parseInt(v, 10), 0)
    .option('-v, --verbose', 'Verbose psoxy-test HTTP logs', false)
    .addOption(new Option('-z, --gzip [type]', 'Gzip request header').default(true))
    .parse(process.argv);

  const options = program.opts();
  if (typeof options.gzip === 'string') {
    options.gzip = options.gzip !== 'false';
  }

  const logger = getLogger(options.verbose);
  environmentCheck(logger);

  const callPsoxy = pickCaller(options);
  const callOptions = {
    force: options.force,
    token: options.token,
    role: options.role,
    region: options.region,
    allowInsecureTls: options.allowInsecureTls,
    cacert: options.cacert,
    gzip: options.gzip,
    verbose: options.verbose,
  };

  async function call(pathAndQuery) {
    const url = joinUrl(options.url, pathAndQuery);
    const result = await callPsoxy({ ...callOptions, url });
    if (typeof result.data === 'string' && result.data.length > 0) {
      try {
        result.data = JSON.parse(result.data);
      } catch {
        // leave as string
      }
    }
    return result;
  }

  async function paginate(path, { itemKey, extraParams = {} } = {}) {
    const items = [];
    let nextPageToken;

    do {
      const qs = queryString({
        page_size: options.pageSize,
        next_page_token: nextPageToken,
        ...extraParams,
      });
      const result = await call(`${path}${qs}`);
      if (result.status !== 200) {
        return { ok: false, status: result.status, data: result.data, items };
      }
      const pageItems = result.data?.[itemKey] ?? [];
      items.push(...pageItems);
      nextPageToken = result.data?.next_page_token || undefined;
    } while (nextPageToken);

    return { ok: true, items };
  }

  logger.info(`Listing Zoom users via ${chalk.blue(joinUrl(options.url, '/v2/users'))}`);
  const usersResult = await paginate('/v2/users', { itemKey: 'users' });
  if (!usersResult.ok) {
    logger.error(`GET /v2/users failed (${usersResult.status})`);
    console.log(usersResult.data);
    process.exitCode = 1;
    return;
  }

  const users = options.maxUsers > 0
    ? usersResult.items.slice(0, options.maxUsers)
    : usersResult.items;
  logger.info(`Checking ${chalk.blue(users.length)} user(s) (lookback ${options.lookbackDays}d)`);

  const windows = reportWindows(options.lookbackDays);
  const seenInstanceUuids = new Set();

  for (const user of users) {
    const userId = user.id;
    logger.info(`User ${chalk.blue(userId)}`);

    const meetingsById = new Map();

    const previous = await paginate(`/v2/users/${encodeURIComponent(userId)}/meetings`, {
      itemKey: 'meetings',
      extraParams: { type: 'previous_meetings' },
    });
    if (previous.ok) {
      for (const meeting of previous.items) {
        meetingsById.set(String(meeting.id), meeting);
      }
    } else {
      logger.verbose(`  list previous_meetings skipped (${previous.status})`);
    }

    for (const { from, to } of windows) {
      const report = await paginate(`/v2/report/users/${encodeURIComponent(userId)}/meetings`, {
        itemKey: 'meetings',
        extraParams: { from, to },
      });
      if (!report.ok) {
        logger.verbose(`  report ${from}..${to} skipped (${report.status})`);
        continue;
      }
      for (const meeting of report.items) {
        meetingsById.set(String(meeting.id), meeting);
      }
    }

    logger.info(`  ${meetingsById.size} distinct past meeting id(s)`);

    for (const [meetingId, meeting] of meetingsById) {
      const instancesResult = await call(`/v2/past_meetings/${encodeURIComponent(meetingId)}/instances`);
      if (instancesResult.status !== 200) {
        logger.verbose(`  meeting ${meetingId} instances skipped (${instancesResult.status}: ${instancesResult.data?.message || 'error'})`);
        continue;
      }

      const instances = instancesResult.data?.meetings ?? [];
      if (instances.length === 0) {
        continue;
      }

      for (const instance of instances) {
        const uuid = instance.uuid;
        if (!uuid || seenInstanceUuids.has(uuid)) {
          continue;
        }
        seenInstanceUuids.add(uuid);

        const encodedUuid = encodeZoomUuid(uuid);
        const details = await call(`/v2/past_meetings/${encodedUuid}`);
        if (details.status !== 200) {
          logger.verbose(`    instance ${uuid} details skipped (${details.status}: ${details.data?.message || 'error'})`);
          continue;
        }

        const hasSummary = details.data?.has_meeting_summary === true;
        if (!hasSummary) {
          logger.verbose(`    ${uuid} has_meeting_summary=false`);
          continue;
        }

        logger.info(`    ${uuid} has_meeting_summary=true`);

        const summary = await call(`/v2/meetings/${encodedUuid}/meeting_summary`);
        console.log(JSON.stringify({
          userId,
          meetingId,
          meetingStartTime: meeting.start_time || meeting.startTime,
          instanceUuid: uuid,
          instanceStartTime: instance.start_time,
          pastMeeting: details.data,
          meetingSummaryStatus: summary.status,
          meetingSummary: summary.data,
          testCall: `./test-zoom.sh GET '/v2/meetings/${encodedUuid}/meeting_summary'`,
        }, null, 2));

        if (summary.status === 200) {
          logger.success('Found a meeting summary');
          return;
        }

        logger.info(`    meeting_summary returned ${summary.status}; continuing search`);
      }
    }
  }

  logger.error('No past meeting instance with has_meeting_summary=true produced a summary');
  process.exitCode = 1;
}

main().catch((error) => {
  console.error(chalk.red(error.message));
  process.exit(1);
});
