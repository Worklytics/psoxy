
const { hashWithSalt } = require("./lib/lib.js");

// Example usage
const input = process.argv[1];
const salt = process.argv[2];

console.log(hashWithSalt(input, {salt: salt}));
