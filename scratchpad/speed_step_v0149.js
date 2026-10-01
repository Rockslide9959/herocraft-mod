// v0.14.9: Super Speed step assist only in the modes (Speed Mode 3, Overdrive 10); Mach Punch a flat 20.
const fs = require('fs');
function edit(f, pairs) {
	let s = fs.readFileSync(f, 'utf8');
	const crlf = s.includes('\r\n');
	s = s.replace(/\r\n/g, '\n');
	for (const [a, b] of pairs) {
		if (!s.includes(a)) throw new Error(f + ' missing: ' + a);
		s = s.split(a).join(b);
	}
	fs.writeFileSync(f, crlf ? s.replace(/\n/g, '\r\n') : s);
}
edit('src/main/java/com/projecthero/mod/hero/power/p04/SuperSpeedHandlers.java', [
	['\t\t\t\tPowerToggles.modifier(player, Attributes.STEP_HEIGHT, PASSIVE_STEP, PASSIVE_STEP_BONUS,\n\t\t\t\t\t\tAttributeModifier.Operation.ADD_VALUE);\n',
		'\t\t\t\tPowerToggles.clearModifier(player, Attributes.STEP_HEIGHT, PASSIVE_STEP); // v0.14.9: no step assist outside the modes\n'],
	['\t\t\tPowerToggles.modifier(p, Attributes.STEP_HEIGHT, PASSIVE_STEP, PASSIVE_STEP_BONUS, AttributeModifier.Operation.ADD_VALUE);\n', ''],
	['PowerToggles.clearModifier(p, Attributes.STEP_HEIGHT, SM_STEP); // v0.14.6: the 3-block passive step covers Speed Mode',
		'movementBoost(p, Attributes.STEP_HEIGHT, SM_STEP, SM_STEP_BONUS, AttributeModifier.Operation.ADD_VALUE); // v0.14.9: 3 blocks, Speed Mode only'],
	['movementBoost(p, Attributes.STEP_HEIGHT, OD_STEP, 7.0, AttributeModifier.Operation.ADD_VALUE); // 3-block passive + 7 = 10',
		'movementBoost(p, Attributes.STEP_HEIGHT, OD_STEP, OD_STEP_BONUS, AttributeModifier.Operation.ADD_VALUE); // v0.14.9: 10 blocks'],
	['\t/** v0.14.6: a permanent 3-block step assist (base 0.6 + 2.4); Overdrive tops it up to 10. */\n\tprivate static final ResourceLocation PASSIVE_STEP = com.projecthero.mod.ProjectHeroMod.id("speed_passive_step");\n\tprivate static final double PASSIVE_STEP_BONUS = 2.4;\n',
		'\t/** v0.14.6 passive step id -- v0.14.9: never applied any more, only cleared from older saves. */\n\tprivate static final ResourceLocation PASSIVE_STEP = com.projecthero.mod.ProjectHeroMod.id("speed_passive_step");\n'
		+ '\t/** v0.14.9: step assist only in the modes -- Speed Mode 3 blocks (base 0.6 + 2.4), Overdrive 10 (0.6 + 9.4). */\n'
		+ '\tpublic static final double SM_STEP_BONUS = 2.4;\n\tpublic static final double OD_STEP_BONUS = 9.4;\n'],
	['Movement passives (+30% speed, swim, 3-block step)', 'Movement passives (+30% speed, swim)'],
	['a 3-block step assist, eating', 'eating'],
]);
edit('src/main/java/com/projecthero/mod/hero/power/p04/SuperSpeedMoves.java', [
	['MACH_MAX = 30.0f;', 'MACH_MAX = 20.0f; // v0.14.9: a flat 20 at any speed'],
	['(20 standing, 30 at full', '(v0.14.9: a flat 20; was 12 standing, 30 at full'],
]);
console.log('ok');
