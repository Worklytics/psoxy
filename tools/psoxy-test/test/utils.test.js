import test from 'ava';
import { transformSpecWithResponse } from '../lib/utils.js';
import spec from '../data-sources/spec.js';
import { createRequire } from 'module';
const require = createRequire(import.meta.url);
// Unorthodox approach: load actual JSON response examples used by Psoxy backend
const slackResponse = require('../../../java/core/src/test/resources/api-response-examples/slack/discovery-enterprise-info.json');
const calendarEventsResponse = require('../../../java/core/src/test/resources/api-response-examples/g-workspace/calendar/events.json');

test('Transform data sources spec with API responses: param replacement', (t) => {
  const unknownSpec = spec['foo'];
  let result = transformSpecWithResponse(unknownSpec, slackResponse);
  t.deepEqual(result, {});

  const slackSpec = spec['slack-discovery-api'];

  result = transformSpecWithResponse(slackSpec, {});
  t.deepEqual(result, slackSpec);

  result = transformSpecWithResponse(slackSpec, slackResponse);
  const workspaceConversationsEndpoint = slackSpec.endpoints
    .find((endpoint) => endpoint.name === 'Workspace Conversations');

  // `team` param replacement
  t.is(workspaceConversationsEndpoint.params.team, slackResponse.enterprise.teams[0].id);
});

test('Transform data sources spec with API responses: path replacement', (t) => {
  const gcalSpec = spec['gcal'];
  const result = transformSpecWithResponse(gcalSpec, calendarEventsResponse);

  // [event_id] path replacement
  const eventEndpoint = gcalSpec.endpoints.find((endpoint) => endpoint.name === 'Event');
  t.true(eventEndpoint.path.endsWith(calendarEventsResponse.items[0].id));
});
