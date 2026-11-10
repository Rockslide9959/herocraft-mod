// v0.17.3 NBC-style regeneration: AndyGameTests -- the food / speed / head-only-plan tests out, NBC timing, order, menu,
// Toggle Regen and armour tests in.
const fs = require('fs');
const G = 'src/gametest/java/com/projecthero/mod/gametest/AndyGameTests.java';
let t = fs.readFileSync(G, 'utf8'); const crlf = t.includes('\r\n'); t = t.replace(/\r\n/g, '\n');
function removeMethod(name) {
  const at = t.indexOf('\tpublic void ' + name + '(');
  if (at < 0) throw new Error('no method ' + name);
  // back to the @GameTest line (and a javadoc right above it)
  let start = t.lastIndexOf('\t@GameTest', at);
  const jd = t.lastIndexOf('\t/**', start);
  if (jd >= 0 && t.slice(jd, start).indexOf('*/') >= 0 && t.slice(t.indexOf('*/', jd), start).trim() === '*/') {
    start = jd;
  }
  const end = t.indexOf('\n\t}\n', at) + 4;
  t = t.slice(0, start) + t.slice(end).replace(/^\n/, '');
}
for (const m of ['aBrokenPartRegrowsAfterTheDelayForHunger', 'theBodyRegrowsBeforeTheRest', 'bothArmsStartTogetherOnceTheTorsoHasShoulders',
  'bothLegsStartTogetherWhenTheTorsoIsDone', 'limbsLostWithTheTorsoWholeRegrowInParallel', 'aPartPaysItsShareUpFrontSaturationFirst',
  'anUnpaidPartTakesTwiceNormalInAnyMode', 'onlyTheUnpaidPartIsSlow', 'vTogglesFastRegeneration', 'shiftVCyclesTheSpeedOnThePress',
  'theSpeedIsSavedAndOldSavesWithAStyleStillLoad', 'aRegrowthTakesTheThreeLayerTime', 'aWholeBodyCostsTheSpeedsShareOfTheHungerBar',
  'heFeedsHimselfPlainFood', 'regenOffKeepsPartsGone', 'anEmptyStomachRegrowsNothingButTheHead', 'burstHealsWoundsQuickly',
  'headOnlyRegrowthOrder', 'theStagesFollowTheParts', 'reformCostsTheUnpaidRegrowthPlusFour', 'reformFailsWithoutFood']) {
  removeMethod(m);
}
const d1 = 'helper.runAfterDelay(AndyConfig.REGROW_DELAY[AndyConfig.SPEED_NORMAL] + 20, () -> {';
const d2 = 'helper.runAfterDelay(AndyConfig.REGROW_DELAY[AndyConfig.SPEED_NORMAL] + 25, () -> {';
if (!t.includes(d1) || !t.includes(d2)) throw new Error('sword delays');
t = t.replace(d1, 'helper.runAfterDelay(30, () -> {').replace(d2, 'helper.runAfterDelay(35, () -> {');

