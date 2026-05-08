import mimicFunction from 'mimic-function';
const maxTimeoutValue = 2_147_483_647;
const cacheStore = new WeakMap();
const cacheTimerStore = new WeakMap();
const cacheKeyStore = new WeakMap();
function getValidCacheItem(cache, key) {
    const item = cache.get(key);
    if (!item) {
        return undefined;
    }
    if (item.maxAge <= Date.now()) {
        cache.delete(key);
        return undefined;
    }
    return item;
}
/**
[Memoize](https://en.wikipedia.org/wiki/Memoization) functions - An optimization used to speed up consecutive function calls by caching the result of calls with identical input.

@param function_ - The function to be memoized.

@example
```
import memoize from 'memoize';

let index = 0;
const counter = () => ++index;
const memoized = memoize(counter);

memoized('foo');
//=> 1

// Cached as it's the same argument
memoized('foo');
//=> 1

// Not cached anymore as the arguments changed
memoized('bar');
//=> 2

memoized('bar');
//=> 2
```
*/
export default function memoize(function_, { cacheKey, cache = new Map(), maxAge, } = {}) {
    if (maxAge === 0) {
        return function_;
    }
    if (typeof maxAge === 'number' && Number.isFinite(maxAge)) {
        if (maxAge > maxTimeoutValue) {
            throw new TypeError(`The \`maxAge\` option cannot exceed ${maxTimeoutValue}.`);
        }
        if (maxAge < 0) {
            throw new TypeError('The `maxAge` option should not be a negative number.');
        }
    }
    const memoized = function (...arguments_) {
        const key = cacheKey ? cacheKey(arguments_) : arguments_[0];
        const cacheItem = getValidCacheItem(cache, key);
        if (cacheItem) {
            return cacheItem.data;
        }
        const result = function_.apply(this, arguments_);
        const computedMaxAge = typeof maxAge === 'function' ? maxAge(...arguments_) : maxAge;
        if (computedMaxAge !== undefined && computedMaxAge !== Number.POSITIVE_INFINITY) {
            if (!Number.isFinite(computedMaxAge)) {
                throw new TypeError('The `maxAge` function must return a finite number, `0`, or `Infinity`.');
            }
            if (computedMaxAge <= 0) {
                return result; // Do not cache
            }
            if (computedMaxAge > maxTimeoutValue) {
                throw new TypeError(`The \`maxAge\` function result cannot exceed ${maxTimeoutValue}.`);
            }
        }
        cache.set(key, {
            data: result,
            maxAge: (computedMaxAge === undefined || computedMaxAge === Number.POSITIVE_INFINITY)
                ? Number.POSITIVE_INFINITY
                : Date.now() + computedMaxAge,
        });
        if (computedMaxAge !== undefined && computedMaxAge !== Number.POSITIVE_INFINITY) {
            const timer = setTimeout(() => {
                cache.delete(key);
                cacheTimerStore.get(memoized)?.delete(timer);
            }, computedMaxAge);
            // eslint-disable-next-line @typescript-eslint/no-unsafe-call
            timer.unref?.();
            const timers = cacheTimerStore.get(memoized) ?? new Set();
            timers.add(timer);
            cacheTimerStore.set(memoized, timers);
        }
        return result;
    };
    mimicFunction(memoized, function_, {
        ignoreNonConfigurable: true,
    });
    cacheStore.set(memoized, cache);
    cacheKeyStore.set(memoized, (cacheKey ?? ((arguments_) => arguments_[0])));
    return memoized;
}
/**
@returns A [decorator](https://github.com/tc39/proposal-decorators) to memoize class methods or static class methods.

@example
```
import {memoizeDecorator} from 'memoize';

class Example {
    index = 0

    @memoizeDecorator()
    counter() {
        return ++this.index;
    }
}

class ExampleWithOptions {
    index = 0

    @memoizeDecorator({maxAge: 1000})
    counter() {
        return ++this.index;
    }
}
```
*/
export function memoizeDecorator(options = {}) {
    const instanceMap = new WeakMap();
    return (target, propertyKey, descriptor) => {
        const input = target[propertyKey]; // eslint-disable-line @typescript-eslint/no-unsafe-assignment
        if (typeof input !== 'function') {
            throw new TypeError('The decorated value must be a function');
        }
        delete descriptor.value;
        delete descriptor.writable;
        descriptor.get = function () {
            if (!instanceMap.has(this)) {
                const value = memoize(input, options);
                instanceMap.set(this, value);
                return value;
            }
            return instanceMap.get(this);
        };
    };
}
/**
Clear all cached data of a memoized function.

@param function_ - The memoized function.
*/
export function memoizeClear(function_) {
    const cache = cacheStore.get(function_);
    if (!cache) {
        throw new TypeError('Can\'t clear a function that was not memoized!');
    }
    if (typeof cache.clear !== 'function') {
        throw new TypeError('The cache Map can\'t be cleared!');
    }
    cache.clear();
    for (const timer of cacheTimerStore.get(function_) ?? []) {
        clearTimeout(timer);
    }
    cacheTimerStore.delete(function_);
}
/**
Check if a specific set of arguments is cached for a memoized function.

@param function_ - The memoized function.
@param arguments_ - The arguments to check.
@returns `true` if the arguments are cached and not expired, `false` otherwise.

Uses the same argument processing as the memoized function, including any custom `cacheKey` function.

@example
```
import memoize, {memoizeIsCached} from 'memoize';

const expensive = memoize((a, b) => a + b, {cacheKey: JSON.stringify});
expensive(1, 2);

memoizeIsCached(expensive, 1, 2);
//=> true

memoizeIsCached(expensive, 3, 4);
//=> false
```
*/
export function memoizeIsCached(function_, ...arguments_) {
    const cacheKey = cacheKeyStore.get(function_);
    if (!cacheKey) {
        return false;
    }
    const cache = cacheStore.get(function_);
    const key = cacheKey(arguments_);
    const item = getValidCacheItem(cache, key);
    return item !== undefined;
}
