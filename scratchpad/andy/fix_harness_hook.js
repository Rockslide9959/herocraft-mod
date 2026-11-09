// TEMP: hook AndyFixHarness in (arg "on") or out (arg "off"). Run from the repo root.
const fs = require('fs');
const on = process.argv[2] !== 'off';
let f = 'src/client/resources/projecthero.client.mixins.json';
let t = fs.readFileSync(f, 'utf8');
const mix = '\t\t"AndyHeldItemMixin",' + (t.includes('\r\n') ? '\r\n' : '\n') + '\t\t"AndyFixHarnessCameraMixin",';
if (on && !t.includes('AndyFixHarnessCameraMixin')) t = t.replace('\t\t"AndyHeldItemMixin",', mix);
if (!on) t = t.replace(mix, '\t\t"AndyHeldItemMixin",');
JSON.parse(t);
fs.writeFileSync(f, t);
f = 'src/client/java/com/projecthero/mod/client/ProjectHeroModClient.java';
t = fs.readFileSync(f, 'utf8');
const nl = t.includes('\r\n') ? '\r\n' : '\n';
const hook = '\t\tModKeyBindings.initialize();' + nl + '\t\tAndyFixHarness.init(); // TEMP-HARNESS';
if (on && !t.includes('AndyFixHarness')) t = t.replace('\t\tModKeyBindings.initialize();', hook);
if (!on) t = t.replace(hook, '\t\tModKeyBindings.initialize();');
fs.writeFileSync(f, t);
if (!on) {
  for (const g of ['src/client/java/com/projecthero/mod/client/AndyFixHarness.java', 'src/client/java/com/projecthero/mod/client/mixin/AndyFixHarnessCameraMixin.java']) {
    if (fs.existsSync(g)) { fs.copyFileSync(g, 'scratchpad/andy/' + require('path').basename(g, '.java') + '.v0173.java.txt'); fs.unlinkSync(g); }
  }
}
console.log(on ? 'hooked' : 'unhooked');
