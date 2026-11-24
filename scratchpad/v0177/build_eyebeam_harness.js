// v0.17.7: builds the EyeBeamHarness (v0.17.5 copy + an "fp3" first-person wobble film) into src for one run.
//   node scratchpad/v0177/build_eyebeam_harness.js install | remove
const fs = require('fs');
const src = fs.readFileSync('scratchpad/EyeBeamHarness.v0175.java.txt', 'utf8').replace(/\r\n/g, '\n');
const javaPath = 'src/client/java/com/projecthero/mod/client/EyeBeamHarness.java';
const mixPath = 'src/client/java/com/projecthero/mod/client/mixin/EyeBeamHarnessCameraMixin.java';
const modJson = 'src/main/resources/fabric.mod.json';
const mixJson = 'src/client/resources/projecthero.client.mixins.json';
const ENTRY = '"com.projecthero.mod.client.EyeBeamHarness"';
const MIX = '"EyeBeamHarnessCameraMixin"';
function editJson(file, fn) { let t = fs.readFileSync(file, 'utf8'); const nl = t.includes('\r\n') ? '\r\n' : '\n'; t = fn(t.replace(/\r\n/g, '\n')).replace(/\n/g, nl); fs.writeFileSync(file, t); }
if (process.argv[2] === 'remove') {
	for (const f of [javaPath, mixPath]) if (fs.existsSync(f)) fs.unlinkSync(f);
	editJson(modJson, t => t.replace(',\n\t\t\t' + ENTRY, ''));
	editJson(mixJson, t => t.replace('"client": [' + MIX + ']', '"client": []'));
	console.log('removed');
	process.exit(0);
}
const parts = src.split(/\/\/ ===== .* =====\n/);
let java = parts[1], mixin = parts[2];
const fp3 = `		if (mode.startsWith("fp3")) {
			// v0.17.7 first-person wobble check: Laser Vision held while strafing with view bobbing on, a look sweep, then Speed II
			// (the FOV widens) -- the beams should stay put on screen
			at(t, () -> {
				select("laser");
				look(-15f, 6f);
				fp();
				mc.options.fov().set(70);
				mc.options.bobView().set(true);
			});
			film(t + 4, t + 128, "film_laser_fp_walk");
			at(t + 8, () -> on(true));
			at(t + 14, () -> KeyMapping.set(mc.options.keyRight.getDefaultKey(), true));
			at(t + 44, () -> {
				KeyMapping.set(mc.options.keyRight.getDefaultKey(), false);
				KeyMapping.set(mc.options.keyLeft.getDefaultKey(), true);
			});
			at(t + 74, () -> KeyMapping.set(mc.options.keyLeft.getDefaultKey(), false));
			for (int i = 0; i <= 30; i++) {
				final float y = -15f + 30f * i / 30f;
				at(t + 76 + i, () -> look(y, 6f));
			}
			at(t + 90, () -> server(mc, p -> p.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.MOVEMENT_SPEED, 200, 2))));
			at(t + 92, () -> KeyMapping.set(mc.options.keyRight.getDefaultKey(), true));
			at(t + 112, () -> KeyMapping.set(mc.options.keyRight.getDefaultKey(), false));
			at(t + 120, () -> on(false));
			final int e3 = t + 132;
			at(e3, () -> {
				System.out.println("[HARNESS] done");
				Runtime.getRuntime().halt(0);
			});
			return;
		}
`;
const anchor = '\t\tif (mode.startsWith("gaze")) {';
if (!java.includes(anchor)) throw 'anchor';
java = java.replace(anchor, fp3 + anchor);
fs.writeFileSync(javaPath, java.replace(/\n/g, '\r\n'));
fs.writeFileSync(mixPath, mixin.replace(/\n/g, '\r\n'));
editJson(modJson, t => t.includes(ENTRY) ? t : t.replace('"com.projecthero.mod.client.ProjectHeroModClient"', '"com.projecthero.mod.client.ProjectHeroModClient",\n\t\t\t' + ENTRY));
editJson(mixJson, t => t.includes(MIX) ? t : t.replace('"client": []', '"client": [' + MIX + ']'));
console.log('installed');
