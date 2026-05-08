import test from 'ava';
import { createRequire } from 'module';
import { inferSchema, describeRequired } from '../lib/schema.js';

const require = createRequire(import.meta.url);
const copilotFixture          = require('./fixtures/copilot-interactions.json');
const asanaFixture            = require('./fixtures/asana-workspaces.json');
const calendarFixture         = require('./fixtures/calendar-events.json');
const copilotExpectedSchema   = require('./fixtures/copilot-interactions.schema.json');
const calendarExpectedSchema  = require('./fixtures/calendar-events.schema.json');
const asanaExpectedSchema     = require('./fixtures/asana-workspaces.schema.json');

// ── primitives ────────────────────────────────────────────────────────────────

test('inferSchema: string', (t) => {
  t.deepEqual(inferSchema('hello'), { type: 'string' });
});

test('inferSchema: integer', (t) => {
  t.deepEqual(inferSchema(42), { type: 'integer' });
});

test('inferSchema: float is number not integer', (t) => {
  t.deepEqual(inferSchema(3.14), { type: 'number' });
});

test('inferSchema: boolean', (t) => {
  t.deepEqual(inferSchema(true), { type: 'boolean' });
});

test('inferSchema: null', (t) => {
  t.deepEqual(inferSchema(null), { type: 'null' });
});

// ── string format detection ───────────────────────────────────────────────────

test('inferSchema: UUID string gets format:uuid', (t) => {
  t.deepEqual(inferSchema('4db02e4b-d144-400e-b194-53253a34c5be'), { type: 'string', format: 'uuid' });
});

test('inferSchema: email string gets format:email', (t) => {
  t.deepEqual(inferSchema('alice@example.com'), { type: 'string', format: 'email' });
});

test('inferSchema: URI string gets format:uri', (t) => {
  t.deepEqual(inferSchema('https://example.com/path'), { type: 'string', format: 'uri' });
});

test('inferSchema: ISO date string gets format:date', (t) => {
  t.deepEqual(inferSchema('2024-01-15'), { type: 'string', format: 'date' });
});

test('inferSchema: ISO date-time string gets format:date-time', (t) => {
  t.deepEqual(inferSchema('2024-01-15T09:00:00.000Z'), { type: 'string', format: 'date-time' });
});

// ── objects ───────────────────────────────────────────────────────────────────

test('inferSchema: flat object — types and required list', (t) => {
  const schema = inferSchema({ id: 1, name: 'Alice', active: true, score: null });

  t.is(schema.type, 'object');
  t.deepEqual(schema.properties.id,     { type: 'integer' });
  t.deepEqual(schema.properties.name,   { type: 'string' });
  t.deepEqual(schema.properties.active, { type: 'boolean' });
  t.deepEqual(schema.properties.score,  { type: 'null' });
  t.deepEqual(schema.required.sort(), ['active', 'id', 'name', 'score']);
});

test('inferSchema: array of objects — key absent from some items is optional (not in required)', (t) => {
  // `extra` only present in item[1] → not required
  const schema = inferSchema([{ id: 1, name: 'Alice' }, { id: 2, name: 'Bob', extra: true }]);

  t.is(schema.type, 'array');
  t.is(schema.items.type, 'object');
  t.deepEqual(schema.items.properties.extra, { type: 'boolean' });
  t.true(schema.items.required.includes('id'));
  t.true(schema.items.required.includes('name'));
  t.false(schema.items.required.includes('extra'));
});

// ── Copilot interaction history ───────────────────────────────────────────────
//
// Exercises: OData metadata keys, nullable identity union types, empty-array
// items, format detection (date-time, uuid, uri), required vs optional fields.

test('inferSchema: copilot — top-level OData envelope', (t) => {
  const schema = inferSchema(copilotFixture);

  t.is(schema.type, 'object');
  t.deepEqual(schema.properties['@odata.context'],  { type: 'string', format: 'uri' });
  t.deepEqual(schema.properties['@odata.count'],    { type: 'integer' });
  t.deepEqual(schema.properties['@odata.nextLink'], { type: 'string', format: 'uri' });
  t.is(schema.properties.value.type, 'array');
});

