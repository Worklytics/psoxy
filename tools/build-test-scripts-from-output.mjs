#!/usr/bin/env node
/**
 * Build Psoxy connector test scripts from Terraform outputs.
 *
 * Synthesizes the same test-*.sh / test-all.sh scripts that Terraform writes via local_file,
 * using standard root outputs (connector instances, aws_region, repo_base_dir, etc.).
 */

import { execFileSync } from 'node:child_process';
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const __dirname = path.dirname(fileURLToPath(import.meta.url));

function parseArgs(argv) {
  const args = { tfDir: process.cwd(), outputDir: null, help: false };
  const positional = [];
  for (const arg of argv) {
    if (arg === '-h' || arg === '--help') {
      args.help = true;
    } else {
      positional.push(arg);
    }
  }
  if (positional[0]) args.tfDir = path.resolve(positional[0]);
  args.outputDir = path.resolve(positional[1] ?? args.tfDir);
  return args;
}

function usage() {
  return `Build Psoxy connector test scripts from Terraform outputs.

Usage:
  build-test-scripts-from-output.mjs [terraform-config-dir] [output-dir]

Arguments:
  terraform-config-dir  Terraform working directory (default: cwd)
  output-dir            Where to write scripts (default: terraform-config-dir)

Environment:
  PSOXY_BASE_DIR   Override repo_base_dir Terraform output when synthesizing scripts
  TF_WORKSPACE     Terraform workspace to select before reading outputs

Required Terraform outputs (from examples):
  api_connector_instances, bulk_connector_instances, webhook_collector_instances
  AWS: caller_role_arn, aws_region, repo_base_dir
  GCP: repo_base_dir; webhook collectors include batch_scheduler_job_id when applicable
`;
}

function runTerraform(tfDir, args) {
  return execFileSync('terraform', ['-chdir=' + tfDir, ...args], {
    encoding: 'utf8',
    stdio: ['ignore', 'pipe', 'pipe'],
  });
}

function readTerraformOutputs(tfDir) {
  if (process.env.TF_WORKSPACE) {
    runTerraform(tfDir, ['workspace', 'select', process.env.TF_WORKSPACE]);
  }
  let raw;
  try {
    raw = runTerraform(tfDir, ['output', '-json']);
  } catch (err) {
    throw new Error(`terraform output -json failed in ${tfDir}: ${err.stderr || err.message}`);
  }
  return unwrapOutputs(raw.trim() ? JSON.parse(raw) : {});
}

function unwrapOutputs(wrapped) {
  const out = {};
  for (const [key, value] of Object.entries(wrapped)) {
    out[key] = value?.value;
  }
  return out;
}

function resolvePsoxyBaseDir(outputs, tfDir) {
  const explicit = process.env.PSOXY_BASE_DIR || outputs.repo_base_dir;
  if (explicit) {
    const resolved = path.isAbsolute(explicit) ? explicit : path.resolve(tfDir, explicit);
    return ensureTrailingSlash(resolved);
  }
  const jar = outputs.path_to_deployment_jar;
  if (typeof jar === 'string' && jar !== 'unknown' && !/^(s3|gs):\/\//.test(jar)) {
    const javaIdx = jar.indexOf('/java/');
    if (javaIdx > 0) return jar.slice(0, javaIdx + 1);
  }
  return ensureTrailingSlash(path.resolve(__dirname, '..'));
}

function ensureTrailingSlash(p) {
  return p.endsWith('/') ? p : `${p}/`;
}

function shellSingleQuote(value) {
  // Bash single quotes: backslash is literal; only ' is special (end, \', start).
  // split/join avoids String.replace, which CodeQL treats as an incomplete escape.
  return `'${String(value).split("'").join("'\\''")}'`;
}

function nodeCli(psoxyBaseDir, scriptName) {
  return `node ${shellSingleQuote(`${psoxyBaseDir}tools/psoxy-test/${scriptName}`)}`;
}

function headerArgPairs(request) {
  const headers = request.headers ?? {};
  const pairs = [];
  for (const [name, value] of Object.entries(headers)) {
    pairs.push('-H', `${name}: ${value}`);
  }
  return pairs;
}

