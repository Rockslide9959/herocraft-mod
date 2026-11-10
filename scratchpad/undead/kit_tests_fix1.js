// v0.17.3 Undead kit: test fixes after the first run (batches for the long-reach moves, the hold/tap guard, the shoulder numbers).
const fs = require('fs');
function edit(f, fn) {
	let t = fs.readFileSync(f, 'utf8');
	const crlf = t.includes('\r\n');
	t = t.replace(/\r\n/g, '\n');
	t = fn(t);
	if (crlf) t = t.replace(/\n/g, '\r\n');
	fs.writeFileSync(f, t);
}
function rep(t, a, b) {
	if (!t.includes(a)) throw new Error('not found: ' + a.slice(0, 90));
	return t.replace(a, b);
}
edit('src/gametest/java/com/projecthero/mod/gametest/UndeadGameTests.java', t => rep(t,
	`&& UndeadAbilities.REGEN.equals(com.projecthero.mod.undead.UndeadAbilityManager.idsOf(p, v)[1]), "V is Reform, Shift+V the speed");`,
	`&& com.projecthero.mod.undead.UndeadRibGuard.ID.equals(com.projecthero.mod.undead.UndeadAbilityManager.idsOf(p, v)[1]),
				"V is Reform, Shift+V the Rib Cage Guard");`));
edit('src/gametest/java/com/projecthero/mod/gametest/UndeadKitGameTests.java', t => {
	// the long-reach moves run alone: a 30-block drill, a 10-block blast and an 8-block dash reach the neighbouring tests
	t = rep(t, `	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = 80)
	public void vortexBulletTwistsThenBoresThroughALine`, `	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = 80, batch = "undeadkit_vortex_alone")
	public void vortexBulletTwistsThenBoresThroughALine`);
	t = rep(t, `	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = 120)
	public void unluckMeteorFallsAfterFiveSecondsAndBlasts`, `	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = 120, batch = "undeadkit_meteor_alone")
	public void unluckMeteorFallsAfterFiveSecondsAndBlasts`);
	t = rep(t, `	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = 120)
	public void theKatanasSkillsHit`, `	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = 120, batch = "undeadkit_katana_alone")
	public void theKatanasSkillsHit`);
	// the flash: check he ends where the draw ends (a wall nearby may cut it short), and that it went a good way
	t = rep(t, `					helper.assertTrue(p.position().distanceTo(helper.absoluteVec(new Vec3(2.5, 2.0, 2.5))) > UndeadKitConfig.K_FLASH_DISTANCE - 1.0,
							"he went 8 blocks: " + p.position().distanceTo(helper.absoluteVec(new Vec3(2.5, 2.0, 2.5))));`,
		`					Vec3 end = UndeadBoneSword.forTestsFlashEnd(p);
					helper.assertTrue(end != null && p.position().distanceTo(end) < 0.3, "he is where the draw ends: " + p.position() + " / " + end);
					helper.assertTrue(p.position().distanceTo(helper.absoluteVec(new Vec3(2.5, 2.0, 2.5))) > UndeadKitConfig.K_FLASH_DISTANCE - 1.0,
							"he went 8 blocks: " + p.position().distanceTo(helper.absoluteVec(new Vec3(2.5, 2.0, 2.5))));`);
	// hold, let go, then a fresh hold (a tick later, with the cooldown cleared)
	t = rep(t, `			if (t[0] == 20) {
				helper.assertTrue(UndeadRibGuard.up(p), "held: up");
				press(p, AbilitySlot.SLOT_5, true, false);
				p.setShiftKeyDown(false);
				Undead.tick(p);
				helper.assertTrue(!UndeadRibGuard.up(p), "a hold let go: down");
				edit(p, n -> n.abilityReadyAt.clear());
				press(p, AbilitySlot.SLOT_5, true, true); // and held on
				p.setShiftKeyDown(false);
			}
			if (t[0] == 20 + UndeadKitConfig.RIB_TICKS - 5) {
				helper.assertTrue(UndeadRibGuard.up(p), "still held: still up");
			}
			if (t[0] == 20 + UndeadKitConfig.RIB_TICKS + 3) {`, `			if (t[0] == 20) {
				helper.assertTrue(UndeadRibGuard.up(p), "held: up");
				press(p, AbilitySlot.SLOT_5, true, false);
				p.setShiftKeyDown(false);
				helper.assertTrue(!UndeadRibGuard.up(p), "a hold let go: down");
			}
			if (t[0] == 21) {
				helper.assertTrue(!Undead.state(p).flag(UndeadKitConfig.FLAG_RIB_GUARD) && Undead.cooldownRemaining(p, UndeadRibGuard.ID) > 0, "folded away, cooling down");
				edit(p, n -> n.abilityReadyAt.clear());
				press(p, AbilitySlot.SLOT_5, true, true); // and held on
				p.setShiftKeyDown(false);
				helper.assertTrue(UndeadRibGuard.up(p), "up again");
			}
			if (t[0] == 21 + UndeadKitConfig.RIB_TICKS - 5) {
				helper.assertTrue(UndeadRibGuard.up(p), "still held: still up");
			}
			if (t[0] == 21 + UndeadKitConfig.RIB_TICKS + 3) {`);
	// the attribute is only refreshed by vanilla on the tick after a hand change: compare with the bare hand plus the sword's +6
	t = rep(t, `		double held = p.getAttributeValue(Attributes.ATTACK_DAMAGE); // with the diamond sword in hand`,
		`		double held = p.getAttributeValue(Attributes.ATTACK_DAMAGE) + 6.0; // a held diamond sword adds 6 to the bare hand`);
	return t;
});
console.log('ok');
