// TEMP: hooks the Raven revamp film harness in, or out with "off". Usage: node scratchpad/raven/revamp_hook.js <worktree root> [off]
// In: copies the harness + its orbit-camera mixin into src/client, registers both, and merges scratchpad/raven/lang_raven_revamp.json
// into a copy of en_us.json (the original is kept in run/en_us.backup.json and put back by "off").
// Then: node scratchpad/low_graphics_options.js run/options.txt, write run/ravenrevamp.flag (text = scene filter, empty = all),
// ./gradlew runClient, sh scratchpad/raven/revamp_films.sh
const fs = require('fs');
const W = process.argv[2];
const off = process.argv[3] === 'off';
const dst = W + '/src/client/java/com/projecthero/mod/client/RavenRevampHarness.java';
const mdst = W + '/src/client/java/com/projecthero/mod/client/mixin/RavenRevampHarnessMixin.java';
const mj = W + '/src/client/resources/projecthero.client.mixins.json';
const cl = W + '/src/client/java/com/projecthero/mod/client/ProjectHeroModClient.java';
const lang = W + '/src/main/resources/assets/projecthero/lang/en_us.json';
const backup = W + '/run/en_us.backup.json';
let t = fs.readFileSync(mj, 'utf8');
const nl = t.includes('\r\n') ? '\r\n' : '\n';
const anchor = '\t\t"StarfireBondGlowMixin",';
const mix = anchor + nl + '\t\t"RavenRevampHarnessMixin",';
let c = fs.readFileSync(cl, 'utf8');
const nl2 = c.includes('\r\n') ? '\r\n' : '\n';
const hook = '\t\tModKeyBindings.initialize();' + nl2 + '\t\tRavenRevampHarness.init(); // TEMP-HARNESS';
if (off) {
	t = t.replace(mix, anchor);
	c = c.replace(hook, '\t\tModKeyBindings.initialize();');
	for (const f of [dst, mdst]) if (fs.existsSync(f)) fs.unlinkSync(f);
	if (fs.existsSync(backup)) {
		fs.copyFileSync(backup, lang);
		fs.unlinkSync(backup);
	}
} else {
	fs.copyFileSync(W + '/scratchpad/raven/RavenRevampHarness.v0173.java.txt', dst);
	const cam = fs.readFileSync(W + '/scratchpad/raven/RavenHarnessCamMixin.v0173.java.txt', 'utf8')
		.replace(/RavenHarnessMixin/g, 'RavenRevampHarnessMixin').replace(/RavenHarness/g, 'RavenRevampHarness').replace(/ravenHarnessCam/g, 'ravenRevampHarnessCam');
	fs.writeFileSync(mdst, cam);
	if (!t.includes('RavenRevampHarnessMixin')) t = t.replace(anchor, mix);
	if (!c.includes('RavenRevampHarness')) {
		if (!c.includes('\t\tModKeyBindings.initialize();')) throw new Error('hook anchor not found');
		c = c.replace('\t\tModKeyBindings.initialize();', hook);
	}
	if (!fs.existsSync(backup)) {
		fs.mkdirSync(W + '/run', { recursive: true });
		fs.copyFileSync(lang, backup);
	}
	const base = JSON.parse(fs.readFileSync(backup, 'utf8'));
	const add = JSON.parse(fs.readFileSync(W + '/scratchpad/raven/lang_raven_revamp.json', 'utf8'));
	Object.assign(base, add);
	fs.writeFileSync(lang, JSON.stringify(base, null, 2));
}
JSON.parse(t);
fs.writeFileSync(mj, t);
fs.writeFileSync(cl, c);
console.log(off ? 'harness off' : 'harness on');
