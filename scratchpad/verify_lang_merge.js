// node scratchpad/verify_lang_merge.js <agentCommit> : checks every key the agent changed/added landed, and keys it deleted are gone
const { execSync } = require('child_process');
const c = process.argv[2];
const P = 'src/main/resources/assets/projecthero/lang/en_us.json';
const theirs = JSON.parse(execSync(`git show ${c}:${P}`, { maxBuffer: 1e8 }).toString());
const base = JSON.parse(execSync(`git show 993e4cf:${P}`, { maxBuffer: 1e8 }).toString());
const ours = JSON.parse(require('fs').readFileSync(P, 'utf8'));
let miss = 0, diff = 0, stale = 0;
for (const k in theirs) { if (base[k] === theirs[k]) continue; if (!(k in ours)) { miss++; console.log('MISSING', k); } else if (ours[k] !== theirs[k]) { diff++; console.log('DIFF', k); } }
for (const k in base) if (!(k in theirs) && (k in ours)) { stale++; console.log('STALE', k); }
console.log(c, { miss, diff, stale });
