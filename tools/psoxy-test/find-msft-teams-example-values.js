#!/usr/bin/env node
/**
 * Walk Microsoft Graph (via the msft-teams Psoxy connector) to find real values
 * for endpoints whose ids a user would otherwise have to hunt for by hand:
 *   - a `callRecords` id (GET /v1.0/communications/callRecords/{id})
 *   - an online meeting (GET /v1.0/users/{userId}/onlineMeetings/{meetingId}),
 *     found by listing chats where `onlineMeetingInfo/joinWebUrl` is set and
 *     resolving that join URL against the onlineMeetings collection.
 *   - a team + channel that actually has messages (GET /v1.0/teams/{teamId}/channels/{channelId}/messages)
 *   - a chat that actually has messages (GET /v1.0/chats/{chatId}/messages)
 *
 * `/v1.0/communications/calls/{callId}` is intentionally NOT covered: Microsoft
 * Graph has no endpoint to list existing calls. A `call` resource only exists
 * transiently, for the lifetime of a session created by a calling bot via
 * `POST /communications/calls`; its id is only known to whatever created it.
 * There's nothing to discover here via read-only requests.
 *
 * Example (same flags as generated test-msft-teams.sh):
 *   node find-msft-teams-example-values.js \
 *     -u https://35.190.95.41/psoxy-dev-erik-msft-teams \
 *     -f gcp --allow-insecure-tls
 */
import { Command, Option } from 'commander';
import chalk from 'chalk';
import aws from './lib/aws.js';
import gcp from './lib/gcp.js';
import getLogger from './lib/logger.js';
import { environmentCheck } from './lib/utils.js';

const GRAPH_HOST_PREFIX = /^https:\/\/graph\.microsoft\.com/i;

function joinUrl(base, pathAndQuery) {
  return `${base.replace(/\/+$/, '')}${pathAndQuery.startsWith('/') ? pathAndQuery : `/${pathAndQuery}`}`;
}

/**
 * `@odata.nextLink` values are absolute `https://graph.microsoft.com/...` URLs;
 * the psoxy connector expects the same relative path+query it received.
 */
