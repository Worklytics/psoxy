import test from 'ava';
import { transformSpecWithResponse } from '../lib/utils.js';
import spec from '../data-sources/spec.js';
import slackResponse from './stub/slack-discovery-api.js';

test('Transform data sources spec with API responses', (t) => {
  const unknownSpec = spec['foo'];
  let result = transformSpecWithResponse(unknownSpec, slackResponse);
  t.deepEqual(result, {});

  const slackSpec = spec['slack-discovery-api'];

  result = transformSpecWithResponse(slackSpec, {});
  t.deepEqual(result, slackSpec);

  const isWorkspaceConversations = (endpoint) => endpoint.name === 'Workspace Conversations';
  const originalParam = slackSpec.endpoints.find(isWorkspaceConversations).params.team;
  result = transformSpecWithResponse(slackSpec, slackResponse);
  t.not(result.endpoints.find(isWorkspaceConversations).params.team, originalParam);
});
