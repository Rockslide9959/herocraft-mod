// v0.17.3: Andy -- gametests for hold-V Bone Sword, Shift+V regen speed, tap-V Tear Off.
const fs = require('fs');
const G = 'src/gametest/java/com/projecthero/mod/gametest/AndyGameTests.java';
let t = fs.readFileSync(G, 'utf8');
const crlf = t.includes('\r\n');
t = t.replace(/\r\n/g, '\n');
function rep(a, b) {
  if (!t.includes(a)) throw new Error('missing: ' + a.slice(0, 100));
  t = t.replace(a, b);
}
// Shift+V cycles the speed now; a tap of V is Tear Off
rep(`	public void tapVCyclesTheSpeedOnThePress(GameTestHelper helper) {
		ServerPlayer p = andy(helper);
		com.projecthero.mod.hero.AbilitySlot v = com.projecthero.mod.hero.AbilitySlot.SLOT_5;
		com.projecthero.mod.andy.AndyAbilityManager.handle(p, v, true);
		helper.assertTrue(Andy.regenSpeed(p) == AndyConfig.SPEED_FAST, "a press of V cycles the speed (there is no style to hold for any more)");
		com.projecthero.mod.andy.AndyAbilityManager.handle(p, v, false);
		helper.assertTrue(Andy.regenSpeed(p) == AndyConfig.SPEED_FAST, "the release does nothing");
		helper.succeed();
	}`,
`	public void shiftVCyclesTheSpeedOnThePress(GameTestHelper helper) {
		// v0.17.3 (user: "the regen speed switch shift+V")
		ServerPlayer p = andy(helper);
		com.projecthero.mod.hero.AbilitySlot v = com.projecthero.mod.hero.AbilitySlot.SLOT_5;
		p.setShiftKeyDown(true);
		com.projecthero.mod.andy.AndyAbilityManager.handle(p, v, true);
		helper.assertTrue(Andy.regenSpeed(p) == AndyConfig.SPEED_FAST, "a press of Shift+V cycles the speed");
		com.projecthero.mod.andy.AndyAbilityManager.handle(p, v, false);
		p.setShiftKeyDown(false);
		helper.assertTrue(Andy.regenSpeed(p) == AndyConfig.SPEED_FAST, "the release does nothing");
		helper.assertTrue(!Andy.missing(p, Andy.otherArm(Andy.mainArm(p))), "and tears nothing off");
		helper.succeed();
	}`);
rep(`		helper.assertTrue(AndyBoneSword.BONE_SWORD.equals(com.projecthero.mod.andy.AndyAbilityManager.idsOf(p, v)[1]), "Shift+V is the Bone Sword");`,
`		helper.assertTrue(AndyAbilities.TEAR_OFF.equals(com.projecthero.mod.andy.AndyAbilityManager.idsOf(p, v)[0])
				&& AndyAbilities.REGEN.equals(com.projecthero.mod.andy.AndyAbilityManager.idsOf(p, v)[1]), "V is Tear Off, Shift+V the speed");`);
// the draw is a 2 s hold now
rep(`	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = 200)
	public void shiftVRipsAnArmIntoTheBoneSword(GameTestHelper helper) {
		ServerPlayer p = andy(helper);
		int main = Andy.mainArm(p), off = Andy.otherArm(main);
		double baseReach = p.getAttributeValue(Attributes.ENTITY_INTERACTION_RANGE);
		p.setShiftKeyDown(true);
		com.projecthero.mod.andy.AndyAbilityManager.handle(p, com.projecthero.mod.hero.AbilitySlot.SLOT_5, true);
		com.projecthero.mod.andy.AndyAbilityManager.handle(p, com.projecthero.mod.hero.AbilitySlot.SLOT_5, false);
		p.setShiftKeyDown(false);
		helper.assertTrue(AndyBoneSword.held(p), "Shift+V draws the sword");`,
`	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = 300)
	public void holdVRipsAnArmIntoTheBoneSword(GameTestHelper helper) {
		// v0.17.3 (user: "make the bone sword hold V for 2 seconds"): the hold is the struggle; at its end the arm tears off
		ServerPlayer p = andy(helper);
		int main = Andy.mainArm(p), off = Andy.otherArm(main);
		double baseReach = p.getAttributeValue(Attributes.ENTITY_INTERACTION_RANGE);
		com.projecthero.mod.hero.AbilitySlot v = com.projecthero.mod.hero.AbilitySlot.SLOT_5;
		com.projecthero.mod.andy.AndyAbilityManager.handle(p, v, true);
		for (int i = 0; i < AndyConfig.BONE_SWORD_TAP + 2; i++) {
			helper.getLevel().setDayTime(helper.getLevel().getDayTime()); // (no-op: the time is advanced below)
		}
		long t0 = helper.getLevel().getGameTime();
		java.util.function.LongConsumer at = dt -> {
			while (helper.getLevel().getGameTime() < t0 + dt) {
				break;
			}
		};
		at.accept(0);
		holdV(helper, p, AndyConfig.BONE_SWORD_TAP + 3);
		helper.assertTrue(Andy.state(p).animId == AndyState.ANIM_SWORD_STRUGGLE && !AndyBoneSword.held(p) && !Andy.missing(p, off),
				"past a tap he struggles with the arm; it is still on");
		holdV(helper, p, AndyConfig.BONE_SWORD_HOLD + 1);
		com.projecthero.mod.andy.AndyAbilityManager.handle(p, v, false);
		helper.assertTrue(AndyBoneSword.held(p), "held 2 s, V draws the sword");`);