test('inferSchema: copilot — createdDateTime detected as date-time format', (t) => {
  const itemProps = inferSchema(copilotFixture).properties.value.items.properties;
  t.deepEqual(itemProps.createdDateTime, { type: 'string', format: 'date-time' });
});

test('inferSchema: copilot — empty arrays (contexts, mentions) produce items:false', (t) => {
  const itemProps = inferSchema(copilotFixture).properties.value.items.properties;
  t.deepEqual(itemProps.contexts, { type: 'array', items: false });
  t.deepEqual(itemProps.mentions, { type: 'array', items: false });
});

test('inferSchema: copilot — from.user and from.application are nullable union types', (t) => {
  const fromProps = inferSchema(copilotFixture).properties.value.items.properties.from.properties;

  // device is null in both items
  t.deepEqual(fromProps.device, { type: 'null' });
  // user is null in item[0], object in item[1] → union
  t.deepEqual(fromProps.user.type.sort(), ['null', 'object']);
  // application is object in item[0], null in item[1] → union
  t.deepEqual(fromProps.application.type.sort(), ['null', 'object']);
});

test('inferSchema: copilot — application.id detected as uuid', (t) => {
  const appProps = inferSchema(copilotFixture)
    .properties.value.items.properties.from.properties.application;
  t.deepEqual(appProps.properties.id, { type: 'string', format: 'uuid' });
});

test('inferSchema: copilot — attachment contentUrl and name typed as null', (t) => {
  const attachProps = inferSchema(copilotFixture)
    .properties.value.items.properties.attachments.items.properties;
  t.deepEqual(attachProps.contentUrl, { type: 'null' });
  t.deepEqual(attachProps.name,       { type: 'null' });
});

test('inferSchema: copilot — link URL detected as uri format', (t) => {
  const linkProps = inferSchema(copilotFixture)
    .properties.value.items.properties.links.items.properties;
  t.deepEqual(linkProps.linkUrl, { type: 'string', format: 'uri' });
});

// ── Google Calendar events ────────────────────────────────────────────────────
//
// Exercises: format detection on emails/dates, merging sub-object schemas across
// array items, optional vs required fields, and deeply nested array schemas.

test('inferSchema: calendar — top-level envelope', (t) => {
  const schema = inferSchema(calendarFixture);
  t.is(schema.properties.kind.type,           'string');
  t.is(schema.properties.timeZone.type,       'string');
  t.is(schema.properties.accessRole.type,     'string');
  t.is(schema.properties.nextPageToken.type,  'string');
  t.is(schema.properties.items.type,          'array');
});

test('inferSchema: calendar — defaultReminders array of objects', (t) => {
  const schema = inferSchema(calendarFixture).properties.defaultReminders;
  t.is(schema.type, 'array');
  t.deepEqual(schema.items.properties.method,  { type: 'string' });
  t.deepEqual(schema.items.properties.minutes, { type: 'integer' });
});

test('inferSchema: calendar — event created/updated detected as date-time', (t) => {
  const eventProps = inferSchema(calendarFixture).properties.items.items.properties;
  t.deepEqual(eventProps.created, { type: 'string', format: 'date-time' });
  t.deepEqual(eventProps.updated, { type: 'string', format: 'date-time' });
});

test('inferSchema: calendar — creator/attendee email detected as email format', (t) => {
  const eventItems = inferSchema(calendarFixture).properties.items.items.properties;
  t.deepEqual(eventItems.creator.properties.email,           { type: 'string', format: 'email' });
  t.deepEqual(eventItems.attendees.items.properties.email,   { type: 'string', format: 'email' });
});

test('inferSchema: calendar — start/end sub-object merges dateTime, timeZone and date from all events', (t) => {
  // event[0] has dateTime only; event[1] adds timeZone; event[2] adds date
  const startProps = inferSchema(calendarFixture).properties.items.items.properties.start.properties;
  t.deepEqual(startProps.dateTime, { type: 'string', format: 'date-time' });
  t.deepEqual(startProps.timeZone, { type: 'string' });
  t.deepEqual(startProps.date,     { type: 'string', format: 'date' });
});

