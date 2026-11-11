// TEMP: hook UndeadRegenV2Harness in (arg "on") or out (arg "off"). Run from the repo root.
const fs = require('fs');
const path = require('path');
const on = process.argv[2] !== 'off';
let f = 'src/client/resources/projecthero.client.mixins.json';
let t = fs.readFileSync(f, 'utf8');
const nl0 = t.includes('\r\n') ? '\r\n' : '\n';
const mix = '\t\t"UndeadHeldItemMixin",' + nl0 + '\t\t"UndeadRegenV2HarnessCameraMixin",';
if (on && !t.includes('UndeadRegenV2HarnessCameraMixin')) t = t.replace('\t\t"UndeadHeldItemMixin",', mix);
if (!on) t = t.replace(mix, '\t\t"UndeadHeldItemMixin",');
JSON.parse(t);
fs.writeFileSync(f, t);
f = 'src/client/java/com/projecthero/mod/client/ProjectHeroModClient.java';
t = fs.readFileSync(f, 'utf8');
const nl = t.includes('\r\n') ? '\r\n' : '\n';
const hook = '\t\tModKeyBindings.initialize();' + nl + '\t\tUndeadRegenV2Harness.init(); // TEMP-HARNESS';
if (on && !t.includes('UndeadRegenV2Harness')) t = t.replace('\t\tModKeyBindings.initialize();', hook);
if (!on) t = t.replace(hook, '\t\tModKeyBindings.initialize();');
fs.writeFileSync(f, t);
if (!on) {
  for (const g of ['src/client/java/com/projecthero/mod/client/UndeadRegenV2Harness.java', 'src/client/java/com/projecthero/mod/client/mixin/UndeadRegenV2HarnessCameraMixin.java']) {
    if (fs.existsSync(g)) { fs.copyFileSync(g, 'scratchpad/undead/' + path.basename(g, '.java') + '.v0173.java.txt'); fs.unlinkSync(g); }
  }
}
console.log(on ? 'hooked' : 'unhooked');