rep(`			AndyBoneSword.toggle(p);
			helper.assertTrue(!AndyBoneSword.held(p), "Shift+V again puts it away");`,
`			com.projecthero.mod.andy.AndyAbilityManager.handle(p, com.projecthero.mod.hero.AbilitySlot.SLOT_5, true);
			com.projecthero.mod.andy.AndyAbilityManager.handle(p, com.projecthero.mod.hero.AbilitySlot.SLOT_5, false);
			helper.assertTrue(!AndyBoneSword.held(p), "V again puts it away");`);
// new tests after noSpareArmNoBoneSword
rep(`	private static ServerPlayer swordsman(GameTestHelper helper) {`,
`	/** V held down for {@code ticks} game ticks' worth of the hold (the hold is timed off the game time: it is wound back). */
	private static void holdV(GameTestHelper helper, ServerPlayer p, int ticks) {
		// the hold started at the press; stand the press back in time and let the hold tick
		com.projecthero.mod.andy.AndyAbilityManager.forTestsBackdateV(p, ticks);
		Andy.tick(p);
	}

	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void lettingGoOfVEarlyLetsGoOfTheArm(GameTestHelper helper) {
		ServerPlayer p = andy(helper);
		int off = Andy.otherArm(Andy.mainArm(p));
		com.projecthero.mod.hero.AbilitySlot v = com.projecthero.mod.hero.AbilitySlot.SLOT_5;
		com.projecthero.mod.andy.AndyAbilityManager.handle(p, v, true);
		holdV(helper, p, AndyConfig.BONE_SWORD_TAP + 10);
		helper.assertTrue(Andy.state(p).animId == AndyState.ANIM_SWORD_STRUGGLE, "struggling");
		com.projecthero.mod.andy.AndyAbilityManager.handle(p, v, false);
		helper.assertTrue(Andy.state(p).animId != AndyState.ANIM_SWORD_STRUGGLE && !AndyBoneSword.held(p) && !Andy.missing(p, off),
				"let go early: no sword, the arm stays on");
		helper.assertTrue(Andy.cooldownRemaining(p, AndyAbilities.TEAR_OFF) == 0, "and it was no tap: nothing torn off");
		helper.succeed();
	}

	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void tapVTearsOffTheWorstLimbAndWhatIsOnHim(GameTestHelper helper) {
		// v0.17.3 (user: "put another move on regular V")
		ServerPlayer p = andy(helper);
		Andy.damagePart(p, AndyState.LEFT_LEG, 5f);
		p.igniteForSeconds(8f);
		p.addEffect(new net.minecraft.world.effect.MobEffectInstance(MobEffects.POISON, 200, 1));
		p.addEffect(new net.minecraft.world.effect.MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 200, 1));
		p.addEffect(new net.minecraft.world.effect.MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 200, 0));
		com.projecthero.mod.hero.AbilitySlot v = com.projecthero.mod.hero.AbilitySlot.SLOT_5;
		com.projecthero.mod.andy.AndyAbilityManager.handle(p, v, true);
		com.projecthero.mod.andy.AndyAbilityManager.handle(p, v, false);
		helper.assertTrue(Andy.missing(p, AndyState.LEFT_LEG) && !Andy.missing(p, AndyState.RIGHT_LEG), "the hurt leg was torn off");
		helper.assertTrue(!p.isOnFire() && !p.hasEffect(MobEffects.POISON) && !p.hasEffect(MobEffects.MOVEMENT_SLOWDOWN), "fire, poison and slowness went with it");
		helper.assertTrue(p.hasEffect(MobEffects.DAMAGE_RESISTANCE), "a good effect stays");
		helper.assertTrue(Andy.cooldownRemaining(p, AndyAbilities.TEAR_OFF) > 0, "a cooldown");
		AABB box = new AABB(p.blockPosition()).inflate(4);
		helper.assertTrue(!helper.getLevel().getEntitiesOfClass(AndyPartEntity.class, box, e -> e.bodyPart() == AndyState.LEFT_LEG).isEmpty(), "the leg is flung aside");
		// nothing hurt: the off arm
		Andy.restore(p);
		edit(p, n -> n.abilityReadyAt.clear());
		int off = Andy.otherArm(Andy.mainArm(p));
		helper.assertTrue(AndyBoneSword.tearOffTarget(p) == off, "whole: the off arm");
		// one hand left: never that one
		Andy.restore(p);
		Andy.breakPart(p, off, AndyState.KIND_BURST);
		Andy.damagePart(p, Andy.mainArm(p), 6f);
		int target = AndyBoneSword.tearOffTarget(p);
		helper.assertTrue(target != Andy.mainArm(p) && target >= AndyState.RIGHT_LEG, "the last hand stays: " + target);
		helper.succeed();
	}

	private static ServerPlayer swordsman(GameTestHelper helper) {`);
// tidy the scaffolding left in holdV's test (the loop and lambda do nothing)
rep(`		for (int i = 0; i < AndyConfig.BONE_SWORD_TAP + 2; i++) {
			helper.getLevel().setDayTime(helper.getLevel().getDayTime()); // (no-op: the time is advanced below)
		}
		long t0 = helper.getLevel().getGameTime();
		java.util.function.LongConsumer at = dt -> {
			while (helper.getLevel().getGameTime() < t0 + dt) {
				break;
			}
		};
		at.accept(0);
`, '');
fs.writeFileSync(G, crlf ? t.replace(/\n/g, '\r\n') : t);
console.log('ok');
