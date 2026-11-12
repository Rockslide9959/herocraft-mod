// node scratchpad/doctorfate/wire_shared.js -- appends Doctor Fate's lines to the shared files, each right after a Spin anchor.
// Idempotent (skips a file that already mentions the inserted text). CRLF-safe.
'use strict';
const fs = require('fs');
const M = 'src/main/java/com/projecthero/mod/';
const C = 'src/client/java/com/projecthero/mod/client/';
let fails = 0;

function edit(file, anchor, insert, opts = {}) {
	let raw = fs.readFileSync(file, 'utf8');
	const crlf = raw.includes('\r\n');
	let s = raw.replace(/\r\n/g, '\n');
	const marker = opts.marker || insert.trim().split('\n')[0].trim();
	if (s.includes(marker)) {
		console.log('skip (already there)', file, marker.slice(0, 60));
		return;
	}
	const at = s.indexOf(anchor);
	if (at < 0 || s.indexOf(anchor, at + 1) >= 0 && !opts.first) {
		console.log('ANCHOR', at < 0 ? 'MISSING' : 'NOT UNIQUE', file, anchor.slice(0, 80));
		fails++;
		return;
	}
	let end = at + anchor.length;
	if (opts.before) {
		end = s.lastIndexOf('\n', at) + 1;
	} else if (opts.lineEnd !== false) {
		end = s.indexOf('\n', end) + 1; // after the anchor's line
	}
	if (opts.skipLines) {
		for (let i = 0; i < opts.skipLines; i++) end = s.indexOf('\n', end) + 1;
	}
	s = s.slice(0, end) + insert + s.slice(end);
	fs.writeFileSync(file, crlf ? s.replace(/\n/g, '\r\n') : s);
	console.log('ok', file);
}

function replace(file, from, to) {
	let raw = fs.readFileSync(file, 'utf8');
	const crlf = raw.includes('\r\n');
	let s = raw.replace(/\r\n/g, '\n');
	if (s.includes(to)) { console.log('skip', file); return; }
	if (s.split(from).length !== 2) { console.log('REPLACE ANCHOR BAD', file); fails++; return; }
	s = s.replace(from, to);
	fs.writeFileSync(file, crlf ? s.replace(/\n/g, '\r\n') : s);
	console.log('ok', file);
}

// ---- server
edit(M + 'ProjectHeroMod.java', 'com.projecthero.mod.spin.SpinMod.initialize();',
	'\t\tcom.projecthero.mod.doctorfate.DoctorFateMod.initialize(); // v0.17.3: Doctor Fate -- items, Tomb of Nabu, damage, payloads\n');
edit(M + 'hero/AbilityRouter.java', 'com.projecthero.mod.spin.SpinAbilityManager.handle(player, slot, pressed);',
	'\t\t// v0.17.3: Doctor Fate, on the same terms\n\t\tif (com.projecthero.mod.doctorfate.DoctorFateAbilityManager.hasContext(player)) {\n'
	+ '\t\t\tcom.projecthero.mod.doctorfate.DoctorFateAbilityManager.handle(player, slot, pressed);\n\t\t\treturn;\n\t\t}\n', { skipLines: 2 });
edit(M + 'hero/AbilityRouter.java', 'com.projecthero.mod.spin.Spin.tick(player);',
	'\t\tcom.projecthero.mod.doctorfate.DoctorFate.tick(player); // v0.17.3: Doctor Fate -- Dominion, the helmet, Possession, bargains, spells\n');
edit(M + 'hero/HeroTiers.java', '|| com.projecthero.mod.spin.Spin.hasPower(player) // v0.17.3: the Spin (the Spin)',
	'\t\t\t\t|| com.projecthero.mod.doctorfate.DoctorFate.hasPower(player) // v0.17.3: Doctor Fate\n');
edit(M + 'hero/HeroTiers.java', '\t\t\t"spin", // v0.17.3: the Spin (the Spin)',
	'\t\t\t"doctor_fate", // v0.17.3: Doctor Fate\n');