test('inferSchema: calendar — attendees: self is optional (not in required)', (t) => {
  const attendeeSchema = inferSchema(calendarFixture)
    .properties.items.items.properties.attendees.items;
  t.deepEqual(attendeeSchema.properties.self, { type: 'boolean' });
  t.true(attendeeSchema.required.includes('email'));
  t.false(attendeeSchema.required.includes('self'));
});

test('inferSchema: calendar — description and recurringEventId from event[1] are optional', (t) => {
  const itemSchema = inferSchema(calendarFixture).properties.items.items;
  t.deepEqual(itemSchema.properties.description,      { type: 'string' });
  t.deepEqual(itemSchema.properties.recurringEventId, { type: 'string' });
  t.false(itemSchema.required.includes('description'));
  t.false(itemSchema.required.includes('recurringEventId'));
});

test('inferSchema: calendar — reminders.overrides array merged from event[1]', (t) => {
  const reminders = inferSchema(calendarFixture)
    .properties.items.items.properties.reminders;
  t.deepEqual(reminders.properties.overrides.items.properties.method,  { type: 'string' });
  t.deepEqual(reminders.properties.overrides.items.properties.minutes, { type: 'integer' });
});

// ── null / empty value handling ───────────────────────────────────────────────
//
// location   : string in event[0], null in event[1], "" in event[2]  → ["string","null"], required
// hangoutLink: null in event[0], URI in event[1], absent in event[2] → ["string","null"]+uri, optional
// conferenceData: null in event[0], object in event[1], absent in event[2] → ["object","null"], optional
// attendeesOmitted: false in event[0]+[1], absent in event[2]        → boolean, optional
// extendedProperties: {} in event[0], {private:{…}} in event[1]     → object with private key, optional

test('inferSchema: calendar — location is nullable union and required (present in every event)', (t) => {
  const itemSchema = inferSchema(calendarFixture).properties.items.items;
  t.deepEqual(itemSchema.properties.location.type.sort(), ['null', 'string']);
  t.true(itemSchema.required.includes('location'));
});

test('inferSchema: calendar — hangoutLink is nullable uri and optional (absent from event[2])', (t) => {
  const itemSchema = inferSchema(calendarFixture).properties.items.items;
  t.deepEqual(itemSchema.properties.hangoutLink, { type: ['string', 'null'], format: 'uri' });
  t.false(itemSchema.required.includes('hangoutLink'));
});

test('inferSchema: calendar — conferenceData is nullable object and optional', (t) => {
  const itemSchema = inferSchema(calendarFixture).properties.items.items;
  t.deepEqual(itemSchema.properties.conferenceData.type.sort(), ['null', 'object']);
  t.false(itemSchema.required.includes('conferenceData'));
});

test('inferSchema: calendar — conferenceData nested structure when non-null', (t) => {
  const conf = inferSchema(calendarFixture).properties.items.items.properties.conferenceData;
  t.deepEqual(conf.properties.conferenceId, { type: 'string' });
  t.deepEqual(conf.properties.conferenceSolution.properties.iconUri, { type: 'string', format: 'uri' });
  t.deepEqual(conf.properties.entryPoints.items.properties.uri, { type: 'string', format: 'uri' });
});

test('inferSchema: calendar — attendeesOmitted is boolean and optional (absent from event[2])', (t) => {
  const itemSchema = inferSchema(calendarFixture).properties.items.items;
  t.deepEqual(itemSchema.properties.attendeesOmitted, { type: 'boolean' });
  t.false(itemSchema.required.includes('attendeesOmitted'));
});

test('inferSchema: calendar — extendedProperties: empty object merged with populated object', (t) => {
  const ext = inferSchema(calendarFixture).properties.items.items.properties.extendedProperties;
  // event[0] has {} → no keys; event[1] adds { private: {…} } → private key present
  t.is(ext.type, 'object');
  t.truthy(ext.properties.private);
  t.deepEqual(ext.properties.private.properties.importedId, { type: 'string' });
  t.deepEqual(ext.properties.private.properties.source,     { type: 'string' });
});

// ── describeRequired ─────────────────────────────────────────────────────────
//
// describeRequired() converts raw `required` string arrays into proper JSON
// Schema type descriptions so every value in the output is consistently typed.
//
// Before: { required: ["id", "name"] }
// After:  { required: { type: "array", items: { type: "string" } } }

