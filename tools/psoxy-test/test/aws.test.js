import test from 'ava';
import * as td from 'testdouble';

const LAMBDA_URL = 'https://foo.lambda-url.us-east-1.on.aws/';

test.beforeEach(async t => {
  t.context.utils = await td.replaceEsm('../lib/utils.js'); 
  t.context.subject = (await import('../lib/aws.js')).default;
});

test.afterEach(() => td.reset());

test('isValidURL URL', t => {
  const aws = t.context.subject;
  t.true(aws.isValidURL(LAMBDA_URL));
  t.true(aws.isValidURL(new URL(LAMBDA_URL)));

  t.false(aws.isValidURL('http://foo.com'));
  t.throws(() => { aws.isValidURL('foo'); }, {instanceOf: TypeError});
});

test('Psoxy call: missing role throws error', async t => {
  const aws = t.context.subject;
  await t.throwsAsync(async () => aws.call({
    url: LAMBDA_URL,
  }), {
    instanceOf: Error, 
    message: 'Role is a required option for AWS'
  });
});

test('Psoxy call: works as expected', async t => {
  const aws = t.context.subject;
  const utils = t.context.utils;

  // Fake assume role and AWS request signing
  const roleArn = 'arn:aws:iam::[accountId]:role/[roleName]';
  const command = `aws sts assume-role --role-arn ${roleArn} --duration 900 --role-session-name lambda_test`
  const options = {
    url: LAMBDA_URL,
    role: roleArn,
  };
  const credentials = {
    Credentials: {
      AccessKeyId: 'foo',
      SecretAccessKey: 'bar',
      SessionToken: 'baz',
    }
  };
  const signedRequest = {
    headers: {
      Host: 'aws-host',
      'X-Amz-Security-Token': 'token',
      'X-Amz-Date': '20221014T172512Z',
      Authorization: 'AWS4-HMAC-SHA256 ...'
    },
    host: 'aws-host',
    path: '/foo',
    region: 'us-east-1',
    service: 'lambda',
  }
  
  // notice that `executeCommand` will return credentials wrapped and as 
  // String; in contrast to `assumeRole` that will parse and unwrap
  td.when(utils.executeCommand(command))
    .thenReturn(JSON.stringify(credentials));

  // sign request using credentials
  td.when(utils.signAWSRequestURL(td.matchers.isA(URL), 
    credentials.Credentials))
    .thenReturn(signedRequest);

  // Pathless URL: result should contain error
  td.when(utils.request(td.matchers.isA(URL), 
    td.matchers.contains(signedRequest.headers))).thenReturn({
      status: 500, 
      error: 'BLOCKED_BY_RULES',
    });

  let result = await aws.call(options);
  t.is(result.error, 'BLOCKED_BY_RULES');

  // Valid URL
  options.url+= '/api/path';
  td.when(utils.request(td.matchers.isA(URL), 
    td.matchers.contains(signedRequest.headers)))
      .thenReturn({ status: 200 });

  result = await aws.call(options);
  t.is(result.status, 200);
});

