// TEMP: hooks the Spirit Bomb filming harness in, or out with "off". Usage: node spirit_bomb_harness_hook.js <worktree root> [off]
// Then: node scratchpad/low_graphics_options.js run/options.txt (raise renderDistance for the crater), touch run/spiritbomb.flag,
// ./gradlew runClient, sh scratchpad/saiyan/spirit_bomb_films.sh
const fs = require('fs');
const W = process.argv[2];
const off = process.argv[3] === 'off';
const sp = W + '/scratchpad/saiyan/';
const dst = W + '/src/client/java/com/projecthero/mod/client/SpiritBombHarness.java';
const mdst = W + '/src/client/java/com/projecthero/mod/client/mixin/SpiritBombHarnessCamMixin.java';
const mj = W + '/src/client/resources/projecthero.client.mixins.json';
const cl = W + '/src/client/java/com/projecthero/mod/client/ProjectHeroModClient.java';
let t = fs.readFileSync(mj, 'utf8');
const nl = t.includes('\r\n') ? '\r\n' : '\n';
const anchor = '\t\t"AlienXHallCameraMixin",';
const mix = anchor + nl + '\t\t"SpiritBombHarnessCamMixin",';
let c = fs.readFileSync(cl, 'utf8');
const nl2 = c.includes('\r\n') ? '\r\n' : '\n';
const hook = '\t\tModKeyBindings.initialize();' + nl2 + '\t\tSpiritBombHarness.init(); // TEMP-HARNESS';
if (off) {
	t = t.replace(mix, anchor);
	c = c.replace(hook, '\t\tModKeyBindings.initialize();');
	for (const f of [dst, mdst]) if (fs.existsSync(f)) fs.unlinkSync(f);
} else {
	fs.copyFileSync(sp + 'SpiritBombHarness.v0173.java.txt', dst);
	fs.copyFileSync(sp + 'SpiritBombHarnessCamMixin.v0173.java.txt', mdst);
	if (!t.includes('SpiritBombHarnessCamMixin')) t = t.replace(anchor, mix);
	if (!c.includes('SpiritBombHarness')) c = c.replace('\t\tModKeyBindings.initialize();', hook);
}
JSON.parse(t);
fs.writeFileSync(mj, t);
fs.writeFileSync(cl, c);
console.log(off ? 'harness off' : 'harness on');