const REQUIRED_TYPE = { type: 'array', items: { type: 'string' } };

test('describeRequired: required array is replaced with type description', (t) => {
  const schema = {
    type: 'object',
    properties: {
      id:   { type: 'integer' },
      name: { type: 'string'  },
    },
    required: ['id', 'name'],
  };

  const result = describeRequired(schema);

  t.deepEqual(result.required, REQUIRED_TYPE);
  // properties are preserved untouched
  t.deepEqual(result.properties.id,   { type: 'integer' });
  t.deepEqual(result.properties.name, { type: 'string'  });
});

test('describeRequired: object with no required array is unchanged', (t) => {
  const schema = {
    type: 'object',
    properties: { a: { type: 'string' }, b: { type: 'number' } },
  };

  const result = describeRequired(schema);

  t.is(result.required, undefined);
  t.deepEqual(result.properties.a, { type: 'string' });
  t.deepEqual(result.properties.b, { type: 'number' });
});

test('describeRequired: recursively processes nested objects', (t) => {
  const schema = {
    type: 'object',
    properties: {
      user: {
        type: 'object',
        properties: {
          id:    { type: 'string' },
          email: { type: 'string', format: 'email' },
        },
        required: ['id', 'email'],
      },
    },
    required: ['user'],
  };

  const result = describeRequired(schema);

  t.deepEqual(result.required,                        REQUIRED_TYPE);
  t.deepEqual(result.properties.user.required,        REQUIRED_TYPE);
  t.deepEqual(result.properties.user.properties.id,   { type: 'string' });
  t.deepEqual(result.properties.user.properties.email,{ type: 'string', format: 'email' });
});

test('describeRequired: recursively processes array items', (t) => {
  const schema = {
    type: 'array',
    items: {
      type: 'object',
      properties: {
        gid:  { type: 'string' },
        name: { type: 'string' },
      },
      required: ['gid', 'name'],
    },
  };

  const result = describeRequired(schema);

  t.deepEqual(result.items.required, REQUIRED_TYPE);
  t.deepEqual(result.items.properties.gid,  { type: 'string' });
  t.deepEqual(result.items.properties.name, { type: 'string' });
});

test('describeRequired: items:false (empty array) is left untouched', (t) => {
  const schema = { type: 'array', items: false };
  t.deepEqual(describeRequired(schema), { type: 'array', items: false });
});

test('describeRequired: asana workspaces — required arrays replaced with type descriptions', (t) => {
  const result = describeRequired(inferSchema(asanaFixture));

  // top-level required converted
  t.deepEqual(result.required, REQUIRED_TYPE);

  // array items required converted
  const item = result.properties.data.items;
  t.deepEqual(item.required, REQUIRED_TYPE);

  // property schemas still present
  t.truthy(item.properties.gid);
  t.truthy(item.properties.resource_type);
  t.truthy(item.properties.name);
});

test('describeRequired: calendar — no raw required arrays remain anywhere in output', (t) => {
  const check = (node, path = '') => {
    if (!node || typeof node !== 'object') return;
    if (Array.isArray(node)) { node.forEach((v, i) => check(v, `${path}[${i}]`)); return; }
    if ('required' in node) {
      t.false(Array.isArray(node.required), `found raw required[] at ${path}`);
    }
    for (const [k, v] of Object.entries(node)) check(v, `${path}.${k}`);
  };
  check(describeRequired(inferSchema(calendarFixture)));
});

// ── Golden-file tests: full schema match ──────────────────────────────────────
//
// Compare the complete inferred schema against a committed golden file.
// To regenerate: node -e "import('@jsonhero/schema-infer').then(m => ...)" or run
// the generation script at the top of this file.

test('inferSchema: copilot — full schema matches golden file', (t) => {
  t.deepEqual(inferSchema(copilotFixture), copilotExpectedSchema);
});

test('inferSchema: calendar — full schema matches golden file', (t) => {
  t.deepEqual(inferSchema(calendarFixture), calendarExpectedSchema);
});

test('inferSchema: asana workspaces — full schema matches golden file', (t) => {
  t.deepEqual(inferSchema(asanaFixture), asanaExpectedSchema);
});