const tests = `
	// ---------------------------------------------------------------- v0.17.3: NBC Heroes' Regenerative Segmentation

	/** A lost arm (and its hand) grows back at once, one frame a tick: the arm in 18, the hand from the arm's 5th frame in 12. */
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = 100)
	public void segmentsRegrowAtNbcSpeed(GameTestHelper helper) {
		ServerPlayer p = andy(helper);
		Andy.breakPart(p, AndyState.LEFT_ARM, AndyState.KIND_BURST);
		helper.assertTrue(Andy.missing(p, AndyState.LEFT_HAND), "the hand goes with the arm");
		long[] armStart = { 0L }, handStart = { 0L };
		helper.onEachTick(() -> {
			Andy.tick(p);
			AndyState s = Andy.state(p);
			long now = helper.getLevel().getGameTime();
			if (armStart[0] == 0L) {
				armStart[0] = s.regrowStart(AndyState.LEFT_ARM);
				helper.assertTrue(armStart[0] == now, "the arm starts at once (no wait), free");
				helper.assertTrue(s.regrowEnd(AndyState.LEFT_ARM) - armStart[0] == 18, "an arm is 18 frames");
			}
			if (handStart[0] == 0L && s.regrowStart(AndyState.LEFT_HAND) > 0L) {
				handStart[0] = s.regrowStart(AndyState.LEFT_HAND);
				helper.assertTrue(handStart[0] == armStart[0] + AndyConfig.HAND_NEEDS_ARM, "the hand starts once the arm has 5 frames: " + (handStart[0] - armStart[0]));
				helper.assertTrue(s.regrowEnd(AndyState.LEFT_HAND) - handStart[0] == 12, "a hand is 12 frames");
			}
			if (now == armStart[0] + 6) {
				helper.assertTrue(s.stage(AndyState.LEFT_ARM, now) == AndyState.STAGE_BONE, "frame 6 of the arm: bone");
			}
			if (now == armStart[0] + 12) {
				helper.assertTrue(s.stage(AndyState.LEFT_ARM, now) == AndyState.STAGE_FLESH, "frame 12: flesh");
			}
			if (!s.broken(AndyState.LEFT_ARM) && !s.broken(AndyState.LEFT_HAND)) {
				helper.assertTrue(now - armStart[0] <= 18 + 2 && now - handStart[0] <= 12 + 2, "whole on time: " + (now - armStart[0]));
				helper.assertTrue(p.getFoodData().getFoodLevel() == 20, "and it cost no food");
				helper.succeed();
			}
		});
	}

	/** From just a head, NBC's order: the body at once, arms and legs at the body's 7th frame, hands and feet at their limbs' 5th. */
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = 200)
	public void theBodyGrowsBackInNbcOrder(GameTestHelper helper) {
		ServerPlayer p = andy(helper);
		Andy.shatter(p);
		long[] body = { 0L };
		java.util.Set<Integer> seenStages = new java.util.LinkedHashSet<>();
		helper.onEachTick(() -> {
			Andy.tick(p);
			AndyState s = Andy.state(p);
			long now = helper.getLevel().getGameTime();
			seenStages.add(com.projecthero.mod.andy.AndyStages.stage(s, now));
			if (body[0] == 0L) {
				body[0] = s.regrowStart(AndyState.BODY);
				helper.assertTrue(body[0] == now, "the head is whole: the body starts at once");
				helper.assertTrue(s.regrowStart(AndyState.RIGHT_ARM) == 0L && s.regrowStart(AndyState.RIGHT_LEG) == 0L, "the limbs wait");
				return;
			}
			if (now == body[0] + AndyConfig.LIMB_NEEDS_BODY + 1) {
				for (int limb = AndyState.RIGHT_ARM; limb <= AndyState.LEFT_LEG; limb++) {
					helper.assertTrue(s.regrowStart(limb) == body[0] + AndyConfig.LIMB_NEEDS_BODY, "limb " + limb + " starts at the body's 7th frame: "
							+ (s.regrowStart(limb) - body[0]));
				}
				helper.assertTrue(s.regrowStart(AndyState.RIGHT_HAND) == 0L && s.regrowStart(AndyState.RIGHT_FOOT) == 0L, "hands and feet wait");
			}
			if (now == body[0] + AndyConfig.LIMB_NEEDS_BODY + AndyConfig.HAND_NEEDS_ARM + 1) {
				helper.assertTrue(s.regrowStart(AndyState.LEFT_HAND) == body[0] + 12 && s.regrowStart(AndyState.LEFT_FOOT) == body[0] + 12,
						"hands and feet at their limbs' 5th frame");
			}
			if (!s.anyBroken()) {
				helper.assertTrue(now - body[0] <= 27 + 2, "whole from a head in about 1.4 s (the legs, 7 + 20 frames): " + (now - body[0]));
				helper.assertTrue(seenStages.contains(com.projecthero.mod.andy.AndyStages.CRAWL), "he lay on the growing torso: " + seenStages);
				helper.assertTrue(com.projecthero.mod.andy.AndyStages.stage(s, now) == com.projecthero.mod.andy.AndyStages.NORMAL, "and walks");
				helper.succeed();
			}
		});
	}

	/** Any way of losing legs gives the stage: one leg a limp, both the hand walk. */
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void theStagesFollowTheParts(GameTestHelper helper) {
		ServerPlayer p = andy(helper);
		Andy.setRegenOff(p, true);
		Andy.breakPart(p, AndyState.RIGHT_LEG, AndyState.KIND_BURST);
		helper.assertTrue(Andy.stage(p) == com.projecthero.mod.andy.AndyStages.LIMP, "one leg gone: a limp");
		Andy.breakPart(p, AndyState.LEFT_LEG, AndyState.KIND_BURST);
		helper.assertTrue(Andy.stage(p) == com.projecthero.mod.andy.AndyStages.ARM_WALK, "both legs gone: on his hands");
		helper.succeed();
	}

	/** Shift+V: a tap toggles auto-regrowth; held, it opens the Regen Menu (and the release toggles nothing). */
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void shiftVTogglesRegenAndHoldOpensTheMenu(GameTestHelper helper) {
		ServerPlayer p = andy(helper);
		var v = com.projecthero.mod.hero.AbilitySlot.SLOT_5;
		p.setShiftKeyDown(true);
		com.projecthero.mod.andy.AndyAbilityManager.handle(p, v, true);
		com.projecthero.mod.andy.AndyAbilityManager.handle(p, v, false);
		helper.assertTrue(Andy.regenOff(p), "a tap: auto-regrowth off");
		helper.assertTrue(!com.projecthero.mod.andy.AndyRegenMenu.open(p), "the menu stays shut");
		com.projecthero.mod.andy.AndyAbilityManager.handle(p, v, true);
		com.projecthero.mod.andy.AndyAbilityManager.forTestsHoldShiftV(p, com.projecthero.mod.andy.AndyAbilityManager.MENU_HOLD);
		helper.assertTrue(com.projecthero.mod.andy.AndyRegenMenu.open(p), "held: the Regen Menu opens");
		com.projecthero.mod.andy.AndyAbilityManager.handle(p, v, false);
		helper.assertTrue(Andy.regenOff(p), "the release after the hold toggles nothing");
		helper.assertTrue(com.projecthero.mod.andy.AndyRegenMenu.segId(AndyState.RIGHT_ARM)
				.equals(com.projecthero.mod.andy.AndyAbilityManager.idsOf(p, com.projecthero.mod.hero.AbilitySlot.SLOT_1)[0]), "R is the right arm's button");
		p.setShiftKeyDown(false);
		Andy.breakPart(p, AndyState.RIGHT_ARM, AndyState.KIND_BURST);
		com.projecthero.mod.andy.AndyAbilityManager.handle(p, com.projecthero.mod.hero.AbilitySlot.SLOT_1, true);
		helper.assertTrue(Andy.state(p).targetFrame(AndyState.RIGHT_ARM) == AndyConfig.MENU_TARGETS[AndyState.RIGHT_ARM][0], "R grew the right arm a stage");
		p.setShiftKeyDown(true);
		com.projecthero.mod.andy.AndyAbilityManager.handle(p, v, false);
		com.projecthero.mod.andy.AndyAbilityManager.handle(p, v, true);
		com.projecthero.mod.andy.AndyAbilityManager.handle(p, v, false);
		helper.assertTrue(!Andy.regenOff(p), "a Shift+V tap in the menu turns auto-regrowth back on");
		p.setShiftKeyDown(false);
		helper.succeed();
	}

	/** Auto-regrowth off: nothing grows on its own; the Regen Menu grows a part a stage at a time, or strips a whole one. */
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = 200)
	public void theRegenMenuGrowsAStageAtATime(GameTestHelper helper) {
		ServerPlayer p = andy(helper);
		Andy.setRegenOff(p, true);
		Andy.breakPart(p, AndyState.LEFT_ARM, AndyState.KIND_BURST);
		int[] t = { 0 };
		helper.onEachTick(() -> {
			Andy.tick(p);
			t[0]++;
			AndyState s = Andy.state(p);
			long now = helper.getLevel().getGameTime();
			switch (t[0]) {
				case 30 -> {
					helper.assertTrue(s.broken(AndyState.LEFT_ARM) && s.regrowStart(AndyState.LEFT_ARM) == 0L, "off: nothing grows on its own");
					com.projecthero.mod.andy.AndyRegenMenu.press(p, AndyState.LEFT_HAND);
					helper.assertTrue(Andy.state(p).regrowStart(AndyState.LEFT_HAND) == 0L, "the hand can't grow before the arm");
					com.projecthero.mod.andy.AndyRegenMenu.press(p, AndyState.LEFT_ARM);
				}
				case 45 -> {
					helper.assertTrue(s.broken(AndyState.LEFT_ARM) && Math.abs(s.frame(AndyState.LEFT_ARM, now) - 5f) < 0.01f, "held at the end of the bone stage");
					helper.assertTrue(s.stage(AndyState.LEFT_ARM, now) == AndyState.STAGE_BONE, "bone");
					com.projecthero.mod.andy.AndyRegenMenu.press(p, AndyState.LEFT_ARM);
				}
				case 60 -> {
					helper.assertTrue(s.stage(AndyState.LEFT_ARM, now) == AndyState.STAGE_FLESH && Math.abs(s.frame(AndyState.LEFT_ARM, now) - 11f) < 0.01f,
							"then held at the end of the flesh stage: " + s.frame(AndyState.LEFT_ARM, now));
					com.projecthero.mod.andy.AndyRegenMenu.press(p, AndyState.LEFT_ARM);
				}
				case 80 -> {
					helper.assertTrue(!s.broken(AndyState.LEFT_ARM), "then whole");
					helper.assertTrue(s.broken(AndyState.LEFT_HAND), "the hand still waits for its own presses");
					com.projecthero.mod.andy.AndyRegenMenu.press(p, AndyState.LEFT_ARM); // a whole part: stripped
				}
				case 81 -> {
					helper.assertTrue(s.broken(AndyState.LEFT_ARM) && s.broken(AndyState.LEFT_HAND), "a press on a whole part strips it (and its hand)");
					com.projecthero.mod.andy.AndyRegenMenu.whole(p);
				}
				default -> {
				}
			}
			if (t[0] > 81 && !s.anyBroken()) {
				helper.assertTrue(t[0] <= 81 + 18 + 2, "Regenerate Whole Body grows everything back, auto-regrowth off or not: " + t[0]);
				helper.succeed();
			}
		});
	}

	/** User: "if the players torso gets destroyed their armour falls off ... if leg gets destroyed remove leggings, if feet ... boots". */
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void armourFallsOffTheLostParts(GameTestHelper helper) {
		ServerPlayer p = andy(helper);
		Andy.setRegenOff(p, true);
		p.setItemSlot(net.minecraft.world.entity.EquipmentSlot.CHEST, new ItemStack(net.minecraft.world.item.Items.IRON_CHESTPLATE));
		p.setItemSlot(net.minecraft.world.entity.EquipmentSlot.LEGS, new ItemStack(net.minecraft.world.item.Items.IRON_LEGGINGS));
		p.setItemSlot(net.minecraft.world.entity.EquipmentSlot.FEET, new ItemStack(net.minecraft.world.item.Items.IRON_BOOTS));
		Andy.breakPart(p, AndyState.RIGHT_FOOT, AndyState.KIND_BURST);
		helper.assertTrue(p.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.FEET).isEmpty(), "a foot gone: the boots fall off");
		helper.assertTrue(!p.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.LEGS).isEmpty(), "the leggings stay");
		Andy.breakPart(p, AndyState.LEFT_LEG, AndyState.KIND_BURST);
		helper.assertTrue(p.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.LEGS).isEmpty(), "a leg gone: the leggings fall off");
		Andy.breakPart(p, AndyState.BODY, AndyState.KIND_BURST);
		helper.assertTrue(p.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.CHEST).isEmpty(), "the torso gone: the chestplate falls off");
		java.util.List<net.minecraft.world.entity.item.ItemEntity> drops = helper.getLevel().getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,
				p.getBoundingBox().inflate(4));
		helper.assertTrue(drops.size() == 3, "all three dropped at his feet, nothing lost or doubled: " + drops.size());
		// put back on with the torso still gone: refused, back into the inventory
		p.getInventory().clearContent();
		p.setItemSlot(net.minecraft.world.entity.EquipmentSlot.CHEST, new ItemStack(net.minecraft.world.item.Items.IRON_CHESTPLATE));
		com.projecthero.mod.andy.AndyArmour.forTestsTick(p);
		helper.assertTrue(p.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.CHEST).isEmpty()
				&& p.getInventory().countItem(net.minecraft.world.item.Items.IRON_CHESTPLATE) == 1, "no torso to wear it on: back in the inventory");
		drops.forEach(e -> e.discard());
		Andy.restore(p);
		p.setItemSlot(net.minecraft.world.entity.EquipmentSlot.CHEST, new ItemStack(net.minecraft.world.item.Items.IRON_CHESTPLATE));
		com.projecthero.mod.andy.AndyArmour.forTestsTick(p);
		helper.assertTrue(!p.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.CHEST).isEmpty(), "regrown: it stays on");
		helper.succeed();
	}

	/** User: "if an arm gets destroyed that shoulder pad doesnt grow back and players armour value is lowered slightly". */
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void losingAnArmTearsOffItsShoulderPad(GameTestHelper helper) {
		ServerPlayer p = andy(helper);
		ItemStack chest = new ItemStack(net.minecraft.world.item.Items.IRON_CHESTPLATE);
		p.setItemSlot(net.minecraft.world.entity.EquipmentSlot.CHEST, chest);
		com.projecthero.mod.andy.AndyArmour.forTestsTick(p);
		double before = p.getAttributeValue(Attributes.ARMOR);
		Andy.breakPart(p, AndyState.RIGHT_ARM, AndyState.KIND_BURST);
		ItemStack worn = p.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.CHEST);
		helper.assertTrue(!worn.isEmpty(), "an arm: the chestplate stays on");
		helper.assertTrue(com.projecthero.mod.andy.AndyArmour.lostPads(worn) == com.projecthero.mod.andy.AndyArmour.PAD_RIGHT, "but the right pad is torn off");
		helper.assertTrue(worn.getDamageValue() > 0, "and it took damage");
		double pad = com.projecthero.mod.andy.AndyArmour.padArmour(worn);
		helper.assertTrue(pad == 1.0, "iron: a point a pad: " + pad);
		helper.assertTrue(Math.abs(p.getAttributeValue(Attributes.ARMOR) - (before - pad)) < 0.01, "his armour drops a little: " + before + " -> "
				+ p.getAttributeValue(Attributes.ARMOR));
		Andy.restore(p); // the arm is back
		com.projecthero.mod.andy.AndyArmour.forTestsTick(p);
		helper.assertTrue(com.projecthero.mod.andy.AndyArmour.lostPads(p.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.CHEST)) != 0,
				"the pad does not grow back with the arm");
		p.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.CHEST).setDamageValue(0); // repaired (anvil / Mending / the suit)
		com.projecthero.mod.andy.AndyArmour.forTestsTick(p);
		helper.assertTrue(com.projecthero.mod.andy.AndyArmour.lostPads(p.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.CHEST)) == 0, "repaired: the pad is back");
		helper.assertTrue(Math.abs(p.getAttributeValue(Attributes.ARMOR) - before) < 0.01, "and the armour");
		helper.succeed();
	}

	/** Auto-regrowth off keeps a lost part gone until it is turned back on; the state (and old saves) load. */
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = 200)
	public void regenOffKeepsPartsGoneAndIsSaved(GameTestHelper helper) {
		ServerPlayer p = andy(helper);
		Andy.toggleRegen(p);
		helper.assertTrue(Andy.regenOff(p), "Shift+V: off");
		AndyState s0 = Andy.state(p);
		net.minecraft.nbt.Tag tag = AndyState.CODEC.encodeStart(net.minecraft.nbt.NbtOps.INSTANCE, s0).getOrThrow();
		AndyState back = AndyState.CODEC.parse(net.minecraft.nbt.NbtOps.INSTANCE, tag).getOrThrow();
		helper.assertTrue(back.flag(AndyState.FLAG_REGEN_OFF), "saved and loaded");
		net.minecraft.nbt.CompoundTag old = ((net.minecraft.nbt.CompoundTag) tag).copy();
		net.minecraft.nbt.ListTag six = new net.minecraft.nbt.ListTag();
		for (int i = 0; i < 18; i++) {
			six.add(net.minecraft.nbt.LongTag.valueOf(0L));
		}
		old.put("part_times", six); // a save from before the hands and feet were segments
		old.putInt("regen_style", 2);
		AndyState fromOld = AndyState.CODEC.parse(net.minecraft.nbt.NbtOps.INSTANCE, old).getOrThrow();
		helper.assertTrue(fromOld.hasPower && fromOld.partTimes.size() == AndyState.SEGS * 3, "an old save still loads, the new segments whole");
		Andy.breakPart(p, AndyState.LEFT_ARM, AndyState.KIND_BURST);
		int[] t = { 0 };
		helper.onEachTick(() -> {
			Andy.tick(p);
			t[0]++;
			if (t[0] == 60) {
				helper.assertTrue(Andy.missing(p, AndyState.LEFT_ARM) && Andy.state(p).regrowStart(AndyState.LEFT_ARM) == 0L, "off: still gone after 3 s");
				Andy.toggleRegen(p);
			}
			if (t[0] > 60 && !Andy.state(p).anyBroken()) {
				helper.assertTrue(t[0] <= 60 + 18 + 12 + 2, "on again: back in about a second: " + (t[0] - 60));
				Andy.clearTransient(p);
				helper.succeed();
			}
		});
	}

	/** One healing rate now (the old Fast): his health comes back on its own a short while after a hit, free. */
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = 200)
	public void woundsHealOnTheirOwn(GameTestHelper helper) {
		ServerPlayer p = andy(helper);
		p.setHealth(6f);
		p.getFoodData().setFoodLevel(20);
		p.getFoodData().setSaturation(0f); // no vanilla saturation healing in the way
		edit(p, s -> s.lastHitAt = helper.getLevel().getGameTime());
		int[] t = { 0 };
		helper.onEachTick(() -> {
			t[0]++;
			Andy.tick(p);
			if (t[0] == 100) {
				helper.assertTrue(p.getHealth() >= 10f, "1.5 health a second after 1.5 s: " + p.getHealth());
				helper.assertTrue(p.getFoodData().getFoodLevel() == 20 && p.getFoodData().getExhaustionLevel() < 0.01f, "free");
				helper.succeed();
			}
		});
	}
`;
const anchor = '\t// ---------------------------------------------------------------- v0.17.3 tester fixes';
if (!t.includes(anchor)) throw new Error('anchor');
t = t.replace(anchor, tests.replace(/^\n/, '') + '\n' + anchor);
fs.writeFileSync(G, crlf ? t.replace(/\n/g, '\r\n') : t);
console.log('ok');
