// node scratchpad/vision/wire_shared.js -- adds Vision's lines to the shared files, each right after a Doctor Fate anchor.
// Idempotent (skips a file that already has the inserted text). CRLF-safe. The guidebook and the chapter-count tests are edited by
// hand (tight, merge-friendly edits).
'use strict';
const fs = require('fs');
const M = 'src/main/java/com/projecthero/mod/';
const C = 'src/client/java/com/projecthero/mod/client/';
let fails = 0;

function edit(file, anchor, insert, opts = {}) {
	const raw = fs.readFileSync(file, 'utf8');
	const crlf = raw.includes('\r\n');
	let s = raw.replace(/\r\n/g, '\n');
	const marker = opts.marker || insert.trim().split('\n')[0].trim();
	if (s.includes(marker)) {
		console.log('skip (already there)', file, marker.slice(0, 60));
		return;
	}
	const at = s.indexOf(anchor);
	if (at < 0 || s.indexOf(anchor, at + 1) >= 0) {
		console.log('ANCHOR', at < 0 ? 'MISSING' : 'NOT UNIQUE', file, anchor.slice(0, 80));
		fails++;
		return;
	}
	let end = s.indexOf('\n', at + anchor.length) + 1; // after the anchor's line
	for (let i = 0; i < (opts.skipLines || 0); i++) end = s.indexOf('\n', end) + 1;
	s = s.slice(0, end) + insert + s.slice(end);
	fs.writeFileSync(file, crlf ? s.replace(/\n/g, '\r\n') : s);
	console.log('ok', file);
}

function replace(file, from, to) {
	const raw = fs.readFileSync(file, 'utf8');
	const crlf = raw.includes('\r\n');
	let s = raw.replace(/\r\n/g, '\n');
	if (s.includes(to)) { console.log('skip', file); return; }
	if (s.split(from).length !== 2) { console.log('REPLACE ANCHOR BAD', file); fails++; return; }
	s = s.replace(from, to);
	fs.writeFileSync(file, crlf ? s.replace(/\n/g, '\r\n') : s);
	console.log('ok', file);
}

// ---- server
edit(M + 'ProjectHeroMod.java', 'com.projecthero.mod.doctorfate.DoctorFateMod.initialize();',
	'\t\tcom.projecthero.mod.vision.VisionMod.initialize(); // v0.17.3: Vision -- the Synthezoid Cradle, damage, flight payload\n');
edit(M + 'hero/AbilityRouter.java', 'com.projecthero.mod.doctorfate.DoctorFateAbilityManager.handle(player, slot, pressed);',
	'\t\t// v0.17.3: Vision, on the same terms\n\t\tif (com.projecthero.mod.vision.VisionAbilityManager.hasContext(player)) {\n'
	+ '\t\t\tcom.projecthero.mod.vision.VisionAbilityManager.handle(player, slot, pressed);\n\t\t\treturn;\n\t\t}\n', { skipLines: 2 });
edit(M + 'hero/AbilityRouter.java', 'com.projecthero.mod.doctorfate.DoctorFate.tick(player);',
	'\t\tcom.projecthero.mod.vision.Vision.tick(player); // v0.17.3: Vision -- the Mind Stone bar, the body, the toggles, flight, the moves\n');
edit(M + 'hero/HeroTiers.java', '|| com.projecthero.mod.doctorfate.DoctorFate.hasPower(player) // v0.17.3: Doctor Fate',
	'\t\t\t\t|| com.projecthero.mod.vision.Vision.hasPower(player) // v0.17.3: Vision\n');
edit(M + 'hero/HeroTiers.java', '\t\t\t"doctor_fate", // v0.17.3: Doctor Fate',
	'\t\t\t"vision", // v0.17.3: Vision\n');
edit(M + 'hero/HeroTiers.java', 'case "doctor_fate" -> com.projecthero.mod.doctorfate.DoctorFate.hasPower(player); // v0.17.3',
	'\t\t\tcase "vision" -> com.projecthero.mod.vision.Vision.hasPower(player); // v0.17.3\n');