edit(M + 'hero/HeroTiers.java', 'case "spin" -> com.projecthero.mod.spin.Spin.hasPower(player); // v0.17.3',
	'\t\t\tcase "doctor_fate" -> com.projecthero.mod.doctorfate.DoctorFate.hasPower(player); // v0.17.3\n');
edit(M + 'hero/HeroTiers.java', 'case "spin" -> { // v0.17.3\n\t\t\t\tif (com.projecthero.mod.spin.Spin.hasPower(player)) {',
	'\t\t\tcase "doctor_fate" -> { // v0.17.3\n\t\t\t\tif (com.projecthero.mod.doctorfate.DoctorFate.hasPower(player)) {\n'
	+ '\t\t\t\t\tcom.projecthero.mod.doctorfate.DoctorFate.revoke(player);\n\t\t\t\t}\n\t\t\t}\n', { skipLines: 3, marker: 'case "doctor_fate" -> { // v0.17.3' });
edit(M + 'hero/HeroTiers.java', 'if (!excludeHeroKeys.contains("spin") && com.projecthero.mod.spin.Spin.hasPower(player)) {',
	'\t\tif (!excludeHeroKeys.contains("doctor_fate") && com.projecthero.mod.doctorfate.DoctorFate.hasPower(player)) {\n\t\t\treturn true; // v0.17.3\n\t\t}\n',
	{ skipLines: 2 });
edit(M + 'hero/PowerGrants.java', '\t\t\t"spin", // v0.17.3: the Spin (the Spin)',
	'\t\t\t"doctor_fate", // v0.17.3: Doctor Fate\n');
edit(M + 'hero/PowerGrants.java', '"spin", // v0.17.3: only the Zeppeli Steel Balls',
	'\t\t\t"doctor_fate", // v0.17.3: only the Helmet of Fate (the Tomb of Nabu)\n');
edit(M + 'hero/PowerGrants.java', 'case "spin" -> { // v0.17.3',
	'\t\t\tcase "doctor_fate" -> { // v0.17.3\n\t\t\t\tboolean ok = com.projecthero.mod.doctorfate.DoctorFate.grant(target);\n'
	+ '\t\t\t\treturn new Result(ok, Component.literal(ok ? name + " now serves Nabu (Doctor Fate)" : name + " already wears the Helmet of Fate"));\n\t\t\t}\n',
	{ skipLines: 3, marker: 'case "doctor_fate" -> { // v0.17.3' });
edit(M + 'hero/PowerGrants.java', 'case "spin" -> com.projecthero.mod.spin.Spin.revoke(target); // v0.17.3',
	'\t\t\tcase "doctor_fate" -> com.projecthero.mod.doctorfate.DoctorFate.revoke(target); // v0.17.3\n');
edit(M + 'command/HeroCommand.java', 'case "spin" -> com.projecthero.mod.spin.Spin.revoke(target); // v0.17.3',
	'\t\t\tcase "doctor_fate" -> com.projecthero.mod.doctorfate.DoctorFate.revoke(target); // v0.17.3\n');
edit(M + 'squad/HeroIdentity.java', 'return "projecthero.squad.identity.spin"; // v0.17.3',
	'\t\tif (com.projecthero.mod.doctorfate.DoctorFate.hasPower(player)) {\n\t\t\treturn "projecthero.squad.identity.doctor_fate"; // v0.17.3\n\t\t}\n',
	{ skipLines: 1 });
edit(M + 'command/ProjectHeroCommand.java', '.then(com.projecthero.mod.spin.SpinMod.command()) // v0.17.3',
	'\t\t\t\t.then(com.projecthero.mod.doctorfate.DoctorFateMod.command()) // v0.17.3\n');
edit(M + 'diagnostics/ServerStateReset.java', 'com.projecthero.mod.spin.SpinMod.clearSessionState();',
	'\t\tcom.projecthero.mod.doctorfate.DoctorFateMod.clearSessionState(); // v0.17.3: Doctor Fate -- chains, storms, wards, whispers, possession\n');
edit(M + 'item/ModCreativeTab.java', 'com.projecthero.mod.spin.SpinItems.addToCreativeTab(output);',
	'\t\t\t\tcom.projecthero.mod.doctorfate.DoctorFateItems.addToCreativeTab(output); // v0.17.3: Doctor Fate\n');

