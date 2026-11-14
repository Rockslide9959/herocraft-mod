// TEMP: hooks the v0.17.4 swim / green hearts harness in, or out with "off".
// Usage: node scratchpad/beastboy/swim_hearts_hook.js <worktree root> [off]
// Then: node scratchpad/low_graphics_options.js run/options.txt, write run/swimhearts.flag (text = scene filter, empty = all),
// ./gradlew runClient (it quits itself when done), sh scratchpad/beastboy/swim_hearts_films.sh <out subdir>
const fs = require('fs');
const W = process.argv[2];
const off = process.argv[3] === 'off';
const sp = W + '/scratchpad/beastboy/';
const dst = W + '/src/client/java/com/projecthero/mod/client/SwimHeartsHarness.java';
const mdst = W + '/src/client/java/com/projecthero/mod/client/mixin/SwimHeartsHarnessMixin.java';
const mj = W + '/src/client/resources/projecthero.client.mixins.json';
const cl = W + '/src/client/java/com/projecthero/mod/client/ProjectHeroModClient.java';
let t = fs.readFileSync(mj, 'utf8');
const anchor = '\t\t"AlienXHallCameraMixin",';
const nl = t.includes('\r\n') ? '\r\n' : '\n';
const mix = anchor + nl + '\t\t"SwimHeartsHarnessMixin",';
let c = fs.readFileSync(cl, 'utf8');
const nl2 = c.includes('\r\n') ? '\r\n' : '\n';
const hook = '\t\tModKeyBindings.initialize();' + nl2 + '\t\tSwimHeartsHarness.init(); // TEMP-HARNESS';
if (off) {
	t = t.replace(mix, anchor);
	c = c.replace(hook, '\t\tModKeyBindings.initialize();');
	for (const f of [dst, mdst]) if (fs.existsSync(f)) fs.unlinkSync(f);
} else {
	fs.copyFileSync(sp + 'SwimHeartsHarness.v0174.java.txt', dst);
	fs.copyFileSync(sp + 'SwimHeartsHarnessCamMixin.v0174.java.txt', mdst);
	if (!t.includes('SwimHeartsHarnessMixin')) t = t.replace(anchor, mix);
	if (!c.includes('SwimHeartsHarness')) {
		if (!c.includes('\t\tModKeyBindings.initialize();')) throw new Error('hook anchor not found');
		c = c.replace('\t\tModKeyBindings.initialize();', hook);
	}
}
JSON.parse(t);
fs.writeFileSync(mj, t);
fs.writeFileSync(cl, c);
console.log(off ? 'harness off' : 'harness on');
