// TEMP: hooks the Saiyan move-kit filming harness in, or out with "off". Usage: node kit_harness_hook.js <worktree root> [off]
// Then: node scratchpad/low_graphics_options.js run/options.txt, write run/saiyankit.flag (text = scene filter, empty = all),
// ./gradlew runClient, sh scratchpad/saiyan/kit_films.sh
const fs = require('fs');
const W = process.argv[2];
const off = process.argv[3] === 'off';
const sp = W + '/scratchpad/saiyan/';
const dst = W + '/src/client/java/com/projecthero/mod/client/SaiyanKitHarness.java';
const mdst = W + '/src/client/java/com/projecthero/mod/client/mixin/SaiyanKitHarnessMixin.java';
const mj = W + '/src/client/resources/projecthero.client.mixins.json';
const cl = W + '/src/client/java/com/projecthero/mod/client/ProjectHeroModClient.java';
let t = fs.readFileSync(mj, 'utf8');
const nl = t.includes('\r\n') ? '\r\n' : '\n';
const anchor = '\t\t"AlienXHallCameraMixin",';
const mix = anchor + nl + '\t\t"SaiyanKitHarnessMixin",';
let c = fs.readFileSync(cl, 'utf8');
const nl2 = c.includes('\r\n') ? '\r\n' : '\n';
const hook = '\t\tModKeyBindings.initialize();' + nl2 + '\t\tSaiyanKitHarness.init(); // TEMP-HARNESS';
if (off) {
	t = t.replace(mix, anchor);
	c = c.replace(hook, '\t\tModKeyBindings.initialize();');
	for (const f of [dst, mdst]) if (fs.existsSync(f)) fs.unlinkSync(f);
} else {
	fs.copyFileSync(sp + 'SaiyanKitHarness.v0173.java.txt', dst);
	fs.copyFileSync(sp + 'SaiyanKitHarnessCamMixin.v0173.java.txt', mdst);
	if (!t.includes('SaiyanKitHarnessMixin')) t = t.replace(anchor, mix);
	if (!c.includes('SaiyanKitHarness')) {
		if (!c.includes('\t\tModKeyBindings.initialize();')) throw new Error('hook anchor not found');
		c = c.replace('\t\tModKeyBindings.initialize();', hook);
	}
}
JSON.parse(t);
fs.writeFileSync(mj, t);
fs.writeFileSync(cl, c);
console.log(off ? 'harness off' : 'harness on');
