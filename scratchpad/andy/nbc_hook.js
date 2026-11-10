// TEMP: hook NbcHarness in (arg "on") or out (arg "off").
const fs = require('fs');
const on = process.argv[2] !== 'off';
let f = 'src/client/resources/projecthero.client.mixins.json';
let t = fs.readFileSync(f, 'utf8');
const nl = t.includes('\r\n') ? '\r\n' : '\n';
const mix = '\t\t"AndyHeldItemMixin",' + nl + '\t\t"NbcHarnessCameraMixin",';
if (on && !t.includes('NbcHarnessCameraMixin')) t = t.replace('\t\t"AndyHeldItemMixin",', mix);
if (!on) t = t.replace(mix, '\t\t"AndyHeldItemMixin",');
JSON.parse(t);
fs.writeFileSync(f, t);
f = 'src/client/java/com/projecthero/mod/client/ProjectHeroModClient.java';
t = fs.readFileSync(f, 'utf8');
const nl2 = t.includes('\r\n') ? '\r\n' : '\n';
const hook = '\t\tModKeyBindings.initialize();' + nl2 + '\t\tNbcHarness.init(); // TEMP-HARNESS';
if (on && !t.includes('NbcHarness')) t = t.replace('\t\tModKeyBindings.initialize();', hook);
if (!on) t = t.replace(hook, '\t\tModKeyBindings.initialize();');
fs.writeFileSync(f, t);
console.log(on ? 'hooked' : 'unhooked');
