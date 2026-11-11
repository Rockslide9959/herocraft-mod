// v0.17.3 Spine Whip: gametest edits (the Rib Cage Guard's tests out, the Spine Whip's in). CRLF-safe. Run from the repo root.
const fs = require('fs');
const { edit } = require('./edit_lib.js');
const G = 'src/gametest/java/com/projecthero/mod/gametest/';

edit(G + 'UndeadGameTests.java', [
	['com.projecthero.mod.undead.UndeadRibGuard.ID.equals(com.projecthero.mod.undead.UndeadAbilityManager.idsOf(p, v)[1]),\n\t\t\t\t"V is the Rib Shotgun, Shift+V the Rib Cage Guard");',
	 'com.projecthero.mod.undead.UndeadSpineWhip.ID.equals(com.projecthero.mod.undead.UndeadAbilityManager.idsOf(p, v)[1]),\n\t\t\t\t"V is the Rib Shotgun, Shift+V the Spine Whip");'],
]);
edit(G + 'UndeadRegenGameTests.java', [
	['// v0.17.3 merge: V is Reform and Shift+V is now the Rib Cage Guard (the kit), not Toggle Regen / the Regen Menu',
	 '// v0.17.3 merge: V is the Rib Shotgun and Shift+V the Spine Whip (the kit), not Toggle Regen / the Regen Menu'],
	['com.projecthero.mod.undead.UndeadRibGuard.ID.equals(UndeadAbilityManager.idsOf(p, AbilitySlot.SLOT_5)[1]), "V: Rib Shotgun; Shift+V: Rib Cage Guard");',
	 'com.projecthero.mod.undead.UndeadSpineWhip.ID.equals(UndeadAbilityManager.idsOf(p, AbilitySlot.SLOT_5)[1]), "V: Rib Shotgun; Shift+V: Spine Whip");'],
]);
{
	const f = G + 'UndeadKitGameTests.java';
	let t = fs.readFileSync(f, 'utf8').replace(/\r\n/g, '\n');
	const a = t.indexOf('\t// ---------------------------------------------------------------- Shift+V: Rib Cage Guard');
	const b = t.indexOf('\t// ---------------------------------------------------------------- hold V: the bone katana\'s skills');
	if (a < 0 || b < 0) throw new Error('guard tests not found');
	t = t.slice(0, a) + t.slice(b);
	fs.writeFileSync(f, t.replace(/\n/g, '\r\n'));
	edit(f, [
		['import com.projecthero.mod.undead.UndeadRibGuard;\n', 'import com.projecthero.mod.undead.UndeadSpineWhip;\n'],
		['import net.minecraft.world.entity.projectile.Arrow;\n', ''],
		['helper.assertTrue(!UndeadBoneSword.held(p) && !UndeadRibGuard.up(p), "Shift+V puts it away (no Rib Cage Guard with it)");',
		 'helper.assertTrue(!UndeadBoneSword.held(p) && !UndeadSpineWhip.active(p), "Shift+V puts it away (no Spine Whip with it)");'],
	]);
}
{
	const f = G + 'UndeadVMoveGameTests.java';
	edit(f, [
		['import com.projecthero.mod.undead.UndeadRibGuard;\nimport com.projecthero.mod.undead.UndeadRibShot;\n',
		 'import com.projecthero.mod.undead.UndeadRibShot;\nimport com.projecthero.mod.undead.UndeadSpineWhip;\n'],
		[' * v0.17.3 (user: "a new combat V move for the undead ability"): tap V is the Rib Shotgun ({@link UndeadRibShot}), Shift+V tapped\n * the Rib Cage Guard, hold V the katana, Shift + hold V Reform.',
		 ' * v0.17.3 (user: "a new combat V move for the undead ability"): tap V is the Rib Shotgun ({@link UndeadRibShot}), Shift+V tapped\n * the Spine Whip ({@link UndeadSpineWhip}, user: "give shift+V another combat ability that isnt the rib cage shield"), hold V the\n * katana, Shift + hold V Reform.'],
		['\t/** Nothing from V happened: no shot, no guard, no Reform, no katana. */', '\t/** Nothing from V happened: no shot, no whip, no Reform, no katana. */'],
		['\t\thelper.assertTrue(!UndeadRibGuard.up(p), why + ": no Rib Cage Guard");',
		 '\t\thelper.assertTrue(!UndeadSpineWhip.active(p) && Undead.cooldownRemaining(p, UndeadSpineWhip.ID) == 0, why + ": no Spine Whip");'],
		['\tpublic void theVSlotShowsTheRibShotgunAndTheGuard(GameTestHelper helper) {', '\tpublic void theVSlotShowsTheRibShotgunAndTheSpineWhip(GameTestHelper helper) {'],
		['UndeadRibShot.ID.equals(ids[0]) && UndeadRibGuard.ID.equals(ids[1]), "V: Rib Shotgun, Shift+V: Rib Cage Guard");',
		 'UndeadRibShot.ID.equals(ids[0]) && UndeadSpineWhip.ID.equals(ids[1]), "V: Rib Shotgun, Shift+V: Spine Whip");\n\t\thelper.assertTrue(UndeadAbilityManager.maxCooldown(UndeadSpineWhip.ID) == UndeadKitConfig.SPINE_COOLDOWN, "the HUD knows the whip\'s 7 s cooldown");\n\t\thelper.assertTrue(UndeadKitConfig.SPINE_COOLDOWN == 140 && UndeadKitConfig.SPINE_DAMAGE == 14f && UndeadKitConfig.SPINE_RANGE == 7.0,\n\t\t\t\t"the whip\'s numbers: 7 s, 14 damage, 7 blocks");\n\t\thelper.assertTrue(UndeadKitConfig.REFORM_TAP <= 6, "the whip fires on a quick tap only (well before Reform\'s hold)");'],
		['\t\thelper.assertTrue(!UndeadRibGuard.up(p) && Undead.cooldownRemaining(p, UndeadAbilities.REFORM) == 0 && !UndeadBoneSword.held(p),\n\t\t\t\t"no guard, no Reform, no katana");',
		 '\t\thelper.assertTrue(!UndeadSpineWhip.active(p) && Undead.cooldownRemaining(p, UndeadAbilities.REFORM) == 0 && !UndeadBoneSword.held(p),\n\t\t\t\t"no whip, no Reform, no katana");'],
	]);
	// the guard-folding test out
	let t = fs.readFileSync(f, 'utf8').replace(/\r\n/g, '\n');
	const a = t.indexOf('\t@GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = 40, batch = "undead_vmove_guard")');
	const b = t.indexOf('\t// ---------------------------------------------------------------- the four V inputs never misfire into each other');
	if (a < 0 || b < 0) throw new Error('guard fold test not found');
	t = t.slice(0, a) + fs.readFileSync('scratchpad/undead/spine_tests.java.txt', 'utf8').replace(/\r\n/g, '\n') + t.slice(b);
	fs.writeFileSync(f, t.replace(/\n/g, '\r\n'));
	edit(f, [
		[`	public void shiftVTappedIsOnlyTheGuard(GameTestHelper helper) {
		ServerPlayer p = undead(helper);
		Undead.breakPart(p, UndeadState.LEFT_LEG, UndeadState.KIND_BURST);
		press(p, true, true);
		assertNothing(helper, p, "Shift+V just pressed");
		press(p, true, false);
		helper.assertTrue(UndeadRibGuard.up(p), "a Shift+V tap: the guard");
		helper.assertTrue(!UndeadRibShot.windingUp(p) && Undead.cooldownRemaining(p, UndeadAbilities.REFORM) == 0 && Undead.missing(p, UndeadState.LEFT_LEG),
				"not the shot, not Reform");
		UndeadRibGuard.forTestsLower(p);
		edit(p, n -> n.abilityReadyAt.clear());
		// let go of Shift before V: still the guard (the press decided)
		press(p, true, true);
		press(p, false, false);
		helper.assertTrue(UndeadRibGuard.up(p) && !UndeadRibShot.windingUp(p), "Shift let go first: still the guard");`,
		 `	public void shiftVTappedIsOnlyTheSpineWhip(GameTestHelper helper) {
		ServerPlayer p = undead(helper);
		Undead.breakPart(p, UndeadState.LEFT_LEG, UndeadState.KIND_BURST);
		press(p, true, true);
		assertNothing(helper, p, "Shift+V just pressed");
		press(p, true, false);
		helper.assertTrue(UndeadSpineWhip.windingUp(p) && Undead.state(p).animId == UndeadKitConfig.ANIM_SPINE_WHIP, "a Shift+V tap: the whip");
		helper.assertTrue(!UndeadRibShot.windingUp(p) && Undead.cooldownRemaining(p, UndeadAbilities.REFORM) == 0 && Undead.missing(p, UndeadState.LEFT_LEG),
				"not the shot, not Reform");
		UndeadSpineWhip.forTestsCancel(p);
		edit(p, n -> n.abilityReadyAt.clear());
		// let go of Shift before V: still the whip (the press decided)
		press(p, true, true);
		press(p, false, false);
		helper.assertTrue(UndeadSpineWhip.windingUp(p) && !UndeadRibShot.windingUp(p), "Shift let go first: still the whip");`],
		['helper.assertTrue(!UndeadRibGuard.up(p) && Undead.cooldownRemaining(p, UndeadAbilities.REFORM) == 0, "never the guard or Reform");',
		 'helper.assertTrue(!UndeadSpineWhip.active(p) && Undead.cooldownRemaining(p, UndeadAbilities.REFORM) == 0, "never the whip or Reform");'],
		['helper.assertTrue(!UndeadRibGuard.up(p) && Undead.missing(p, UndeadState.LEFT_LEG), "no guard, nothing re-formed yet");',
		 'helper.assertTrue(!UndeadSpineWhip.active(p) && Undead.missing(p, UndeadState.LEFT_LEG), "no whip, nothing re-formed yet");'],
		['helper.assertTrue(!UndeadRibGuard.up(p) && !UndeadRibShot.windingUp(p) && !UndeadBoneSword.held(p), "letting go after: no guard, no shot, no katana");',
		 'helper.assertTrue(!UndeadSpineWhip.active(p) && !UndeadRibShot.windingUp(p) && !UndeadBoneSword.held(p), "letting go after: no whip, no shot, no katana");'],
		['helper.assertTrue(!UndeadRibGuard.up(p) && !UndeadRibShot.windingUp(p), "and the let-go is not a guard or a shot");',
		 'helper.assertTrue(!UndeadSpineWhip.active(p) && !UndeadRibShot.windingUp(p), "and the let-go is not a whip or a shot");'],
		['Undead.cooldownRemaining(p, UndeadRibShot.ID) == 0 && !UndeadRibGuard.up(p)\n', 'Undead.cooldownRemaining(p, UndeadRibShot.ID) == 0 && !UndeadSpineWhip.active(p)\n'],
		['helper.assertTrue(Undead.cooldownRemaining(p, UndeadAbilities.REFORM) == 0 && !UndeadRibGuard.up(p), "no Reform, no guard with it");',
		 'helper.assertTrue(Undead.cooldownRemaining(p, UndeadAbilities.REFORM) == 0 && !UndeadSpineWhip.active(p), "no Reform, no whip with it");'],
	]);
}
