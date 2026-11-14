// TEMP: builds the v0.17.4 Starfire harness (v0173 harness + scratchpad/titans/sf_v0174_script.java.txt as its script) and hooks it in
// through sf_anim_hook.js, or "off" to unhook. Usage: node scratchpad/titans/sf_anim_hook_v0174.js <worktree root> [off]
// Then: node scratchpad/low_graphics_options.js run/options.txt, write run/sfanim.flag (scene filter, empty = all), ./gradlew runClient,
// OUT=docs/screenshots/v0174/starfire sh scratchpad/titans/films.sh
const fs = require('fs');
const cp = require('child_process');
const W = process.argv[2];
const off = process.argv[3] === 'off';
const D = W + '/scratchpad/titans/';
const base = fs.readFileSync(D + 'StarfireAnimHarness.v0173.java.txt', 'utf8').replace(/\r\n/g, '\n');
const i = base.indexOf('\tprivate static void script(Minecraft mc) {');
if (i < 0) throw new Error('script() not found');
const script = fs.readFileSync(D + 'sf_v0174_script.java.txt', 'utf8').replace(/\r\n/g, '\n');
const backup = D + 'StarfireAnimHarness.v0173.java.txt.bak';
if (!off) {
	fs.copyFileSync(D + 'StarfireAnimHarness.v0173.java.txt', backup);
	fs.writeFileSync(D + 'StarfireAnimHarness.v0173.java.txt', base.slice(0, i) + script);
}
try {
	cp.execFileSync('node', [D + 'sf_anim_hook.js', W].concat(off ? ['off'] : []), { stdio: 'inherit' });
} finally {
	if (fs.existsSync(backup)) {
		fs.copyFileSync(backup, D + 'StarfireAnimHarness.v0173.java.txt');
		fs.unlinkSync(backup);
	}
}
