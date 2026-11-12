// TEMP: hooks VisionHarness in, or out with "off". Usage: node scratchpad/vision/harness_hook.js <worktree root> [off]
// Then: node scratchpad/low_graphics_options.js run/options.txt, touch run/visionshots.flag, ./gradlew runClient,
// sh scratchpad/vision/films.sh
const fs = require('fs');
const W = process.argv[2];
const off = process.argv[3] === 'off';
const sp = W + '/scratchpad/vision/';
const cdir = W + '/src/client/java/com/projecthero/mod/client/';
const mj = W + '/src/client/resources/projecthero.client.mixins.json';
const cl = cdir + 'ProjectHeroModClient.java';
const mixins = ['VisionHarnessCamMixin', 'VisionHarnessSkinMixin'];
let t = fs.readFileSync(mj, 'utf8');
const nl = t.includes('\r\n') ? '\r\n' : '\n';
const anchor = '\t\t"AlienXHallCameraMixin",';
const added = anchor + mixins.map((m) => nl + '\t\t"' + m + '",').join('');
let c = fs.readFileSync(cl, 'utf8');
const nl2 = c.includes('\r\n') ? '\r\n' : '\n';
const init = '\t\tModKeyBindings.initialize();';
const hook = init + nl2 + '\t\tVisionHarness.init(); // TEMP-HARNESS';
if (off) {
	t = t.replace(added, anchor);
	c = c.replace(hook, init);
	for (const f of [cdir + 'VisionHarness.java', ...mixins.map((m) => cdir + 'mixin/' + m + '.java')]) if (fs.existsSync(f)) fs.unlinkSync(f);
} else {
	fs.copyFileSync(sp + 'VisionHarness.v0173.java.txt', cdir + 'VisionHarness.java');
	const parts = fs.readFileSync(sp + 'VisionHarnessMixins.v0173.java.txt', 'utf8').split(/^=== (\S+)\r?\n/m);
	for (let i = 1; i < parts.length; i += 2) fs.writeFileSync(cdir + 'mixin/' + parts[i], parts[i + 1]);
	if (!t.includes('VisionHarnessCamMixin')) { if (!t.includes(anchor)) throw new Error('mixin anchor'); t = t.replace(anchor, added); }
	if (!c.includes('VisionHarness')) { if (!c.includes(init)) throw new Error('hook anchor'); c = c.replace(init, hook); }
}
fs.writeFileSync(mj, t);
fs.writeFileSync(cl, c);
console.log(off ? 'harness out' : 'harness in');
