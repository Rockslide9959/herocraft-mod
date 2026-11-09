// v0.17.3: the hold-V draw / reveal / Tear Off / put-away blood is the client's (AndySwordFx, BloodFx): the server's big dust
// squares (huge right in front of a first-person camera) go.
const fs = require('fs');
const f = 'src/main/java/com/projecthero/mod/andy/AndyBoneSword.java';
let t = fs.readFileSync(f, 'utf8');
const nl = t.includes('\r\n') ? '\r\n' : '\n';
t = t.replace(/\r\n/g, '\n');
const cuts = [
  '\t\tlevel.sendParticles(Andy.BLOOD_BIG, at.x, at.y, at.z, 10, 0.12, 0.15, 0.12, 0.0);\n\t\tlevel.sendParticles(Andy.BLOOD_DARK, at.x, at.y, at.z, 6, 0.2, 0.2, 0.2, 0.0);\n',
  '\t\tlevel.sendParticles(Andy.BLOOD_BIG, at.x, at.y, at.z, 8, 0.2, 0.2, 0.2, 0.0);\n',
  '\t\tlevel.sendParticles(Andy.BLOOD_BIG, at.x, at.y, at.z, 8, 0.12, 0.15, 0.12, 0.0);\n',
  '\t\t\tlevel.sendParticles(Andy.BLOOD_BIG, at.x, at.y, at.z, 10, 0.15, 0.25, 0.15, 0.0);\n',
];
for (const c of cuts) {
  if (!t.includes(c)) throw new Error('missing ' + c);
  t = t.replace(c, '');
}
fs.writeFileSync(f, t.replace(/\n/g, nl));
console.log('ok');
