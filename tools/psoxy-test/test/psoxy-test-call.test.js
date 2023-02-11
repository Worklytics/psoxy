import test from 'ava';
import * as td from 'testdouble';
import aws from '../lib/aws.js';
import gcp from '../lib/gcp.js';
import psoxyTestCall from '../psoxy-test-call.js';

let awsCall, gcpCall;
test.beforeEach(async () => {
  // double test dependencies: only replace "call" methods, we expect every
  // other function in aws and gcp modules to behave as intended
  // (e.g. isValidURL)
  awsCall = td.replace(aws, 'call');
  gcpCall = td.replace(gcp, 'call');
});

test.afterEach(() => td.reset());

test('Psoxy Test Call: AWS - API Gateway and Lambda', async (t) => {
  const options = {
    url: 'https://foo.execute-api.us-east-1.amazonaws.com/live/psoxy-instance'
  }
  // should throw error, since actual call is stubbed (undefined result)
  await t.throwsAsync(async () => psoxyTestCall(options), { instanceOf: Error });

  options.url = 'https://foo.lambda-url.us-east-1.on.aws/';
  await t.throwsAsync(async () => psoxyTestCall(options), { instanceOf: Error });

  td.verify(awsCall(options), {times: 2});
});

test('Psoxy Test Call: GCP', async (t) => {
  const options = {
    url: 'https://foo.cloudfunctions.net'
  }
  await t.throwsAsync(async () => psoxyTestCall(options), { instanceOf: Error });
  td.verify(gcpCall(options));
});

test('Psoxy Test Call: AWS - force option', async (t) => {
  const options = {
    url: 'https://foo.bar',
    force: 'aws'
  }
  await t.throwsAsync(async () => psoxyTestCall(options), { instanceOf: Error });
  td.verify(gcpCall(options), {times: 0});
  td.verify(awsCall(options));
});

test('Psoxy Test Call: GCP - force option', async (t) => {
  const options = {
    url: 'https://foo.bar',
    force: 'gcp'
  }
  await t.throwsAsync(async () => psoxyTestCall(options), { instanceOf: Error });
  td.verify(gcpCall(options));
  td.verify(awsCall(options), {times: 0});
});

test('Psoxy Test Call: unrecognized force option', async (t) => {
  const options = {
    url: 'https://foo.bar',
    force: 'foo'
  }
  await t.throwsAsync(async () => psoxyTestCall(options), { instanceOf: Error });
  td.verify(gcpCall(options), {times: 0});
  td.verify(awsCall(options), {times: 0});
});

test('Psoxy Test Call: not recognized URL', async (t) => {
  const options = {
    url: 'https://foo.bar'
  }
  await t.throwsAsync(async () => psoxyTestCall(options), { instanceOf: Error });
  td.verify(gcpCall(), {times: 0});
  td.verify(awsCall(), {times: 0});
});
