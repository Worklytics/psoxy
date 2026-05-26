/**
 * Integration tests for the CLI (cli-schema.js).
 *
 * These tests spawn the CLI as a child process so they exercise the full
 * argument-parsing, validation, and output logic without mocking the
 * internals of lib/schema.js.
 */

import test from 'ava';
import { execFile } from 'node:child_process';
import { writeFile, mkdir, rm } from 'node:fs/promises';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { fileURLToPath } from 'node:url';

const CLI = fileURLToPath(new URL('../cli-schema.js', import.meta.url));

/**
 * Run the CLI with the given args. Returns { stdout, stderr, exitCode }.
 * Never rejects — exit code 1 is a normal failure case we want to assert on.
 * Spawn errors (e.g. node not found) resolve with exitCode 1 and the error
 * message in stderr so tests fail deterministically rather than hanging.
 */
async function runCli(args, { stdin } = {}) {
  return new Promise((resolve) => {
    const child = execFile('node', [CLI, ...args]);

    let stdout = '';
    let stderr = '';
    child.stdout.on('data', (d) => (stdout += d));
    child.stderr.on('data', (d) => (stderr += d));

    if (stdin !== undefined) {
      child.stdin.write(stdin);
      child.stdin.end();
    }

    child.on('error', (err) => resolve({ stdout, stderr: stderr + err.message, exitCode: 1 }));
    child.on('close', (code) => resolve({ stdout, stderr, exitCode: code ?? 0 }));
  });
}

// ── Shared temp dir ───────────────────────────────────────────────────────

let TMP_DIR;

test.before(async () => {
  TMP_DIR = join(tmpdir(), `schema-tool-cli-test-${process.pid}`);
  await mkdir(TMP_DIR, { recursive: true });
});

test.after.always(async () => {
  await rm(TMP_DIR, { recursive: true, force: true });
});

// ── Validation errors ──────────────────────────────────────────────────────

test('error: no --endpoint and no --input', async (t) => {
  const { stderr, exitCode } = await runCli([]);
  t.is(exitCode, 1);
  t.true(stderr.includes('--endpoint') || stderr.includes('--input'));
});

test('error: --endpoint and --input are mutually exclusive', async (t) => {
  const file = join(TMP_DIR, 'dummy.json');
  await writeFile(file, '{}');

  const { stderr, exitCode } = await runCli([
    '--endpoint', 'https://api.example.com/',
    '--auth', 'tok',
    '--input', file,
  ]);

  t.is(exitCode, 1);
  t.true(stderr.includes('mutually exclusive'));
});

test('error: --endpoint without --auth', async (t) => {
  const { stderr, exitCode } = await runCli(['--endpoint', 'https://api.example.com/']);
  t.is(exitCode, 1);
  t.true(stderr.includes('--auth'));
});

// ── --input: file mode ─────────────────────────────────────────────────────

test('--input: JSON object — outputs schema (no headers key)', async (t) => {
  const file = join(TMP_DIR, 'obj.json');
  await writeFile(file, JSON.stringify({ id: 1, name: 'Alice' }));

  const { stdout, exitCode } = await runCli(['--input', file]);

  t.is(exitCode, 0);
  const output = JSON.parse(stdout);
  // No headers key — we never made an HTTP request
  t.false(Object.hasOwn(output, 'headers'));
  t.truthy(output.schema);
  t.is(output.schema.type, 'object');
});

test('--input: JSON array — outputs array schema', async (t) => {
  const file = join(TMP_DIR, 'arr.json');
  await writeFile(file, JSON.stringify([{ id: 1 }, { id: 2 }]));

  const { stdout, exitCode } = await runCli(['--input', file]);

  t.is(exitCode, 0);
  const output = JSON.parse(stdout);
  t.is(output.schema.type, 'array');
});

test('--input: JSONL file — outputs array schema', async (t) => {
  const file = join(TMP_DIR, 'events.jsonl');
  await writeFile(file, '{"ts":"2024-01-01","event":"login"}\n{"ts":"2024-01-02","event":"logout"}\n');

  const { stdout, exitCode } = await runCli(['--input', file]);

  t.is(exitCode, 0);
  const output = JSON.parse(stdout);
  t.is(output.schema.type, 'array');
});

test('--input --raw: prints raw file content unchanged', async (t) => {
  const content = JSON.stringify({ id: 42, name: 'Bob' });
  const file = join(TMP_DIR, 'raw.json');
  await writeFile(file, content);

  const { stdout, exitCode } = await runCli(['--input', file, '--raw']);

  t.is(exitCode, 0);
  // stdout should be the raw content (console.log adds a trailing newline)
  t.is(stdout.trim(), content);
});

test('--input: invalid JSON — exits 1 with error message', async (t) => {
  const file = join(TMP_DIR, 'bad.json');
  await writeFile(file, 'not json at all');

  const { stderr, exitCode } = await runCli(['--input', file]);

  t.is(exitCode, 1);
  t.true(stderr.includes('not valid JSON'));
});

test('--input: missing file — treated as inline content, fails with JSON error', async (t) => {
  // A value that looks like a path but doesn't exist is treated as inline content.
  // Since it isn't valid JSON it should fail with a parse error, not a file error.
  const { stderr, exitCode } = await runCli(['--input', '/nonexistent/path/file.json']);
  t.is(exitCode, 1);
  t.true(stderr.includes('not valid JSON'));
  t.false(stderr.includes('Could not read input'));
});

test('--input: inline JSON object string — outputs schema without needing a file', async (t) => {
  const { stdout, exitCode } = await runCli(['--input', '{"id":1,"name":"Alice"}']);
  t.is(exitCode, 0);
  const output = JSON.parse(stdout);
  t.is(output.schema.type, 'object');
  t.false(Object.hasOwn(output, 'headers'));
});

test('--input: inline JSON array string — outputs array schema', async (t) => {
  const { stdout, exitCode } = await runCli(['--input', '[{"x":1},{"x":2}]']);
  t.is(exitCode, 0);
  const output = JSON.parse(stdout);
  t.is(output.schema.type, 'array');
});

test('--input: inline JSON string with --raw — passes content through unchanged', async (t) => {
  const payload = '{"id":99}';
  const { stdout, exitCode } = await runCli(['--input', payload, '--raw']);
  t.is(exitCode, 0);
  t.is(stdout.trim(), payload);
});

test('--input: invalid inline string — exits 1 with JSON error', async (t) => {
  const { stderr, exitCode } = await runCli(['--input', 'this is not json']);
  t.is(exitCode, 1);
  t.true(stderr.includes('not valid JSON'));
});

// ── --input: stdin mode (path = '-') ──────────────────────────────────────

test('--input -: reads JSON from stdin — outputs schema', async (t) => {
  const payload = JSON.stringify([{ userId: 'u1', score: 99 }]);

  const { stdout, exitCode } = await runCli(['--input', '-'], { stdin: payload });

  t.is(exitCode, 0);
  const output = JSON.parse(stdout);
  t.is(output.schema.type, 'array');
  t.false(Object.hasOwn(output, 'headers'));
});

test('--input - --raw: passes stdin content through unchanged', async (t) => {
  const payload = '{"raw":true}';

  const { stdout, exitCode } = await runCli(['--input', '-', '--raw'], { stdin: payload });

  t.is(exitCode, 0);
  t.is(stdout.trim(), payload);
});
