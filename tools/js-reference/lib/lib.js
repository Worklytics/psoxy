const crypto = require('node:crypto');

/**
 * hashes a string with a salt and encodes it as a URL-safe base64 string, without padding, prefixed with 't~'
 * so that Worklytics recognizes it as a pseudonymized token
 *
 */
function hashWithSalt(inputString, options) {
    if (typeof inputString !== 'string') {
        throw new TypeError('inputString must be a string');
    }
    //validate options passed
    if (typeof options !== 'object') {
        throw new TypeError('options must be an object');
    }
    if (typeof options.salt !== 'string') {
        throw new TypeError('options.salt must be a string');
    }


    // Concatenate the salt and input string
    const saltedInput = inputString + options.salt;

    // Create a SHA-256 hash of the salted input string, base64-encoded
    const hash = crypto.createHash('sha256').update(saltedInput).digest('base64');

    // encode as URL-safe without padding
    // eg replace '+' with '-', '/' with '_', and remove trailing '=' characters
    return 't~' + hash.replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '');
}

module.exports = {
  hashWithSalt
}
