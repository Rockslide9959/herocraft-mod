// TEMP: hooks the Titans tester-round harness in, or out with "off". Usage: node scratchpad/titans/harness_hook.js <worktree root> [off]
// Then: node scratchpad/low_graphics_options.js run/options.txt, write run/titansfix.flag (text = scene filter, empty = all),
// ./gradlew runClient, sh scratchpad/titans/films.sh
const fs = require('fs');
const W = process.argv[2];
const off = process.argv[3] === 'off';
const dst = W + '/src/client/java/com/projecthero/mod/client/TitansFixHarness.java';
const mdst = W + '/src/client/java/com/projecthero/mod/client/mixin/TitansFixHarnessMixin.java';
const mj = W + '/src/client/resources/projecthero.client.mixins.json';
const cl = W + '/src/client/java/com/projecthero/mod/client/ProjectHeroModClient.java';
let t = fs.readFileSync(mj, 'utf8');
const nl = t.includes('\r\n') ? '\r\n' : '\n';
const anchor = '\t\t"StarfireBondGlowMixin",';
const mix = anchor + nl + '\t\t"TitansFixHarnessMixin",';
let c = fs.readFileSync(cl, 'utf8');
const nl2 = c.includes('\r\n') ? '\r\n' : '\n';
const hook = '\t\tModKeyBindings.initialize();' + nl2 + '\t\tTitansFixHarness.init(); // TEMP-HARNESS';
if (off) {
	t = t.replace(mix, anchor);
	c = c.replace(hook, '\t\tModKeyBindings.initialize();');
	for (const f of [dst, mdst]) if (fs.existsSync(f)) fs.unlinkSync(f);
} else {
	fs.copyFileSync(W + '/scratchpad/titans/TitansFixHarness.v0173.java.txt', dst);
	const cam = fs.readFileSync(W + '/scratchpad/raven/RavenHarnessCamMixin.v0173.java.txt', 'utf8')
		.replace(/RavenHarnessMixin/g, 'TitansFixHarnessMixin').replace(/RavenHarness/g, 'TitansFixHarness').replace(/ravenHarnessCam/g, 'titansFixHarnessCam');
	fs.writeFileSync(mdst, cam);
	if (!t.includes('TitansFixHarnessMixin')) t = t.replace(anchor, mix);
	if (!c.includes('TitansFixHarness')) {
		if (!c.includes('\t\tModKeyBindings.initialize();')) throw new Error('hook anchor not found');
		c = c.replace('\t\tModKeyBindings.initialize();', hook);
	}
}
JSON.parse(t);
fs.writeFileSync(mj, t);
fs.writeFileSync(cl, c);
console.log(off ? 'harness off' : 'harness on');
