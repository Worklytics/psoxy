/**
 * Concurrency test runner for psoxy-test CLI.
 *
 * Fires N copies of a request simultaneously using Promise.all and reports
 * per-request results, timing, and cross-contamination checks.
 */

import chalk from 'chalk';

const MAX_CONCURRENCY = 5;

/**
 * @typedef {Object} ConcurrencyResult
 * @property {number} index - Request index (0-based)
 * @property {number} status - HTTP status code
 * @property {number} latencyMs - Request latency in milliseconds
 * @property {boolean} passed - Whether the request was successful
 * @property {string} [error] - Error message if failed
 * @property {*} [data] - Response data
 */

/**
 * Run a concurrency test by firing N copies of the same request simultaneously.
 *
 * @param {Object} options - CLI options (same shape as psoxyTestCall expects)
 * @param {Function} callFn - The async function to call (psoxyTestCall)
 * @param {Object} logger - Logger instance
 * @returns {Object} Summary result
 */
export async function runConcurrencyTest(options, callFn, logger) {
  const n = Math.min(Math.max(options.concurrency, 2), MAX_CONCURRENCY);

  if (options.concurrency > MAX_CONCURRENCY) {
    logger.warn(`Concurrency capped at ${MAX_CONCURRENCY} (requested ${options.concurrency})`);
  }

  logger.info(`Running concurrency test: ${n} simultaneous requests to ${options.url}`);

  // Fire all N requests at the same time
  const promises = Array.from({ length: n }, (_, i) => {
    const start = Date.now();
    return callFn({ ...options, verbose: false })
      .then(result => ({
        index: i,
        status: result.status,
        latencyMs: Date.now() - start,
        passed: result.status >= 200 && result.status < 300,
        data: result.data,
      }))
      .catch(error => ({
        index: i,
        status: 0,
        latencyMs: Date.now() - start,
        passed: false,
        error: error.message,
      }));
  });

  const results = await Promise.all(promises);

  // Compute stats
  const passed = results.filter(r => r.passed);
  const failed = results.filter(r => !r.passed);
  const latencies = results.map(r => r.latencyMs).sort((a, b) => a - b);
  const p50 = latencies[Math.floor(latencies.length * 0.5)];
  const p95 = latencies[Math.floor(latencies.length * 0.95)];

  // Cross-contamination check: for identical requests, all successful responses should match
  let contamination = false;
  const successfulData = passed
    .map(r => typeof r.data === 'string' ? r.data : JSON.stringify(r.data))
    .filter(Boolean);

  if (successfulData.length > 1) {
    const reference = successfulData[0];
    const mismatches = successfulData.filter(d => d !== reference);
    if (mismatches.length > 0) {
      contamination = true;
    }
  }

  // Report
  console.log('');
  console.log(chalk.bold.underline('Concurrency Test Results'));
  console.log('');

  for (const r of results) {
    const statusColor = r.passed ? chalk.green : chalk.red;
    const statusIcon = r.passed ? '✓' : '✗';
    const errorInfo = r.error ? ` — ${r.error}` : '';
    console.log(`  ${statusIcon} Request ${r.index + 1}: ${statusColor(r.status)} (${r.latencyMs}ms)${errorInfo}`);
  }

  console.log('');
  console.log(chalk.bold('Summary:'));
  console.log(`  Total:    ${results.length}`);
  console.log(`  Passed:   ${chalk.green(passed.length)}`);
  console.log(`  Failed:   ${failed.length > 0 ? chalk.red(failed.length) : '0'}`);
  console.log(`  p50:      ${p50}ms`);
  console.log(`  p95:      ${p95}ms`);

  if (contamination) {
    console.log(`  ${chalk.bold.red('⚠ CROSS-CONTAMINATION DETECTED:')} responses differ between concurrent requests`);
  } else if (successfulData.length > 1) {
    console.log(`  ${chalk.green('✓')} No cross-contamination: all ${successfulData.length} successful responses are identical`);
  }

  console.log('');

  // Return summary
  return {
    status: failed.length === 0 ? 200 : 500,
    statusMessage: failed.length === 0 ? 'All concurrent requests passed' : `${failed.length}/${results.length} requests failed`,
    data: {
      total: results.length,
      passed: passed.length,
      failed: failed.length,
      p50,
      p95,
      contamination,
      results,
    },
  };
}