function bashArrayLiteral(words) {
  if (words.length === 0) return '()';
  return `(${words.map((word) => shellSingleQuote(word)).join(' ')})`;
}

function buildScriptInvocation(request) {
  const words = [request.method, request.path];
  if (request.body != null) {
    words.push(request.content_type ?? 'application/json');
    words.push(typeof request.body === 'string' ? request.body : JSON.stringify(request.body));
  } else if (headerArgPairs(request).length > 0) {
    words.push('', '');
  }
  words.push(...headerArgPairs(request));
  return words.map((word) => shellSingleQuote(word)).join(' ');
}

function isIpHost(host) {
  if (!host) return false;
  const h = host.replace(/^\[/, '').replace(/\]$/, '');
  return /^(\d{1,3}\.){3}\d{1,3}$/.test(h) || h.includes(':');
}

function gcpCliCallFlags(endpointUrl) {
  let host;
  try {
    host = new URL(endpointUrl).hostname;
  } catch {
    return '';
  }
  const isCloudFunction = /\.(cloudfunctions\.net|run\.app)$/.test(host);
  const flags = [];
  if (!isCloudFunction) flags.push('-f gcp');
  if (isIpHost(host)) flags.push('--allow-insecure-tls');
  return flags.join(' ');
}

function detectPlatform(outputs) {
  if (outputs.deployment_platform === 'aws' || outputs.deployment_platform === 'gcp') {
    return outputs.deployment_platform;
  }
  if (outputs.webhook_test_caller_role_arn != null || outputs.caller_role_arn != null) return 'aws';
  if (outputs.artifacts_bucket_id != null) return 'gcp';
  const instances = [
    ...Object.values(outputs.api_connector_instances ?? {}),
    ...Object.values(outputs.bulk_connector_instances ?? {}),
    ...Object.values(outputs.webhook_collector_instances ?? {}),
  ];
  if (instances.some((inst) => inst?.cloud_function_name || inst?.batch_scheduler_job_id)) return 'gcp';
  if (instances.some((inst) => /cloudfunctions\.net|run\.app/.test(inst?.endpoint_url ?? ''))) return 'gcp';
  return 'aws';
}

function buildApiTestScript({
  platform,
  functionName,
  endpointUrl,
  testExamples,
  psoxyBaseDir,
  callerRoleArn,
  awsRegion,
}) {
  const requests = testExamples?.api_requests ?? [];
  const getRequests = requests.filter((r) => r.method === 'GET');
  const postRequests = requests.filter((r) => r.method === 'POST' && r.body != null);
  const defaultPath = getRequests[0]?.path ?? '';
  const defaultHeaderArgs = getRequests[0] ? headerArgPairs(getRequests[0]) : [];
  const impersonationParam =
    testExamples?.user_to_impersonate != null ? ` -i "${testExamples.user_to_impersonate}"` : '';
  const roleParam = platform === 'aws' && callerRoleArn ? ` -r "${callerRoleArn}"` : '';
  const regionParam = platform === 'aws' && awsRegion ? ` --region "${awsRegion}"` : '';
  const gcpFlags = platform === 'gcp' ? gcpCliCallFlags(endpointUrl) : '';
  const gcpFlagSuffix = gcpFlags ? ` ${gcpFlags}` : '';
  const commandCliCall = `${nodeCli(psoxyBaseDir, 'cli-call.js')}${roleParam}${regionParam}${gcpFlagSuffix}`;
  const supportsAsync = Boolean(testExamples?.supports_async);

  const getInvocations = getRequests.map((r) => ({
    ...r,
    script_invocation: buildScriptInvocation(r),
  }));
  const postInvocations = postRequests.map((r) => ({
    ...r,
    script_invocation: buildScriptInvocation(r),
  }));

  const lines = [
    '#!/bin/bash',
    '',
    'METHOD=${1:-"GET"}',
    `API_PATH=\${2:-${shellSingleQuote(defaultPath)}}`,
    'CONTENT_TYPE=${3:-""}',
    'BODY=${4:-""}',
    'if [ "$#" -gt 4 ]; then',
    '  shift 4',
    '  HEADER_FLAGS=("$@")',
    'else',
    `  HEADER_FLAGS=${bashArrayLiteral(defaultHeaderArgs)}`,
    'fi',
    '',
    `echo "Quick test of ${functionName} ..."`,
    '',
    '# Suppress "punycode" deprecation warnings from Node 21+',
    'export NODE_OPTIONS="${NODE_OPTIONS:+$NODE_OPTIONS }--no-deprecation"',
    '',
    `${commandCliCall} -u "${endpointUrl}/" --health-check`,
    'HEALTHCHECK_RC=$?',
    '',
    `${commandCliCall} -u "${endpointUrl}$API_PATH" ${impersonationParam} -m $METHOD -b "$BODY" "\${HEADER_FLAGS[@]}"`,
    'SYNC_CALL_RC=$?',
    '',
  ];

  if (supportsAsync) {
    lines.push(
      `${commandCliCall} -u "${endpointUrl}$API_PATH" ${impersonationParam} -m $METHOD -b "$BODY" "\${HEADER_FLAGS[@]}" --async`,
      'ASYNC_CALL_RC=$?',
    );
  } else {
    lines.push('ASYNC_CALL_RC=0');
  }

  lines.push(
    '',
    'echo "Invoke this script with any of the following as arguments to test other endpoints:"',
    '',
  );
  for (const r of getInvocations) {
    lines.push(`    printf '\\t%s\\n' ${shellSingleQuote(r.script_invocation)}`);
  }
  for (const r of postInvocations) {
    lines.push(`    printf '\\t%s\\n' ${shellSingleQuote(r.script_invocation)}`);
  }
  lines.push('', 'exit $(( HEALTHCHECK_RC + SYNC_CALL_RC + ASYNC_CALL_RC ))', '');
  return lines.join('\n');
}

