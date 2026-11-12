// TEMP: hooks the Spin filming harness in, or out with "off". Usage: node scratchpad/spin/harness_hook.js <worktree root> [off|curve]
// ("curve" = the v0.17.3 round 2/3 curve / held-ball / VFX-trail scenes; films.sh raw names their MP4s after the scene)
// Then: node scratchpad/low_graphics_options.js run/options.txt, write run/spin.flag (text = scene filter, empty = all),
// ./gradlew runClient, sh scratchpad/spin/films.sh
const fs = require('fs');
const W = process.argv[2];
const off = process.argv[3] === 'off';
const sp = W + '/scratchpad/spin/';
const dst = W + '/src/client/java/com/projecthero/mod/client/SpinHarness.java';
const mdst = W + '/src/client/java/com/projecthero/mod/client/mixin/SpinHarnessMixin.java';
const mj = W + '/src/client/resources/projecthero.client.mixins.json';
const cl = W + '/src/client/java/com/projecthero/mod/client/ProjectHeroModClient.java';
let t = fs.readFileSync(mj, 'utf8');
const nl = t.includes('\r\n') ? '\r\n' : '\n';
const anchor = '\t\t"AlienXHallCameraMixin",';
const mix = anchor + nl + '\t\t"SpinHarnessMixin",';
let c = fs.readFileSync(cl, 'utf8');
const nl2 = c.includes('\r\n') ? '\r\n' : '\n';
const hook = '\t\tModKeyBindings.initialize();' + nl2 + '\t\tSpinHarness.init(); // TEMP-HARNESS';
if (off) {
	t = t.replace(mix, anchor);
	c = c.replace(hook, '\t\tModKeyBindings.initialize();');
	for (const f of [dst, mdst]) if (fs.existsSync(f)) fs.unlinkSync(f);
} else {
	if (process.argv[3] === 'curve') {
		// v0.17.3 round 2/3: the same harness with the curve / hold / VFX scenes (SpinHarness.curve_script.java.txt) as its script()
		const base = fs.readFileSync(sp + 'SpinHarness.v0173.java.txt', 'utf8');
		const cut = base.indexOf('\tprivate static void script(Minecraft mc) {');
		if (cut < 0) throw new Error('script() not found');
		fs.writeFileSync(dst, base.slice(0, cut) + fs.readFileSync(sp + 'SpinHarness.curve_script.java.txt', 'utf8'));
	} else {
		fs.copyFileSync(sp + 'SpinHarness.v0173.java.txt', dst);
	}
	fs.copyFileSync(sp + 'SpinHarnessCamMixin.v0173.java.txt', mdst);
	if (!t.includes('SpinHarnessMixin')) t = t.replace(anchor, mix);
	if (!c.includes('SpinHarness')) {
		if (!c.includes('\t\tModKeyBindings.initialize();')) throw new Error('hook anchor not found');
		c = c.replace('\t\tModKeyBindings.initialize();', hook);
	}
}
JSON.parse(t);
fs.writeFileSync(mj, t);
fs.writeFileSync(cl, c);
console.log(off ? 'harness off' : 'harness on');
