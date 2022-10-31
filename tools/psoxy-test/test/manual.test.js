import aws from '../lib/aws.js';

(async function() {
  const objects = await aws.listObjects('psoxy-hris-y28nljpd-output', {
    role: 'arn:aws:iam::616480446222:role/InfraAdmin',
  });
  console.log(objects);
})();