function buildAwsBulkTestScript({
  instanceId,
  psoxyBaseDir,
  inputBucket,
  sanitizedBucket,
  exampleFiles,
  callerRoleArn,
  writeRoleArn,
  awsRegion,
}) {
  const paths = exampleFiles.map((f) => `${psoxyBaseDir}${f.path}`);
  const defaultPaths = paths.join(',');
  const roleLines = [
    writeRoleArn ? `    --write-role-to-assume ${writeRoleArn} \\` : '',
    callerRoleArn && String(callerRoleArn).includes(':role/') ? `    -r ${callerRoleArn} \\` : '',
  ].filter(Boolean);
  return `#!/bin/bash
FILE_PATH=\${1:-${defaultPaths}}
BLUE='\\e[0;34m'
NC='\\e[0m'
FAILED=0

printf "Quick test of \${BLUE}${instanceId}\${NC} ...\\n"

# Process multiple files separated by comma
IFS=',' read -ra FILES <<< "$FILE_PATH"
for FILE in "\${FILES[@]}"; do
  # trim whitespace
  FILE=$(echo "$FILE" | xargs)
  if [ -z "$FILE" ]; then continue; fi

  if [ ! -f "$FILE" ]; then
    printf "error: file not found: %s\\n" "$FILE" >&2
    FAILED=1
    continue
  fi
  
  printf "Testing file: $FILE\\n"
  ${nodeCli(psoxyBaseDir, 'cli-file-upload.js')} \\
    -f "$FILE" \\
    -d "AWS" \\
    -i "${inputBucket}" \\
    -o "${sanitizedBucket}" \\
${roleLines.join('\n')}${roleLines.length ? '\n' : ''}    --region "${awsRegion}"
  if [ $? -ne 0 ]; then
    FAILED=1
  fi
done

exit $FAILED
`;
}

