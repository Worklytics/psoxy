import test from 'ava';
//import * as td from 'testdouble';
import aws from '../lib/aws.js';
import { createRequire } from 'module';
import { time } from 'console';
const require = createRequire(import.meta.url);

const sample = require('./cloudwatch-log-events-sample.json').events;

test('Psoxy Logs: parse log events command result', (t) => {
  t.deepEqual([], aws.parseLogEvents(null));
  t.deepEqual([], aws.parseLogEvents({}));

  const result = aws.parseLogEvents(sample);

  t.is(result.length, sample.length);
  // It doesn't modify the message
  t.is(result[0].message, sample[0].message);
  
  // It formats the timestamp
  t.not(result[0].timestamp, sample[0].timestamp);

  // It creates a new property "level" if the starting of the message matches
  // "SEVERE" or "WARNING" logging Java levels, and removes the level keyword 
  // from the original message
  const severePrefix = 'SEVERE';
  const severeEventIndex = sample
    .findIndex(event => event.message.startsWith(severePrefix));
  t.is(result[severeEventIndex].level, severePrefix);
  t.not(result[severeEventIndex].message.startsWith(severePrefix));
  
  const warningPrefix = 'WARNING';
  const warningEventIndex = sample
    .findIndex(event => event.message.startsWith(warningPrefix));
  t.is(result[warningEventIndex].level, warningPrefix);
  t.not(result[warningEventIndex].message.startsWith(warningPrefix));  
});