edit(M + 'hero/HeroTiers.java', 'case "doctor_fate" -> { // v0.17.3\n\t\t\t\tif (com.projecthero.mod.doctorfate.DoctorFate.hasPower(player)) {',
	'\t\t\tcase "vision" -> { // v0.17.3\n\t\t\t\tif (com.projecthero.mod.vision.Vision.hasPower(player)) {\n'
	+ '\t\t\t\t\tcom.projecthero.mod.vision.Vision.revoke(player);\n\t\t\t\t}\n\t\t\t}\n', { skipLines: 3, marker: 'case "vision" -> { // v0.17.3' });
edit(M + 'hero/HeroTiers.java', 'if (!excludeHeroKeys.contains("doctor_fate") && com.projecthero.mod.doctorfate.DoctorFate.hasPower(player)) {',
	'\t\tif (!excludeHeroKeys.contains("vision") && com.projecthero.mod.vision.Vision.hasPower(player)) {\n\t\t\treturn true; // v0.17.3\n\t\t}\n',
	{ skipLines: 2 });
edit(M + 'hero/PowerGrants.java', '\t\t\t"doctor_fate", // v0.17.3: Doctor Fate',
	'\t\t\t"vision", // v0.17.3: Vision\n');
edit(M + 'hero/PowerGrants.java', '"doctor_fate", // v0.17.3: only the Helmet of Fate',
	'\t\t\t"vision", // v0.17.3: only the Mind Stone in the Synthezoid Cradle\n');
edit(M + 'hero/PowerGrants.java', 'case "doctor_fate" -> { // v0.17.3',
	'\t\t\tcase "vision" -> { // v0.17.3\n\t\t\t\tboolean ok = com.projecthero.mod.vision.Vision.grant(target);\n'
	+ '\t\t\t\treturn new Result(ok, Component.literal(ok ? "Made " + name + " Vision" : name + " is already Vision"));\n\t\t\t}\n',
	{ skipLines: 3, marker: 'case "vision" -> { // v0.17.3' });
edit(M + 'hero/PowerGrants.java', 'case "doctor_fate" -> com.projecthero.mod.doctorfate.DoctorFate.revoke(target); // v0.17.3',
	'\t\t\tcase "vision" -> com.projecthero.mod.vision.Vision.revoke(target); // v0.17.3\n');
edit(M + 'command/HeroCommand.java', 'case "doctor_fate" -> com.projecthero.mod.doctorfate.DoctorFate.revoke(target); // v0.17.3',
	'\t\t\tcase "vision" -> com.projecthero.mod.vision.Vision.revoke(target); // v0.17.3\n');
edit(M + 'command/ProjectHeroCommand.java', '.then(com.projecthero.mod.doctorfate.DoctorFateMod.command()) // v0.17.3',
	'\t\t\t\t.then(com.projecthero.mod.vision.VisionMod.command()) // v0.17.3\n');
edit(M + 'diagnostics/ServerStateReset.java', 'com.projecthero.mod.doctorfate.DoctorFateMod.clearSessionState();',
	'\t\tcom.projecthero.mod.vision.VisionMod.clearSessionState(); // v0.17.3: Vision -- gripped / thrown / punched creatures, dive targets, flight\n');
edit(M + 'item/ModCreativeTab.java', 'com.projecthero.mod.doctorfate.DoctorFateItems.addToCreativeTab(output);',
	'\t\t\t\tcom.projecthero.mod.vision.VisionCradle.addToCreativeTab(output); // v0.17.3: Vision -- the Synthezoid Cradle\n');
edit(M + 'squad/HeroIdentity.java', 'return "projecthero.squad.identity.doctor_fate"; // v0.17.3',
	'\t\tif (com.projecthero.mod.vision.Vision.hasPower(player)) {\n\t\t\treturn "projecthero.squad.identity.vision"; // v0.17.3\n\t\t}\n',
	{ skipLines: 1 });

// ---- client
edit(C + 'ProjectHeroModClient.java', 'com.projecthero.mod.client.doctorfate.DoctorFateClient.initialize();',
	'\t\tcom.projecthero.mod.client.vision.VisionClient.initialize(); // v0.17.3: Vision -- suit, hologram, cape, HUD, beams\n');