function buildGcpBulkTestScript({
  functionName,
  psoxyBaseDir,
  inputBucket,
  sanitizedBucket,
  exampleFiles,
}) {
  const paths = exampleFiles.map((f) => `${psoxyBaseDir}${f.path}`);
  const defaultPaths = paths.join(',');
  return `#!/bin/bash
FILE_PATH=\${1:-${defaultPaths}}
BLUE='\\e[0;34m'
NC='\\e[0m'
FAILED=0

printf "Quick test of \${BLUE}${functionName}\${NC} ...\\n"

# Process multiple files separated by comma
IFS=',' read -ra FILES <<< "$FILE_PATH"
for FILE in "\${FILES[@]}"; do
  # trim whitespace
  FILE=$(echo "$FILE" | xargs)
  if [ -z "$FILE" ]; then continue; fi

  if [ ! -f "$FILE" ]; then
    printf "error: file not found: %s\\n" "$FILE" >&2
    FAILED=1
    continue
  fi
  
  printf "Testing file: $FILE\\n"
  ${nodeCli(psoxyBaseDir, 'cli-file-upload.js')} -f "$FILE" -d GCP -i ${inputBucket} -o ${sanitizedBucket}
  if [ $? -ne 0 ]; then
    FAILED=1
    continue
  fi

  if gzip -t "$FILE" 2>/dev/null; then
    printf "test file was compressed, so not testing compression as a separate case\\n"
  else
    printf "testing with compressed input file ... \\n"
    # extract the file name from the path
    TEST_FILE_NAME=/tmp/$(basename "$FILE").gz

    if ! gzip -c "$FILE" > "$TEST_FILE_NAME"; then
      printf "error: failed to compress file: %s\\n" "$FILE" >&2
      rm -f "$TEST_FILE_NAME"
      FAILED=1
      continue
    fi
    ${nodeCli(psoxyBaseDir, 'cli-file-upload.js')} -f "$TEST_FILE_NAME" -d GCP -i ${inputBucket} -o ${sanitizedBucket}
    NODE_EXIT=$?
    rm -f "$TEST_FILE_NAME"
    if [ $NODE_EXIT -ne 0 ]; then
      FAILED=1
    fi
  fi
done

exit $FAILED
`;
}

function webhookTestExamples(inst, platform) {
  const examples = Array.isArray(inst?.test_examples) ? inst.test_examples : [];
  const first = examples[0] ?? null;
  if (first?.signing_key_id) return examples;
  const keys = inst?.provisioned_auth_key_pairs;
  const raw = Array.isArray(keys) && keys.length > 0 ? keys[platform === 'aws' ? keys.length - 1 : 0] : null;
  if (!raw) return examples;
  const prefix = platform === 'aws' ? 'aws-kms:' : 'gcp-kms:';
  const signingKeyId = String(raw).startsWith(prefix) ? String(raw) : `${prefix}${raw}`;
  return [{ ...(first ?? {}), signing_key_id: signingKeyId }];
}

function decodeExamplePayload(testExamples) {
  const first = Array.isArray(testExamples) ? testExamples[0] : null;
  if (!first?.content_base64) return '{"test": "data"}';
  return Buffer.from(first.content_base64, 'base64').toString('utf8');
}

function buildAwsWebhookTestScript({
  functionName,
  endpointUrl,
  psoxyBaseDir,
  sanitizedBucket,
  testExamples,
  callerRoleArn,
  awsRegion,
}) {
  const example = Array.isArray(testExamples) ? testExamples[0] : null;
  const payload = shellSingleQuote(decodeExamplePayload(testExamples));
  const roleParam = callerRoleArn ? ` -r "${callerRoleArn}"` : '';
  const regionParam = awsRegion ? ` --region "${awsRegion}"` : '';
  const commandCliCall = `${nodeCli(psoxyBaseDir, 'cli-call.js')}${roleParam}${regionParam}`;
  let signingLines = '';
  if (example?.signing_key_id) {
    const arn = example.signing_key_id.replace(/^aws-kms:/, '');
    signingLines = `--signing-key "aws-kms:${arn}" \\
--identity-issuer "${endpointUrl}" \\
`;
  }
  // AWS collectors expose POST /collect; well-known endpoints stay on the base URL.
  let identityLine = '';
  if (example?.identity != null) {
    identityLine = `--identity-subject ${shellSingleQuote(String(example.identity))} \\
`;
  }
  return `#!/bin/bash
echo "Quick test of ${functionName} ..."

# Suppress "punycode" deprecation warnings from Node 21+
export NODE_OPTIONS="\${NODE_OPTIONS:+$NODE_OPTIONS }--no-deprecation"

${commandCliCall} -u "${endpointUrl}/.well-known/openid-configuration"
OPENID_CONFIG_RC=$?

${commandCliCall} -u "${endpointUrl}/.well-known/jwks.json"
JWKS_RC=$?

${commandCliCall} -u "${endpointUrl}/collect" --method POST \\
${signingLines}${identityLine}--verify-collection "${sanitizedBucket}" \\
--body ${payload}
COLLECTION_RC=$?

exit $(( OPENID_CONFIG_RC + JWKS_RC + COLLECTION_RC ))
`;
}

