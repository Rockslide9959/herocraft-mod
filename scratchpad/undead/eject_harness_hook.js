// TEMP: hook UndeadEjectHarness in (arg "on") or out (arg "off"). Run from the repo root. "off" also moves the harness
// sources to scratchpad/undead/*.v0173.java.txt.
const fs = require('fs');
const path = require('path');
const on = process.argv[2] !== 'off';
let f = 'src/client/resources/projecthero.client.mixins.json';
let t = fs.readFileSync(f, 'utf8');
const nlm = t.includes('\r\n') ? '\r\n' : '\n';
const anchor = '\t\t"UndeadCameraMixin",';
const mix = anchor + nlm + '\t\t"UndeadEjectHarnessCameraMixin",';
if (on && !t.includes('UndeadEjectHarnessCameraMixin')) t = t.replace(anchor, mix);
if (!on) t = t.replace(mix, anchor);
JSON.parse(t);
fs.writeFileSync(f, t);
f = 'src/client/java/com/projecthero/mod/client/ProjectHeroModClient.java';
t = fs.readFileSync(f, 'utf8');
const nl = t.includes('\r\n') ? '\r\n' : '\n';
const hook = '\t\tModKeyBindings.initialize();' + nl + '\t\tUndeadEjectHarness.init(); // TEMP-HARNESS';
if (on && !t.includes('UndeadEjectHarness')) t = t.replace('\t\tModKeyBindings.initialize();', hook);
if (!on) t = t.replace(hook, '\t\tModKeyBindings.initialize();');
fs.writeFileSync(f, t);
if (!on) {
	for (const g of ['src/client/java/com/projecthero/mod/client/UndeadEjectHarness.java', 'src/client/java/com/projecthero/mod/client/mixin/UndeadEjectHarnessCameraMixin.java']) {
		if (fs.existsSync(g)) {
			fs.copyFileSync(g, 'scratchpad/undead/' + path.basename(g, '.java') + '.v0173.java.txt');
			fs.unlinkSync(g);
		}
	}
}
console.log(on ? 'hooked' : 'unhooked');
