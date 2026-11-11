// TEMP: hooks the aura v3 filming harness in, or out with "off". Usage: node aura_v3_harness_hook.js <worktree root> [off]
// Also drops in the old v1/v2 renderers (SaiyanAuraRendererV1/V2.compare.java.txt) and a variant switch in SaiyanLayer for compare.png.
// Then: low_graphics_options.js run/options.txt, write run/saiyanfx.flag, ./gradlew runClient, sh scratchpad/saiyan/aura_v3_films.sh
const fs = require('fs');
const W = process.argv[2];
const off = process.argv[3] === 'off';
const sp = W + '/scratchpad/saiyan/';
const dst = W + '/src/client/java/com/projecthero/mod/client/SaiyanFxHarness.java';
const mdst = W + '/src/client/java/com/projecthero/mod/client/mixin/SaiyanFxHarnessMixin.java';
const aura = W + '/src/client/java/com/projecthero/mod/client/saiyan/aura/';
const v1 = aura + 'SaiyanAuraRendererV1.java', v2 = aura + 'SaiyanAuraRendererV2.java';
const layer = W + '/src/client/java/com/projecthero/mod/client/saiyan/SaiyanLayer.java';
const mj = W + '/src/client/resources/projecthero.client.mixins.json';
const cl = W + '/src/client/java/com/projecthero/mod/client/ProjectHeroModClient.java';
let t = fs.readFileSync(mj, 'utf8');
const nl = t.includes('\r\n') ? '\r\n' : '\n';
const anchor = '\t\t"AlienXHallCameraMixin",';
const mix = anchor + nl + '\t\t"SaiyanFxHarnessMixin",';
let c = fs.readFileSync(cl, 'utf8');
const nl2 = c.includes('\r\n') ? '\r\n' : '\n';
const hook = '\t\tModKeyBindings.initialize();' + nl2 + '\t\tSaiyanFxHarness.init(); // TEMP-HARNESS';
let l = fs.readFileSync(layer, 'utf8');
const crlf = l.includes('\r\n');
const call = '\t\tSaiyanAuraRenderer.render(pose, buffers, player, partialTick, ageInTicks);';
let sw = '\t\tswitch (com.projecthero.mod.client.SaiyanFxHarness.variant) { // TEMP-HARNESS\n'
	+ '\t\t\tcase 1 -> com.projecthero.mod.client.saiyan.aura.SaiyanAuraRendererV1.render(pose, buffers, player, partialTick, ageInTicks);\n'
	+ '\t\t\tcase 2 -> com.projecthero.mod.client.saiyan.aura.SaiyanAuraRendererV2.render(pose, buffers, player, partialTick, ageInTicks);\n'
	+ '\t\t\tdefault -> SaiyanAuraRenderer.render(pose, buffers, player, partialTick, ageInTicks);\n\t\t}';
if (crlf) sw = sw.replace(/\n/g, '\r\n');
if (off) {
	t = t.replace(mix, anchor);
	c = c.replace(hook, '\t\tModKeyBindings.initialize();');
	l = l.replace(sw, call);
	for (const f of [dst, mdst, v1, v2]) if (fs.existsSync(f)) fs.unlinkSync(f);
} else {
	fs.copyFileSync(sp + 'SaiyanAuraV3Harness.v0173.java.txt', dst);
	fs.copyFileSync(sp + 'SaiyanAuraHarnessCamMixin.v0173.java.txt', mdst);
	fs.copyFileSync(sp + 'SaiyanAuraRendererV1.compare.java.txt', v1);
	fs.copyFileSync(sp + 'SaiyanAuraRendererV2.compare.java.txt', v2);
	if (!t.includes('SaiyanFxHarnessMixin')) t = t.replace(anchor, mix);
	if (!c.includes('SaiyanFxHarness')) c = c.replace('\t\tModKeyBindings.initialize();', hook);
	if (!l.includes('TEMP-HARNESS')) {
		if (!l.includes(call)) throw new Error('SaiyanLayer call not found');
		l = l.replace(call, sw);
	}
}
JSON.parse(t);
fs.writeFileSync(mj, t);
fs.writeFileSync(cl, c);
fs.writeFileSync(layer, l);
console.log(off ? 'harness off' : 'harness on');
