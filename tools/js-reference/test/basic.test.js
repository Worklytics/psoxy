const test = require('ava');
const { hashWithSalt } = require('../lib/lib.js');

console.log(require('../lib/lib.js'));

const SALT = "salt";
// Test cases
test('hashWithSalt various input strings', t => {
    /**
     * taken from co/worklytics/psoxy/storage/impl/BulkDataSanitizerImplTest.java:305
     * see "https://github.com/Worklytics/psoxy/blob/d2e9c79abd52953006ffcd65b132e78625b55af3/java/core/src/test/java/co/worklytics/psoxy/storage/impl/BulkDataSanitizerImplTest.java#L305"
      */

    const examples = {
      "1" : "t~0zPKqEd-CtbCLB1ZSwX6Zo7uAWUvkpfHGzv9-cuYwZc",
      "2" : "t~-hN_i1M1DeMAicDVp6LhFgW9lH7r3_LbOpTlXYWpXVI",
      "3" : "t~4W7Sl-LI6iMzNNngivs5dLMiVw-7ob3Cyr3jn8NureY",
      "4" : "t~BOg00PLoiEEKyGzije3FJlKBzM6_Vjk87VJI9lTIA2o",
    }

    for (const [input, expected] of Object.entries(examples)) {
      const result = hashWithSalt(input, {salt : SALT});
      t.is(result, expected, `Hash of "${input}" with salt "salt" should be consistent`);
    }
});

test('hashWithSalt produces different hashes for different inputs', t => {
    const hash1 = hashWithSalt('1',{salt : SALT});
    const hash2 = hashWithSalt('2', {salt : SALT});
    t.not(hash1, hash2, 'Hashes for different input strings should be different');
});