edit(C + 'ProjectHeroModClient.java', 'com.projecthero.mod.client.doctorfate.DoctorFateClient.toggleFlight();',
	'\t\t// v0.17.3: Vision -- double-tap Space in the air takes off / lands (server re-validates)\n'
	+ '\t\tif (com.projecthero.mod.client.vision.VisionClient.wantsFlightToggle(player)) {\n\t\t\tticksSinceJumpPress = Integer.MAX_VALUE;\n'
	+ '\t\t\tcom.projecthero.mod.client.vision.VisionClient.toggleFlight();\n\t\t\treturn;\n\t\t}\n', { skipLines: 2 });
edit(C + 'flight/DirectionalFlight.java', ': com.projecthero.mod.doctorfate.DoctorFateConfig.FLIGHT_CRUISE_BPS) / 20.0);',
	'\t\t// v0.17.3: Vision\'s double-tap-Space flight -- Kryptonian handling, 20 b/s cruise, 36 sprinting\n'
	+ '\t\tif (com.projecthero.mod.vision.Vision.isFlying(player)) {\n'
	+ '\t\t\treturn DirectionalFlightModel.fixedCruise(DirectionalFlightModel.kryptonian(sprint, false), (sprint ? com.projecthero.mod.vision.VisionConfig.FLIGHT_SPRINT_BPS\n'
	+ '\t\t\t\t\t: com.projecthero.mod.vision.VisionConfig.FLIGHT_CRUISE_BPS) / 20.0);\n\t\t}\n', { skipLines: 1 });
edit(C + 'flight/FlightFeel.java', 'return GENERIC; // v0.17.3: Doctor Fate hovers upright',
	'\t\tif (com.projecthero.mod.vision.Vision.isFlying(player)) {\n\t\t\treturn KRYPTONIAN; // v0.17.3: Vision flies like the Kryptonian, cape streaming\n\t\t}\n',
	{ skipLines: 1 });
replace(C + 'FlightPoseHelper.java', '|| com.projecthero.mod.doctorfate.DoctorFate.isFlying(player);',
	'|| com.projecthero.mod.doctorfate.DoctorFate.isFlying(player)\n\t\t\t\t// v0.17.3: Vision\'s double-tap-Space flight\n\t\t\t\t|| com.projecthero.mod.vision.Vision.isFlying(player);');
edit(C + 'gui/PowerInfoScreen.java', 'return com.projecthero.mod.hero.guide.HeroPackGuide.doctorFateChapter(); // v0.17.3',
	'\t\tif (com.projecthero.mod.vision.Vision.hasPower(mc.player)) {\n\t\t\treturn com.projecthero.mod.hero.guide.HeroPackGuide.visionChapter(); // v0.17.3\n\t\t}\n',
	{ skipLines: 1 });
edit(C + 'gui/SquadScreen.java', 'case "doctor_fate": return 0xFFFFC93A; // v0.17.3: Helmet of Fate gold',
	'\t\t\t\tcase "vision": return 0xFFFFD23C; // v0.17.3: Mind Stone gold\n');
// the suit hides the wearer's whole outer layer, like Doctor Fate's
replace(C + 'mixin/PlayerModelMixin.java',
	'|| com.projecthero.mod.client.doctorfate.FateSuitLayer.hidesSkinOverlay(player)) { // v0.17.3 the DCEU skin suit',
	'|| com.projecthero.mod.client.doctorfate.FateSuitLayer.hidesSkinOverlay(player) // v0.17.3 the DCEU skin suit\n'
	+ '\t\t\t\t|| com.projecthero.mod.client.vision.VisionSuitLayer.hidesSkinOverlay(player)) { // v0.17.3 Vision (not under the whole Human Hologram)');

// ---- gametests
edit('src/gametest/resources/fabric.mod.json', '"com.projecthero.mod.gametest.DoctorFateGameTests",',
	'      "com.projecthero.mod.gametest.VisionGameTests",\n');

if (fails) {
	console.log(fails + ' FAILED');
	process.exit(1);
}
