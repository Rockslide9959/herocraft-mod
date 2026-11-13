// TEMP: hooks a filming harness in (or out with "off"). CRLF-safe, idempotent.
// Usage: node scratchpad/doctorfate/fx_harness_hook.js <worktree root> <harness .java.txt> <camera mixin .java.txt> [off]
// The class names are read from the files. Then: node scratchpad/low_graphics_options.js run/options.txt, write the flag file,
// ./gradlew runClient. Always hook out before committing.
const fs = require('fs');
const path = require('path');
const [W, harness, mixin, mode] = process.argv.slice(2);
const off = mode === 'off';
const cls = f => /public (?:final |abstract )*class (\w+)/.exec(fs.readFileSync(f, 'utf8'))[1];
const hName = cls(harness);
const mName = cls(mixin);
const dst = W + '/src/client/java/com/projecthero/mod/client/' + hName + '.java';
const mdst = W + '/src/client/java/com/projecthero/mod/client/mixin/' + mName + '.java';
const mj = W + '/src/client/resources/projecthero.client.mixins.json';
const cl = W + '/src/client/java/com/projecthero/mod/client/ProjectHeroModClient.java';
let t = fs.readFileSync(mj, 'utf8');
const nl = t.includes('\r\n') ? '\r\n' : '\n';
const anchor = '\t\t"AlienXHallCameraMixin",';
const mix = anchor + nl + '\t\t"' + mName + '",';
let c = fs.readFileSync(cl, 'utf8');
const nl2 = c.includes('\r\n') ? '\r\n' : '\n';
const base = '\t\tModKeyBindings.initialize();';
const hook = base + nl2 + '\t\t' + hName + '.init(); // TEMP-HARNESS';
if (off) {
	t = t.replace(mix, anchor);
	c = c.replace(hook, base);
	for (const f of [dst, mdst]) if (fs.existsSync(f)) fs.unlinkSync(f);
} else {
	fs.copyFileSync(harness, dst);
	fs.copyFileSync(mixin, mdst);
	if (!t.includes('"' + mName + '"')) t = t.replace(anchor, mix);
	if (!c.includes(hName + '.init()')) {
		if (!c.includes(base)) throw new Error('hook anchor not found');
		c = c.replace(base, hook);
	}
}
fs.writeFileSync(mj, t);
fs.writeFileSync(cl, c);
console.log(off ? 'harness out: ' + hName : 'harness in: ' + hName);
