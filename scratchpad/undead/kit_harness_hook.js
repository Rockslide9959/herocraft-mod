// TEMP: hook UndeadKitHarness in (arg "on") or out (arg "off"). Run from the repo root.
// "off" also moves the harness sources out of src into scratchpad/undead/ (kept as .java.txt).
const fs = require('fs');
const path = require('path');
const on = process.argv[2] !== 'off';
let f = 'src/client/resources/projecthero.client.mixins.json';
let t = fs.readFileSync(f, 'utf8');
const nl = t.includes('\r\n') ? '\r\n' : '\n';
const mix = '\t\t"UndeadHeldItemMixin",' + nl + '\t\t"UndeadKitHarnessCameraMixin",';
if (on && !t.includes('UndeadKitHarnessCameraMixin')) t = t.replace('\t\t"UndeadHeldItemMixin",', mix);
if (!on) t = t.replace(mix, '\t\t"UndeadHeldItemMixin",');
JSON.parse(t);
fs.writeFileSync(f, t);
f = 'src/client/java/com/projecthero/mod/client/ProjectHeroModClient.java';
t = fs.readFileSync(f, 'utf8');
const nl2 = t.includes('\r\n') ? '\r\n' : '\n';
const hook = '\t\tModKeyBindings.initialize();' + nl2 + '\t\tUndeadKitHarness.init(); // TEMP-HARNESS';
if (on && !t.includes('UndeadKitHarness')) t = t.replace('\t\tModKeyBindings.initialize();', hook);
if (!on) t = t.replace(hook, '\t\tModKeyBindings.initialize();');
fs.writeFileSync(f, t);
if (!on) {
	for (const g of ['src/client/java/com/projecthero/mod/client/UndeadKitHarness.java', 'src/client/java/com/projecthero/mod/client/mixin/UndeadKitHarnessCameraMixin.java']) {
		if (fs.existsSync(g)) {
			fs.copyFileSync(g, 'scratchpad/undead/' + path.basename(g, '.java') + '.v0173.java.txt');
			fs.unlinkSync(g);
		}
	}
}
console.log(on ? 'hooked' : 'unhooked');