function buildGcpWebhookTestScript({
  functionName,
  endpointUrl,
  psoxyBaseDir,
  sanitizedBucket,
  testExamples,
  schedulerJob,
}) {
  const example = Array.isArray(testExamples) ? testExamples[0] : null;
  const payload = shellSingleQuote(decodeExamplePayload(testExamples));
  const commandCliCall = nodeCli(psoxyBaseDir, 'cli-call.js');
  let signingLines = '';
  if (example?.signing_key_id) {
    signingLines = `--signing-key "${example.signing_key_id}" \\
--identity-issuer "${endpointUrl}" \\
`;
  }
  let identityLine = '';
  if (example?.identity != null) {
    identityLine = `--identity-subject ${shellSingleQuote(String(example.identity))} \\
`;
  }
  const schedulerLine = schedulerJob ? `--scheduler-job "${schedulerJob}" \\\n` : '';
  return `#!/bin/bash
echo "Quick test of ${functionName} ..."

# Suppress "punycode" deprecation warnings from Node 21+
export NODE_OPTIONS="\${NODE_OPTIONS:+$NODE_OPTIONS }--no-deprecation"

${commandCliCall} -u "${endpointUrl}/.well-known/openid-configuration"
OPENID_CONFIG_RC=$?

${commandCliCall} -u "${endpointUrl}/.well-known/jwks.json"
JWKS_RC=$?

${commandCliCall} -u "${endpointUrl}/" --method POST \\
${signingLines}${identityLine}--verify-collection "${sanitizedBucket}" \\
${schedulerLine}--body ${payload}
COLLECTION_RC=$?

exit $(( OPENID_CONFIG_RC + JWKS_RC + COLLECTION_RC ))
`;
}

function apiScriptFilename(_platform, instanceKey) {
  return `test-${instanceKey}.sh`;
}

function bulkScriptFilename(instanceKey) {
  return `test-${instanceKey}.sh`;
}

function webhookScriptFilename(instanceKey) {
  return `test-${instanceKey}.sh`;
}

function materializeExampleFiles(_outputDir, _instanceKey, exampleFiles, psoxyBaseDir) {
  const baseDir = path.resolve(psoxyBaseDir);
  for (const file of exampleFiles) {
    if (!file?.content_base64 || !file?.path) continue;
    const target = path.resolve(baseDir, file.path);
    const relativeTarget = path.relative(baseDir, target);
    if (relativeTarget === '..' || relativeTarget.startsWith(`..${path.sep}`) || path.isAbsolute(relativeTarget)) {
      throw new Error(`Refusing to write example file outside base directory: ${file.path}`);
    }
    if (fs.existsSync(target)) continue;
    fs.mkdirSync(path.dirname(target), { recursive: true });
    fs.writeFileSync(target, Buffer.from(file.content_base64, 'base64'));
  }
}

