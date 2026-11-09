// Normalises the named files to CRLF (the repo's Java files are CRLF).
const fs = require('fs');
for (const f of process.argv.slice(2)) {
  const t = fs.readFileSync(f, 'utf8').replace(/\r\n/g, '\n').replace(/\n/g, '\r\n');
  fs.writeFileSync(f, t);
  console.log('crlf', f);
}
