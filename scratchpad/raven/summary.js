// Prints the failures and the pass count from build/reports/gametest.xml.
const s = require('fs').readFileSync('build/reports/gametest.xml', 'utf8');
const cases = [...s.matchAll(/<testcase[^>]*name="([^"]+)"[^>]*?(\/>|>([\s\S]*?)<\/testcase>)/g)];
let pass = 0;
for (const c of cases) {
  if (c[3] && /failure|error/.test(c[3])) {
    const m = c[3].match(/message="([^"]*)"/);
    console.log('FAIL', c[1], m ? m[1].slice(0, 300) : '');
  } else pass++;
}
console.log('pass', pass, 'of', cases.length);