function generateScriptsFromOutputs({ outputs, psoxyBaseDir, platform, awsRegion, outputDir }) {
  const scripts = {};

  const api = outputs.api_connector_instances ?? {};
  for (const [key, inst] of Object.entries(api)) {
    if (!inst?.endpoint_url) continue;
    const filename = apiScriptFilename(platform, key);
    const functionName = inst.cloud_function_name ?? key;
    scripts[filename] = buildApiTestScript({
      platform,
      functionName,
      endpointUrl: inst.endpoint_url.replace(/\/$/, ''),
      testExamples: inst.test_examples ?? {},
      psoxyBaseDir,
      callerRoleArn: outputs.caller_role_arn,
      awsRegion,
    });
  }

  const bulk = outputs.bulk_connector_instances ?? {};
  for (const [key, inst] of Object.entries(bulk)) {
    const exampleFiles = inst.example_files ?? [];
    materializeExampleFiles(outputDir, key, exampleFiles, psoxyBaseDir);
    const filename = bulkScriptFilename(key);
    if (platform === 'gcp') {
      scripts[filename] = buildGcpBulkTestScript({
        functionName: key,
        psoxyBaseDir,
        inputBucket: inst.input_bucket,
        sanitizedBucket: inst.sanitized_bucket,
        exampleFiles,
      });
    } else {
      scripts[filename] = buildAwsBulkTestScript({
        instanceId: key,
        psoxyBaseDir,
        inputBucket: inst.input_bucket,
        sanitizedBucket: inst.sanitized_bucket,
        exampleFiles,
        callerRoleArn: inst.aws_principal_arn_when_testing || outputs.caller_role_arn,
        writeRoleArn: inst.aws_write_role_to_assume_when_testing,
        awsRegion,
      });
    }
  }

  const webhooks = outputs.webhook_collector_instances ?? {};
  for (const [key, inst] of Object.entries(webhooks)) {
    if (!inst?.endpoint_url) continue;
    const filename = webhookScriptFilename(key);
    const endpointUrl = inst.endpoint_url.replace(/\/$/, '');
    if (platform === 'gcp') {
      scripts[filename] = buildGcpWebhookTestScript({
        functionName: key,
        endpointUrl,
        psoxyBaseDir,
        sanitizedBucket: inst.sanitized_bucket,
        testExamples: webhookTestExamples(inst, platform),
        schedulerJob: inst.batch_scheduler_job_id,
      });
    } else {
      scripts[filename] = buildAwsWebhookTestScript({
        functionName: key,
        endpointUrl,
        psoxyBaseDir,
        sanitizedBucket: inst.sanitized_bucket,
        testExamples: webhookTestExamples(inst, platform),
        callerRoleArn: outputs.webhook_test_caller_role_arn || outputs.caller_role_arn,
        awsRegion,
      });
    }
  }

  return scripts;
}

function buildTestAllScript() {
  // Same discovery script Terraform writes. Listing connector outputs here created destroy cycles.
  return `#!/bin/bash

# Run all per-connector test-*.sh scripts in the current directory (alphabetical).
# Per-connector scripts are generated from Terraform outputs.

found=0
while IFS= read -r script; do
  found=1
  echo "Running \${script} ..."
  "\${script}"
done < <(find . -maxdepth 1 -type f -name 'test-*.sh' ! -name 'test-all.sh' | LC_ALL=C sort)

if [ "$found" -eq 0 ]; then
  echo "No per-connector test-*.sh scripts found."
  exit 1
fi
`;
}

function writeScripts(outputDir, scripts) {
  fs.mkdirSync(outputDir, { recursive: true });
  const written = [];
  for (const [filename, content] of Object.entries(scripts)) {
    if (filename.includes('/') || filename.includes('..')) {
      throw new Error(`Refusing to write unsafe script path: ${filename}`);
    }
    const target = path.join(outputDir, filename);
    fs.writeFileSync(target, content, { mode: 0o755 });
    fs.chmodSync(target, 0o755);
    written.push(target);
  }
  return written;
}

function main() {
  const args = parseArgs(process.argv.slice(2));
  if (args.help) {
    process.stdout.write(usage());
    process.exit(0);
  }

  const outputs = readTerraformOutputs(args.tfDir);
  const platform = detectPlatform(outputs);
  const awsRegion = outputs.aws_region;
  if (platform === 'aws' && !awsRegion) {
    throw new Error(
      'Terraform output aws_region is required for AWS deployments. Add: output "aws_region" { value = var.aws_region }',
    );
  }

  const psoxyBaseDir = resolvePsoxyBaseDir(outputs, args.tfDir);
  const generated = generateScriptsFromOutputs({
    outputs,
    psoxyBaseDir,
    platform,
    awsRegion,
    outputDir: args.outputDir,
  });

  if (Object.keys(generated).length === 0) {
    process.stderr.write('No connector instance outputs found to synthesize test scripts from.\n');
    process.exit(1);
  }

  generated['test-all.sh'] = buildTestAllScript();

  const written = writeScripts(args.outputDir, generated);
  process.stdout.write(`Wrote ${written.length} script(s) to ${args.outputDir} (from Terraform outputs).\n`);
  for (const file of written.sort()) {
    process.stdout.write(`  ${file}\n`);
  }
}

try {
  main();
} catch (err) {
  process.stderr.write(`${err.message}\n`);
  process.exit(1);
}
