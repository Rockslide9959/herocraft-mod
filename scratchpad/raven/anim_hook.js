// TEMP: hooks the v0.17.4 Raven animation / HUD film harness in, or out with "off".
// Usage: node scratchpad/raven/anim_hook.js <worktree root> [off]
// The harness = RavenRevampHarness.v0173 (world, camera, helpers) with RavenAnimHarness.script.v0174 as its script(), plus the
// orbit-camera mixin. Then: node scratchpad/low_graphics_options.js run/options.txt, write run/ravenanim.flag (text = scene
// filter, empty = all), ./gradlew runClient, OUT=docs/screenshots/v0174/raven PREFIX=ra sh scratchpad/raven/anim_films.sh
const fs = require('fs');
const W = process.argv[2];
const off = process.argv[3] === 'off';
const sp = W + '/scratchpad/raven/';
const dst = W + '/src/client/java/com/projecthero/mod/client/RavenAnimHarness.java';
const mdst = W + '/src/client/java/com/projecthero/mod/client/mixin/RavenAnimHarnessMixin.java';
const mj = W + '/src/client/resources/projecthero.client.mixins.json';
const cl = W + '/src/client/java/com/projecthero/mod/client/ProjectHeroModClient.java';
let t = fs.readFileSync(mj, 'utf8');
const nl = t.includes('\r\n') ? '\r\n' : '\n';
const anchor = '\t\t"StarfireBondGlowMixin",';
const mix = anchor + nl + '\t\t"RavenAnimHarnessMixin",';
let c = fs.readFileSync(cl, 'utf8');
const nl2 = c.includes('\r\n') ? '\r\n' : '\n';
const hook = '\t\tModKeyBindings.initialize();' + nl2 + '\t\tRavenAnimHarness.init(); // TEMP-HARNESS';
if (off) {
	t = t.replace(mix, anchor);
	c = c.replace(hook, '\t\tModKeyBindings.initialize();');
	for (const f of [dst, mdst]) if (fs.existsSync(f)) fs.unlinkSync(f);
} else {
	if (!t.includes(anchor)) throw new Error('mixin anchor not found');
	const base = fs.readFileSync(sp + 'RavenRevampHarness.v0173.java.txt', 'utf8').replace(/\r\n/g, '\n');
	const cut = base.indexOf('\tprivate static void script(Minecraft mc) {');
	if (cut < 0) throw new Error('script() not found');
	let src = base.slice(0, cut) + fs.readFileSync(sp + 'RavenAnimHarness.script.v0174.java.txt', 'utf8').replace(/\r\n/g, '\n');
	src = src.replace(/RavenRevampHarness/g, 'RavenAnimHarness').replace(/ravenrevamp\.flag/g, 'ravenanim.flag')
		.replace(/"rr_%s_%03d\.png"/g, '"ra_%s_%03d.png"').replace(/ravenrevamp-harness-watchdog/g, 'ravenanim-harness-watchdog')
		.replace(/"RR_"/g, '"RA_"');
	fs.writeFileSync(dst, src);
	const cam = fs.readFileSync(sp + 'RavenHarnessCamMixin.v0173.java.txt', 'utf8')
		.replace(/RavenHarnessMixin/g, 'RavenAnimHarnessMixin').replace(/RavenHarness/g, 'RavenAnimHarness').replace(/ravenHarnessCam/g, 'ravenAnimHarnessCam');
	fs.writeFileSync(mdst, cam);
	if (!t.includes('RavenAnimHarnessMixin')) t = t.replace(anchor, mix);
	if (!c.includes('RavenAnimHarness')) {
		if (!c.includes('\t\tModKeyBindings.initialize();')) throw new Error('hook anchor not found');
		c = c.replace('\t\tModKeyBindings.initialize();', hook);
	}
}
JSON.parse(t);
fs.writeFileSync(mj, t);
fs.writeFileSync(cl, c);
console.log(off ? 'harness off' : 'harness on');
