import test from 'ava';
import * as td from 'testdouble';
import { compareContent } from '../lib/utils.js';

test('compareContent: strict equality', (t) => {
    const logger = td.object({ info: () => {}, error: () => {}, success: () => {} });
    const items = [{ id: 1, name: 'foo' }];
    const expected = JSON.stringify({ id: 1, name: 'foo' });

    t.true(compareContent(items, expected, logger));
});

test('compareContent: pseudonymized equality (ignore actor.id)', (t) => {
    const logger = td.object({ info: () => {}, error: () => {}, success: () => {} });
    const items = [{ actor: { id: 'generated-id', name: 'user' }, action: 'login' }];
    const expected = JSON.stringify({ actor: { id: 'original-id', name: 'user' }, action: 'login' });

    t.true(compareContent(items, expected, logger));
});

test('compareContent: mismatch', (t) => {
    const logger = td.object({ info: () => {}, error: () => {}, success: () => {} });
    const items = [{ id: 1, name: 'foo' }];
    const expected = JSON.stringify({ id: 1, name: 'bar' });

    t.false(compareContent(items, expected, logger));
});

test('compareContent: empty items', (t) => {
    const logger = td.object({ info: () => {}, error: () => {}, success: () => {} });
    const items = [];
    const expected = JSON.stringify({ id: 1 });

    t.false(compareContent(items, expected, logger));
});

test('compareContent: expectedContent as object', (t) => {
    const logger = td.object({ info: () => {}, error: () => {}, success: () => {} });
    const items = [{ id: 1, name: 'foo' }];
    const expected = { id: 1, name: 'foo' };

    t.true(compareContent(items, expected, logger));
});
