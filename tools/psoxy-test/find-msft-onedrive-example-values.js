#!/usr/bin/env node
/**
 * Walk Microsoft Graph (via the msft-onedrive Psoxy connector) to find real values
 * for endpoints whose ids a user would otherwise have to hunt for by hand:
 *   - a Drive id (GET /v1.0/users/{userId}/drives or /v1.0/groups/{groupId}/drives)
 *   - a driveItem id from that drive's root delta feed (GET /v1.0/drives/{driveId}/root/delta)
 *
 * These are exactly the values `msft_onedrive_example_drive_id` / `msft_onedrive_example_item_id`
 * (part of `msft_365_connector_settings`) exist for - Terraform has no way to enumerate them.
 *
 * Example (same flags as generated test-msft-onedrive.sh):
 *   node find-msft-onedrive-example-values.js \
 *     -u https://35.190.95.41/psoxy-dev-erik-msft-onedrive \
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
    .name('find-msft-onedrive-example-values.js')
    .description('Find a real Drive id / driveItem id for Microsoft OneDrive example API calls')
    .requiredOption('-u, --url <url>', 'Psoxy msft-onedrive function base URL (no API path)')
    .option('-f, --force <type>', 'Force deploy type: AWS or GCP')
    .option('-t, --token <token>', 'Authorization token for GCP')
    .option('-r, --role <arn>', 'ARN of AWS role to assume')
    .option('--region <region>', 'AWS region of the Psoxy instance')
    .option('--allow-insecure-tls', 'Allow self-signed TLS (PoC ALB)', false)
    .option('--cacert <path>', 'PEM file to trust as CA')
    .addOption(new Option('--source <source>', 'Look for only this one (shorthand for skipping the other)')
      .choices(['all', 'users', 'groups'])
      .default('all'))
    .option('--skip-users', 'Skip checking users\' drives', false)
    .option('--skip-groups', 'Skip checking groups\' drives', false)
    .option('--page-size <n>', 'Graph $top page size', (v) => parseInt(v, 10), 50)
    .option('--max-owners <n>', 'Stop scanning users/groups after this many (0 = all)', (v) => parseInt(v, 10), 0)
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

  async function ownerCandidates(kind) {
    const path = kind === 'users'
      ? `/v1.0/users${queryString({ '$select': 'id,displayName', '$top': options.pageSize })}`
      : `/v1.0/groups${queryString({ '$select': 'id,displayName', '$top': options.pageSize })}`;
    logger.info(`Listing ${kind} via ${chalk.blue(joinUrl(options.url, path))}`);

    const result = await paginateGraph(path);
    if (!result.ok) {
      logger.error(`GET ${path.split('?')[0]} failed (${result.status})`);
      console.log(result.data);
      return [];
    }
    return options.maxOwners > 0 ? result.items.slice(0, options.maxOwners) : result.items;
  }

  async function findDriveFor(kind, ownerId) {
    const drivesPath = kind === 'users'
      ? `/v1.0/users/${encodeURIComponent(ownerId)}/drives${queryString({ '$top': options.pageSize })}`
      : `/v1.0/groups/${encodeURIComponent(ownerId)}/drives${queryString({ '$top': options.pageSize })}`;

    const drivesResult = await paginateGraph(drivesPath);
    if (!drivesResult.ok) {
      logger.verbose(`  ${kind === 'users' ? 'user' : 'group'} ${ownerId}: drives skipped (${drivesResult.status})`);
      return undefined;
    }
    return drivesResult.items[0];
  }

  async function findDriveItem(driveId) {
    // NOTE: root/delta allows $top (but not $skiptoken - it pages via a `token` param instead).
    const deltaPath = `/v1.0/drives/${encodeURIComponent(driveId)}/root/delta${queryString({ '$top': options.pageSize })}`;
    const delta = await call(deltaPath);
    if (delta.status !== 200) {
      return { ok: false, status: delta.status, data: delta.data };
    }

    const items = delta.data?.value ?? [];
    // prefer an actual file over a folder, since driveItem activities are more meaningful for files
    const item = items.find((i) => i.file) ?? items[0];
    return { ok: true, deltaPath, delta, item };
  }

  async function findDriveAndItem(kind) {
    const owners = await ownerCandidates(kind);
    logger.info(`Checking ${chalk.blue(owners.length)} ${kind} for a OneDrive with content`);

    for (const owner of owners) {
      const drive = await findDriveFor(kind, owner.id);
      if (!drive?.id) {
        continue;
      }
      logger.info(`  ${kind === 'users' ? 'user' : 'group'} ${owner.id}: found drive ${drive.id}`);

      const found = await findDriveItem(drive.id);
      if (!found.ok) {
        logger.verbose(`    root/delta skipped (${found.status})`);
        continue;
      }
      if (!found.item?.id) {
        logger.verbose('    root/delta returned no items');
        continue;
      }

      logger.success(`Found drive ${drive.id} (${kind === 'users' ? 'user' : 'group'} ${owner.id}) with item ${found.item.id}`);

      const itemActivitiesPath = `/v1.0/drives/${encodeURIComponent(drive.id)}/items/${encodeURIComponent(found.item.id)}/activities`;
      const driveActivitiesPath = `/v1.0/drives/${encodeURIComponent(drive.id)}/activities`;
      // NOTE: both *_activities* endpoints only allow $expand/$skiptoken (no $top) - don't add one.
      const itemActivities = await call(itemActivitiesPath);
      const driveActivities = await call(driveActivitiesPath);

      console.log(JSON.stringify({
        ownerType: kind === 'users' ? 'user' : 'group',
        ownerId: owner.id,
        driveId: drive.id,
        itemId: found.item.id,
        rootDeltaStatus: found.delta.status,
        rootDeltaSample: found.item,
        itemActivitiesStatus: itemActivities.status,
        itemActivities: itemActivities.data,
        driveActivitiesStatus: driveActivities.status,
        driveActivities: driveActivities.data,
        testCalls: [
          `./test-msft-onedrive.sh GET '${kind === 'users' ? `/v1.0/users/${owner.id}/drives` : `/v1.0/groups/${owner.id}/drives`}'`,
          `./test-msft-onedrive.sh GET '${found.deltaPath}'`,
          `./test-msft-onedrive.sh GET '${itemActivitiesPath}'`,
          `./test-msft-onedrive.sh GET '${driveActivitiesPath}'`,
        ],
      }, null, 2));

      return true;
    }

    return false;
  }

  const requested = options.source === 'all' ? ['users', 'groups'] : [options.source];
  const skip = { users: options.skipUsers, groups: options.skipGroups };
  const kinds = requested.filter((kind) => !skip[kind]);

  if (kinds.length === 0) {
    logger.error('Nothing to do: every source was skipped or excluded via --source/--skip-*');
    process.exitCode = 1;
    return;
  }

  let found = false;

  for (const kind of kinds) {
    logger.info(`--- Searching ${kind} for a drive/item ---`);
    found = await findDriveAndItem(kind);
    if (found) {
      break;
    }
  }

  if (!found) {
    logger.error('No drive with a discoverable item was found');
    process.exitCode = 1;
  }
}

main().catch((error) => {
  console.error(chalk.red(error.message));
  process.exit(1);
});