// ---- client
edit(C + 'ProjectHeroModClient.java', 'com.projecthero.mod.client.spin.SpinClient.initialize();',
	'\t\tcom.projecthero.mod.client.doctorfate.DoctorFateClient.initialize(); // v0.17.3: Doctor Fate -- HUD, suit, Avatar, bargains, possession\n');
edit(C + 'ProjectHeroModClient.java', '\t\t\tcom.projecthero.mod.client.saiyan.SaiyanClient.toggleFlight();',
	'\t\t// v0.17.3: Doctor Fate -- double-tap Space in the air with the helmet on (server re-validates)\n'
	+ '\t\tif (com.projecthero.mod.client.doctorfate.DoctorFateClient.wantsFlightToggle(player)) {\n\t\t\tticksSinceJumpPress = Integer.MAX_VALUE;\n'
	+ '\t\t\tcom.projecthero.mod.client.doctorfate.DoctorFateClient.toggleFlight();\n\t\t\treturn;\n\t\t}\n\n', { skipLines: 3 });
edit(C + 'gui/PowerInfoScreen.java', 'return com.projecthero.mod.hero.guide.HeroPackGuide.spinChapter(); // v0.17.3',
	'\t\tif (com.projecthero.mod.doctorfate.DoctorFate.hasPower(mc.player)) {\n\t\t\treturn com.projecthero.mod.hero.guide.HeroPackGuide.doctorFateChapter(); // v0.17.3\n\t\t}\n',
	{ skipLines: 1 });
edit(C + 'gui/SquadScreen.java', 'case "spin": return 0xFF4FB86A;',
	'\t\t\t\tcase "doctor_fate": return 0xFFFFC93A; // v0.17.3: Helmet of Fate gold\n');
edit(C + 'flight/DirectionalFlight.java', '\t\t// v0.17.3: the Saiyan\'s double-tap-jump flight',
	'\t\t// v0.17.3: Doctor Fate\'s golden-ankh hover flight (double-tap Space) -- Kryptonian handling, 14 b/s cruise, 28 sprinting\n'
	+ '\t\tif (com.projecthero.mod.doctorfate.DoctorFate.isFlying(player)) {\n'
	+ '\t\t\treturn DirectionalFlightModel.fixedCruise(DirectionalFlightModel.kryptonian(sprint, false), (sprint ? com.projecthero.mod.doctorfate.DoctorFateConfig.FLIGHT_SPRINT_BPS\n'
	+ '\t\t\t\t\t: com.projecthero.mod.doctorfate.DoctorFateConfig.FLIGHT_CRUISE_BPS) / 20.0);\n\t\t}\n', { before: true, marker: 'Doctor Fate\'s golden-ankh hover flight' });
edit(C + 'flight/FlightFeel.java', 'return KRYPTONIAN; // v0.17.3: the Saiyan banks and leans like the Kryptonian',
	'\t\tif (com.projecthero.mod.doctorfate.DoctorFate.isFlying(player)) {\n\t\t\treturn GENERIC; // v0.17.3: Doctor Fate hovers upright, cloak billowing\n\t\t}\n',
	{ skipLines: 1 });
replace(C + 'FlightPoseHelper.java', '|| com.projecthero.mod.saiyan.Saiyan.isFlying(player);',
	'|| com.projecthero.mod.saiyan.Saiyan.isFlying(player)\n\t\t\t\t// v0.17.3: Doctor Fate\'s hover flight\n\t\t\t\t|| com.projecthero.mod.doctorfate.DoctorFate.isFlying(player);');
replace('src/client/resources/projecthero.client.mixins.json', '"GreenLanternMechGuiMixin"\n',
	'"GreenLanternMechGuiMixin",\n\t\t"FateInputMixin",\n\t\t"FateMouseMixin",\n\t\t"FateSightGlowMixin",\n\t\t"FateSightColorMixin"\n');

console.log(fails ? fails + ' FAILED' : 'all anchors found');
