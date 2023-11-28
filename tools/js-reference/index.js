const crypto = require('crypto');

function hashWithSalt(inputString, salt) {
    // Concatenate the salt and input string
    const combinedString = inputString + salt;

    // Create a SHA-256 hash of the combined string
    const hash = crypto.createHash('sha256').update(combinedString).digest('base64');

    // URL-safe base64 encoding
    return hash.replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '');
}

// Example usage
const salt = "your_salt_here";
const input = "your_input_string_here";
console.log(hashWithSalt(input, salt));