function relativeFromNextLink(nextLink) {
  if (!nextLink) {
    return undefined;
  }
  const stripped = nextLink.replace(GRAPH_HOST_PREFIX, '');
  return stripped.startsWith('/') ? stripped : `/${stripped}`;
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
    .name('find-msft-teams-example-values.js')
    .description('Find real callRecord / onlineMeeting / channel / chat values for Microsoft Teams example API calls')
    .requiredOption('-u, --url <url>', 'Psoxy msft-teams function base URL (no API path)')
    .option('-f, --force <type>', 'Force deploy type: AWS or GCP')
    .option('-t, --token <token>', 'Authorization token for GCP')
    .option('-r, --role <arn>', 'ARN of AWS role to assume')
    .option('--region <region>', 'AWS region of the Psoxy instance')
    .option('--allow-insecure-tls', 'Allow self-signed TLS (PoC ALB)', false)
    .option('--cacert <path>', 'PEM file to trust as CA')
    .addOption(new Option('--target <target>', 'Look for only this one (shorthand for skipping the other three)')
      .choices(['all', 'call-record', 'online-meeting', 'team-channel', 'chat'])
      .default('all'))
    .option('--skip-call-record', 'Skip the call record search', false)
    .option('--skip-online-meeting', 'Skip the online meeting search', false)
    .option('--skip-team-channel', 'Skip the team/channel search', false)
    .option('--skip-chat', 'Skip the chat search', false)
    .option('--skip-calls-note', 'Skip the informational note about /communications/calls', false)
    .option('--page-size <n>', 'Graph $top page size', (v) => parseInt(v, 10), 50)
    .option('--max-users <n>', 'Stop scanning users after this many (online-meeting/chat search only; 0 = all)', (v) => parseInt(v, 10), 0)
    .option('--max-teams <n>', 'Stop scanning teams after this many (team-channel search only; 0 = all)', (v) => parseInt(v, 10), 0)
    .option('--consistency-level <value>', 'ConsistencyLevel header used for the chats $filter query', 'eventual')
    .option('-H, --header <header>', 'Extra request header in "Name: Value" format (repeatable)', (val, prev) => prev.concat([val]), [])
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

  async function call(pathAndQuery, extraHeaders = []) {
    const url = joinUrl(options.url, pathAndQuery);
    const header = [...options.header, ...extraHeaders];
    const result = await callPsoxy({ ...callOptions, header, url });
    if (typeof result.data === 'string' && result.data.length > 0) {
      try {
        result.data = JSON.parse(result.data);
      } catch {
        // leave as string
      }
    }
    return result;
  }

  async function paginateGraph(pathAndQuery, extraHeaders = []) {
    const items = [];
    let next = pathAndQuery;

    do {
      const result = await call(next, extraHeaders);
      if (result.status !== 200) {
        return { ok: false, status: result.status, data: result.data, items };
      }
      items.push(...(result.data?.value ?? []));
      next = relativeFromNextLink(result.data?.['@odata.nextLink']);
    } while (next);

    return { ok: true, items };
  }

  async function findCallRecord() {
    // NOTE: the msft-teams rules only allow $select/$expand/$filter on this endpoint
    // (no $top/$skip) - don't add other query params here or the whole request gets rejected.
    const listPath = '/v1.0/communications/callRecords';
    logger.info(`Listing call records via ${chalk.blue(joinUrl(options.url, listPath))}`);

    // Single page only: Graph's `@odata.nextLink` for this endpoint carries a $skiptoken,
    // which isn't in this endpoint's allowedQueryParams, so following it would get rejected.
    const listResult = await call(listPath);
    if (listResult.status !== 200) {
      logger.error(`GET /v1.0/communications/callRecords failed (${listResult.status})`);
      console.log(listResult.data);
      return false;
    }
    const items = listResult.data?.value ?? [];
    if (items.length === 0) {
      logger.error('No call records found (tenant may not have any completed Teams calls yet)');
      return false;
    }

    const record = items[0];
    const detailPath = `/v1.0/communications/callRecords/${encodeURIComponent(record.id)}${queryString({ '$expand': 'sessions($expand=segments)' })}`;
    const detail = await call(detailPath);

    console.log(JSON.stringify({
      callRecordId: record.id,
      callRecordDetailStatus: detail.status,
      callRecordDetail: detail.data,
      testCall: `./test-msft-teams.sh GET '${detailPath}'`,
    }, null, 2));

    if (detail.status === 200) {
      logger.success(`Found call record ${record.id}`);
      return true;
    }

    logger.error(`Found call record id ${record.id}, but fetching its detail returned ${detail.status}`);
    return false;
  }

  async function findOnlineMeeting() {
    const usersPath = `/v1.0/users${queryString({ '$select': 'id,displayName', '$top': options.pageSize })}`;
    logger.info(`Listing users via ${chalk.blue(joinUrl(options.url, usersPath))}`);

    const usersResult = await paginateGraph(usersPath);
    if (!usersResult.ok) {
      logger.error(`GET /v1.0/users failed (${usersResult.status})`);
      console.log(usersResult.data);
      return false;
    }

    const users = options.maxUsers > 0
      ? usersResult.items.slice(0, options.maxUsers)
      : usersResult.items;
    logger.info(`Checking ${chalk.blue(users.length)} user(s) for a chat with an online meeting`);

    const consistencyHeader = [`ConsistencyLevel: ${options.consistencyLevel}`];

    for (const user of users) {
      const userId = user.id;
      const chatsPath = `/v1.0/users/${encodeURIComponent(userId)}/chats${queryString({
        '$filter': 'onlineMeetingInfo/joinWebUrl ne null',
        '$top': options.pageSize,
      })}`;

      const chatsResult = await paginateGraph(chatsPath, consistencyHeader);
      if (!chatsResult.ok) {
        logger.verbose(`  user ${userId}: chats $filter skipped (${chatsResult.status})`);
        continue;
      }
      if (chatsResult.items.length === 0) {
        continue;
      }
      logger.info(`  user ${userId}: ${chatsResult.items.length} meeting chat(s)`);

      for (const chat of chatsResult.items) {
        const joinWebUrl = chat.onlineMeetingInfo?.joinWebUrl;
        if (!joinWebUrl) {
          continue;
        }

        const meetingPath = `/v1.0/users/${encodeURIComponent(userId)}/onlineMeetings${queryString({
          '$filter': `JoinWebUrl eq '${joinWebUrl}'`,
        })}`;
        const meetingResult = await call(meetingPath);
        if (meetingResult.status !== 200) {
          logger.verbose(`    onlineMeetings lookup failed (${meetingResult.status})`);
          continue;
        }

        const meeting = meetingResult.data?.value?.[0];
        if (!meeting?.id) {
          logger.verbose('    onlineMeetings lookup returned no match for this chat');
          continue;
        }

        logger.success(`Found online meeting ${meeting.id} for user ${userId}`);

        const reportsPath = `/v1.0/users/${encodeURIComponent(userId)}/onlineMeetings/${encodeURIComponent(meeting.id)}/attendanceReports`;
        const reports = await call(reportsPath);
        const firstReportId = reports.status === 200 ? reports.data?.value?.[0]?.id : undefined;

        console.log(JSON.stringify({
          userId,
          chatId: chat.id,
          joinWebUrl,
          onlineMeeting: meeting,
          attendanceReportsStatus: reports.status,
          attendanceReports: reports.data,
          testCalls: [
            `./test-msft-teams.sh GET '${meetingPath}'`,
            `./test-msft-teams.sh GET '${reportsPath}'`,
            ...(firstReportId ? [`./test-msft-teams.sh GET '${reportsPath}/${firstReportId}'`] : []),
          ],
        }, null, 2));

        return true;
      }
    }

    logger.error('No chat with a resolvable online meeting was found');
    return false;
  }

  async function findTeamChannel() {
    const teamsPath = `/v1.0/teams${queryString({ '$select': 'id,displayName', '$top': options.pageSize })}`;
    logger.info(`Listing teams via ${chalk.blue(joinUrl(options.url, teamsPath))}`);

    const teamsResult = await paginateGraph(teamsPath);
    if (!teamsResult.ok) {
      logger.error(`GET /v1.0/teams failed (${teamsResult.status})`);
      console.log(teamsResult.data);
      return false;
    }
    if (teamsResult.items.length === 0) {
      logger.error('No teams found');
      return false;
    }

    const teams = options.maxTeams > 0
      ? teamsResult.items.slice(0, options.maxTeams)
      : teamsResult.items;
    logger.info(`Checking ${chalk.blue(teams.length)} team(s) for a channel with messages`);

    for (const team of teams) {
      const teamId = team.id;
      const channelsPath = `/v1.0/teams/${encodeURIComponent(teamId)}/allChannels`;
      // NOTE: allChannels only allows $select/$filter (no $top/$skiptoken) - single page only.
      const channelsResult = await call(channelsPath);
      if (channelsResult.status !== 200) {
        logger.verbose(`  team ${teamId}: allChannels skipped (${channelsResult.status})`);
        continue;
      }

      const channels = channelsResult.data?.value ?? [];
      if (channels.length === 0) {
        continue;
      }
      logger.info(`  team ${teamId}: ${channels.length} channel(s)`);

      for (const channel of channels) {
        const messagesPath = `/v1.0/teams/${encodeURIComponent(teamId)}/channels/${encodeURIComponent(channel.id)}/messages${queryString({ '$top': options.pageSize })}`;
        const messages = await call(messagesPath);
        if (messages.status !== 200) {
          logger.verbose(`    channel ${channel.id} messages skipped (${messages.status})`);
          continue;
        }

        const items = messages.data?.value ?? [];
        if (items.length === 0) {
          continue;
        }

        logger.success(`Found channel ${channel.id} (team ${teamId}) with ${items.length} message(s)`);

        const deltaPath = `/v1.0/teams/${encodeURIComponent(teamId)}/channels/${encodeURIComponent(channel.id)}/messages/delta`;
        const delta = await call(deltaPath);

        console.log(JSON.stringify({
          teamId,
          channelId: channel.id,
          messagesStatus: messages.status,
          messages: messages.data,
          deltaStatus: delta.status,
          delta: delta.data,
          testCalls: [
            `./test-msft-teams.sh GET '/v1.0/teams'`,
            `./test-msft-teams.sh GET '${channelsPath}'`,
            `./test-msft-teams.sh GET '${messagesPath}'`,
            `./test-msft-teams.sh GET '${deltaPath}'`,
          ],
        }, null, 2));

        return true;
      }
    }

    logger.error('No channel with messages was found');
    return false;
  }

  async function findChat() {
    const usersPath = `/v1.0/users${queryString({ '$select': 'id,displayName', '$top': options.pageSize })}`;
    logger.info(`Listing users via ${chalk.blue(joinUrl(options.url, usersPath))}`);

    const usersResult = await paginateGraph(usersPath);
    if (!usersResult.ok) {
      logger.error(`GET /v1.0/users failed (${usersResult.status})`);
      console.log(usersResult.data);
      return false;
    }

    const users = options.maxUsers > 0
      ? usersResult.items.slice(0, options.maxUsers)
      : usersResult.items;
    logger.info(`Checking ${chalk.blue(users.length)} user(s) for a chat with messages`);

    for (const user of users) {
      const userId = user.id;
      const chatsPath = `/v1.0/users/${encodeURIComponent(userId)}/chats${queryString({ '$top': options.pageSize })}`;

      const chatsResult = await paginateGraph(chatsPath);
      if (!chatsResult.ok) {
        logger.verbose(`  user ${userId}: chats skipped (${chatsResult.status})`);
        continue;
      }
      if (chatsResult.items.length === 0) {
        continue;
      }
      logger.info(`  user ${userId}: ${chatsResult.items.length} chat(s)`);

      for (const chat of chatsResult.items) {
        const messagesPath = `/v1.0/chats/${encodeURIComponent(chat.id)}/messages${queryString({ '$top': options.pageSize })}`;
        const messages = await call(messagesPath);
        if (messages.status !== 200) {
          logger.verbose(`    chat ${chat.id} messages skipped (${messages.status})`);
          continue;
        }

        const items = messages.data?.value ?? [];
        if (items.length === 0) {
          continue;
        }

        logger.success(`Found chat ${chat.id} (user ${userId}, type ${chat.chatType}) with ${items.length} message(s)`);

        console.log(JSON.stringify({
          userId,
          chatId: chat.id,
          chatType: chat.chatType,
          messagesStatus: messages.status,
          messages: messages.data,
          testCalls: [
            `./test-msft-teams.sh GET '/v1.0/users/${userId}/chats'`,
            `./test-msft-teams.sh GET '${messagesPath}'`,
          ],
        }, null, 2));

        return true;
      }
    }

    logger.error('No chat with messages was found');
    return false;
  }

  function explainCalls() {
    logger.info(
      "Skipping /v1.0/communications/calls/{callId}: Microsoft Graph has no endpoint to " +
      "list existing calls. A call resource only exists while a calling bot has an active " +
      "session, and its id is only known to whatever created it (POST /communications/calls) " +
      "or subscribed to notifications about it. There's nothing to discover here via " +
      "read-only requests; you need an id from your own calling-bot integration."
    );
  }

  const finders = {
    'call-record': { label: 'call record', run: findCallRecord, skip: options.skipCallRecord },
    'online-meeting': { label: 'online meeting', run: findOnlineMeeting, skip: options.skipOnlineMeeting },
    'team-channel': { label: 'team channel with messages', run: findTeamChannel, skip: options.skipTeamChannel },
    'chat': { label: 'chat with messages', run: findChat, skip: options.skipChat },
  };

  const requested = options.target === 'all'
    ? [...Object.keys(finders), 'calls']
    : [options.target];
  const targets = requested.filter((target) => target === 'calls'
    ? !options.skipCallsNote
    : !finders[target].skip);

  if (targets.length === 0) {
    logger.error('Nothing to do: every target was skipped or excluded via --target/--skip-*');
    process.exitCode = 1;
    return;
  }

  let anyFound = false;

  for (const target of targets) {
    if (target === 'calls') {
      explainCalls();
      continue;
    }

    const { label, run } = finders[target];
    logger.info(`--- Searching for a ${label} ---`);
    const found = await run();
    anyFound = anyFound || found;
  }

  if (!anyFound) {
    process.exitCode = 1;
  }
}

main().catch((error) => {
  console.error(chalk.red(error.message));
  process.exit(1);
});
