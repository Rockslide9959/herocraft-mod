// TEMP (v0.17.4): hook TimeStopMobilityHarness in (arg "on") or out (arg "off"). Run from the repo root.
// "off" moves the harness + camera mixin into scratchpad/stand/*.v0174.java.txt.
const fs = require('fs');
const path = require('path');
const on = process.argv[2] !== 'off';
let f = 'src/client/resources/projecthero.client.mixins.json';
let t = fs.readFileSync(f, 'utf8');
const nl0 = t.includes('\r\n') ? '\r\n' : '\n';
const mix = '\t\t"StandArmPoseInvoker",' + nl0 + '\t\t"TimeStopMobilityHarnessCameraMixin",';
if (on && !t.includes('TimeStopMobilityHarnessCameraMixin')) t = t.replace('\t\t"StandArmPoseInvoker",', mix);
if (!on) t = t.replace(mix, '\t\t"StandArmPoseInvoker",');
JSON.parse(t);
fs.writeFileSync(f, t);
f = 'src/client/java/com/projecthero/mod/client/ProjectHeroModClient.java';
t = fs.readFileSync(f, 'utf8');
const nl = t.includes('\r\n') ? '\r\n' : '\n';
const hook = '\t\tModKeyBindings.initialize();' + nl + '\t\tTimeStopMobilityHarness.init(); // TEMP-HARNESS';
if (on && !t.includes('TimeStopMobilityHarness')) t = t.replace('\t\tModKeyBindings.initialize();', hook);
if (!on) t = t.replace(hook, '\t\tModKeyBindings.initialize();');
fs.writeFileSync(f, t);
if (!on) {
  for (const g of ['src/client/java/com/projecthero/mod/client/TimeStopMobilityHarness.java', 'src/client/java/com/projecthero/mod/client/mixin/TimeStopMobilityHarnessCameraMixin.java']) {
    if (fs.existsSync(g)) { fs.copyFileSync(g, path.join('scratchpad/stand', path.basename(g, '.java') + '.v0174.java.txt')); fs.unlinkSync(g); }
  }
}
console.log(on ? 'hooked' : 'unhooked');
