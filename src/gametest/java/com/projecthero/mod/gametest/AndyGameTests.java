package com.projecthero.mod.gametest;

import com.projecthero.mod.andy.Andy;
import com.projecthero.mod.andy.AndyAbilities;
import com.projecthero.mod.andy.AndyBoneSword;
import com.projecthero.mod.andy.AndyConfig;
import com.projecthero.mod.andy.AndyParts;
import com.projecthero.mod.andy.data.AndyState;
import com.projecthero.mod.andy.entity.AndyCloneEntity;
import com.projecthero.mod.andy.entity.AndyPartEntity;
import com.projecthero.mod.andy.item.AndyItems;
import com.projecthero.mod.attachment.ModAttachments;
import com.projecthero.mod.hero.HeroTiers;
import com.projecthero.mod.hero.PowerGrants;
import com.projecthero.mod.kryptonian.Kryptonian;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * v0.17.3 Andy (Undead Unluck): where hits land, a part breaking and regrowing (for hunger), Undead (lethal damage shatters,
 * /kill still kills), the card and the one-Primary rule, the speed cycle, Victor, and each move's server side. Mock players
 * are not ticked by the server, so tests over time drive {@link Andy#tick}.
 */
public class AndyGameTests implements FabricGameTest {
	private static ServerPlayer mock(GameTestHelper helper) {
		ServerPlayer p = helper.makeMockServerPlayerInLevel();
		p.setGameMode(GameType.SURVIVAL);
		Vec3 at = helper.absoluteVec(new Vec3(2.5, 2.0, 2.5));
		p.moveTo(at.x, at.y, at.z, 0f, 0f);
		p.setYHeadRot(0f);
		p.yBodyRot = 0f;
		p.invulnerableTime = 0;
		try {
			java.lang.reflect.Field f = ServerPlayer.class.getDeclaredField("spawnInvulnerableTime");
			f.setAccessible(true);
			f.setInt(p, 0);
		} catch (ReflectiveOperationException e) {
			throw new IllegalStateException(e);
		}
		return p;
	}

	private static ServerPlayer andy(GameTestHelper helper) {
		ServerPlayer p = mock(helper);
		Andy.grant(p);
		return p;
	}

	private static void edit(ServerPlayer p, java.util.function.Consumer<AndyState> fn) {
		AndyState n = Andy.state(p).copy();
		fn.accept(n);
		p.setAttached(ModAttachments.ANDY_STATE, n);
	}

	private static Zombie zombie(GameTestHelper helper, int x, int z) {
		Zombie zo = helper.spawn(EntityType.ZOMBIE, new BlockPos(x, 2, z));
		zo.setNoAi(true);
		zo.getAttribute(Attributes.MAX_HEALTH).setBaseValue(200);
		zo.setHealth(200);
		return zo;
	}

	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void hitsLandOnThePartTheyTouch(GameTestHelper helper) {
		ServerPlayer p = andy(helper); // yaw 0: facing +z, his right is -x
		AABB b = p.getBoundingBox();
		Vec3 c = b.getCenter();
		double h = b.getYsize();
		helper.assertTrue(AndyParts.locate(p, new Vec3(c.x, b.minY + h * 0.9, c.z)) == AndyState.HEAD, "the top quarter is the head");
		helper.assertTrue(AndyParts.locate(p, new Vec3(c.x, b.minY + h * 0.55, c.z)) == AndyState.BODY, "the middle is the body");
		helper.assertTrue(AndyParts.locate(p, new Vec3(c.x - 0.28, b.minY + h * 0.55, c.z)) == AndyState.RIGHT_ARM, "his right side is the right arm");
		helper.assertTrue(AndyParts.locate(p, new Vec3(c.x + 0.28, b.minY + h * 0.55, c.z)) == AndyState.LEFT_ARM, "his left side is the left arm");
		helper.assertTrue(AndyParts.locate(p, new Vec3(c.x - 0.1, b.minY + h * 0.2, c.z)) == AndyState.RIGHT_LEG, "low right is the right leg");
		helper.assertTrue(AndyParts.locate(p, new Vec3(c.x + 0.1, b.minY + h * 0.2, c.z)) == AndyState.LEFT_LEG, "low left is the left leg");
		float[] fall = AndyParts.assign(p, p.damageSources().fall(), 6f, p.getRandom());
		helper.assertTrue(fall[AndyState.RIGHT_LEG] == 3f && fall[AndyState.LEFT_LEG] == 3f && fall[AndyState.HEAD] == 0f, "a fall lands on both legs");
		float[] blast = AndyParts.assign(p, p.damageSources().explosion(null, null), 10f, p.getRandom());
		for (float f : blast) {
			helper.assertTrue(f > 0f, "an explosion hits every part");
		}
		float[] magic = AndyParts.assign(p, p.damageSources().magic(), 10f, p.getRandom());
		for (float f : magic) {
			helper.assertTrue(f == 0f, "magic hits no part");
		}
		helper.succeed();
	}

	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void anArrowBreaksTheArmItHits(GameTestHelper helper) {
		ServerPlayer p = andy(helper);
		AABB b = p.getBoundingBox();
		net.minecraft.world.entity.projectile.Arrow arrow = new net.minecraft.world.entity.projectile.Arrow(helper.getLevel(), b.getCenter().x - 0.27,
				b.minY + b.getYsize() * 0.55, b.getCenter().z, new ItemStack(net.minecraft.world.item.Items.ARROW), null);
		boolean hurt = p.hurt(p.damageSources().arrow(arrow, null), 9f);
		helper.assertTrue(Andy.missing(p, AndyState.RIGHT_ARM), "9 damage on the right arm (8 HP) breaks it: hurt=" + hurt + " hp=" + Andy.state(p).partHp);
		helper.assertTrue(!Andy.missing(p, AndyState.LEFT_ARM) && !Andy.missing(p, AndyState.BODY), "nothing else breaks");
		helper.assertTrue(Andy.state(p).kind(AndyState.RIGHT_ARM) == AndyState.KIND_BURST, "it bursts into pixels");
		helper.succeed();
	}

	// v0.17.3: the three-layer regrowth takes 170 ticks at Normal -- with the delay that is past 200
	/** Breaks {@code parts} now; they may start regrowing as soon as their own wait is up (the tests then drive the ticks). */
	private static void breakAll(ServerPlayer p, int... parts) {
		for (int part : parts) {
			Andy.breakPart(p, part, AndyState.KIND_BURST);
		}
	}

	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void lethalDamageShattersButKillStillKills(GameTestHelper helper) {
		ServerPlayer p = andy(helper);
		p.hurt(p.damageSources().magic(), 1000f);
		helper.assertTrue(p.isAlive() && p.getHealth() == p.getMaxHealth(), "Undead: alive, health refilled");
		AndyState s = Andy.state(p);
		helper.assertTrue(!s.broken(AndyState.HEAD), "the head stays");
		for (int i = AndyState.BODY; i < AndyState.PARTS; i++) {
			helper.assertTrue(s.broken(i) && s.kind(i) == AndyState.KIND_SHATTER, "part " + i + " shattered");
		}
		helper.assertTrue(Andy.reforming(p), "the grace after the shatter");
		float hp = p.getHealth();
		p.invulnerableTime = 0;
		p.hurt(p.damageSources().magic(), 5f);
		helper.assertTrue(p.getHealth() == hp, "nothing hurts while re-forming");
		p.hurt(p.damageSources().genericKill(), Float.MAX_VALUE);
		helper.assertTrue(!p.isAlive(), "/kill still kills");
		helper.succeed();
	}

	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void theCardGrantsThePowerAndTakesThePrimarySlot(GameTestHelper helper) {
		ServerPlayer p = mock(helper);
		Kryptonian.grant(p);
		ItemStack card = new ItemStack(AndyItems.UNDEAD_CARD);
		p.setItemInHand(InteractionHand.MAIN_HAND, card);
		card.use(helper.getLevel(), p, InteractionHand.MAIN_HAND);
		helper.assertTrue(Andy.hasPower(p), "the card grants the power");
		helper.assertTrue(p.getItemInHand(InteractionHand.MAIN_HAND).isEmpty(), "the card is used up");
		helper.assertTrue(!Kryptonian.hasPower(p), "the old Primary is replaced");
		helper.assertTrue(HeroTiers.holdsHero(p, Andy.KEY), "listed as the held hero");
		helper.assertTrue(PowerGrants.NOT_FROM_SERUMS.contains(Andy.KEY), "never from a serum");
		helper.assertTrue(!Andy.grant(p), "a second grant is refused");
		Andy.revoke(p);
		helper.assertTrue(!Andy.hasPower(p), "revoked");
		helper.succeed();
	}

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
		java.util.function.DoubleSupplier penalty = () -> p.getAttribute(Attributes.ARMOR).getModifiers().stream()
				.filter(m -> m.id().getPath().equals("andy_lost_pads")).mapToDouble(m -> m.amount()).sum();
		helper.assertTrue(penalty.getAsDouble() == 0.0, "no penalty with both pads");
		Andy.breakPart(p, AndyState.RIGHT_ARM, AndyState.KIND_BURST);
		ItemStack worn = p.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.CHEST);
		helper.assertTrue(!worn.isEmpty(), "an arm: the chestplate stays on");
		helper.assertTrue(com.projecthero.mod.andy.AndyArmour.lostPads(worn) == com.projecthero.mod.andy.AndyArmour.PAD_RIGHT, "but the right pad is torn off");
		helper.assertTrue(worn.getDamageValue() > 0, "and it took damage");
		double pad = com.projecthero.mod.andy.AndyArmour.padArmour(worn);
		helper.assertTrue(pad == 1.0, "iron: a point a pad: " + pad);
		helper.assertTrue(Math.abs(penalty.getAsDouble() + pad) < 0.01, "his armour drops a little: " + penalty.getAsDouble());
		Andy.restore(p); // the arm is back
		com.projecthero.mod.andy.AndyArmour.forTestsTick(p);
		helper.assertTrue(com.projecthero.mod.andy.AndyArmour.lostPads(p.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.CHEST)) != 0,
				"the pad does not grow back with the arm");
		p.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.CHEST).setDamageValue(0); // repaired (anvil / Mending / the suit)
		com.projecthero.mod.andy.AndyArmour.forTestsTick(p);
		helper.assertTrue(com.projecthero.mod.andy.AndyArmour.lostPads(p.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.CHEST)) == 0, "repaired: the pad is back");
		helper.assertTrue(penalty.getAsDouble() == 0.0, "and the armour");
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

	// ---------------------------------------------------------------- v0.17.3 tester fixes

	/** v0.17.3 (user): Andy's Suit mends itself while worn, one point every 2 s, never past whole, and not in the inventory. */
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void andysSuitMendsItselfWhileWorn(GameTestHelper helper) {
		ServerPlayer p = andy(helper);
		ItemStack worn = new ItemStack(AndyItems.SUIT_LEGGINGS);
		worn.setDamageValue(10);
		p.setItemSlot(net.minecraft.world.entity.EquipmentSlot.LEGS, worn);
		ItemStack spare = new ItemStack(AndyItems.SUIT_BOOTS);
		spare.setDamageValue(10);
		p.getInventory().setItem(5, spare);
		helper.assertTrue(AndyItems.SUIT_CHESTPLATE.getDefense() == 6
				&& AndyItems.SUIT_LEGGINGS.getDefense() == 5 && AndyItems.SUIT_BOOTS.getDefense() == 2, "iron protection 6/5/2 (no helmet)");
		helper.assertTrue(worn.getMaxDamage() == net.minecraft.world.item.ArmorItem.Type.LEGGINGS.getDurability(15), "iron durability");
		helper.assertTrue(com.projecthero.mod.andy.item.AndySuitItem.REPAIR_EVERY == 40 && com.projecthero.mod.andy.item.AndySuitItem.REPAIR_AMOUNT == 1, "1 durability every 2 s");
		for (int i = 0; i < 20; i++) { // twenty 2 s steps
			com.projecthero.mod.andy.item.AndySuitItem.mend(worn, p);
			com.projecthero.mod.andy.item.AndySuitItem.mend(spare, p);
		}
		helper.assertTrue(worn.getDamageValue() == 0, "mended back to whole, never past: " + worn.getDamageValue());
		helper.assertTrue(spare.getDamageValue() == 10, "a spare piece in the inventory does not mend: " + spare.getDamageValue());
		helper.succeed();
	}

	/** v0.17.3 (user): the suit is not given with the power -- it is only crafted. */
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void andysSuitIsCraftedNotGiven(GameTestHelper helper) {
		ServerPlayer p = andy(helper);
		helper.assertTrue(p.getInventory().countItem(AndyItems.SUIT_CHESTPLATE) == 0 && p.getInventory().countItem(AndyItems.SUIT_LEGGINGS) == 0
				&& p.getInventory().countItem(AndyItems.SUIT_BOOTS) == 0, "gaining the power gives no suit");
		var recipes = helper.getLevel().getRecipeManager();
		for (String id : new String[] { "andy_suit_chestplate", "andy_suit_leggings", "andy_suit_boots" }) {
			helper.assertTrue(recipes.byKey(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("projecthero", id)).isPresent(), "craftable: " + id);
		}
		helper.succeed();
	}

	/** Tester: "Alternation between left and right hands instead of just one when throwing arms or hands". */
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void shotsAlternateHands(GameTestHelper helper) {
		ServerPlayer p = andy(helper);
		long now = helper.getLevel().getGameTime();
		AndyAbilities.partsBullet(p);
		AndyState s = Andy.state(p);
		boolean firstLeft = s.eaten(AndyState.LEFT_ARM, now) > 0f;
		helper.assertTrue(firstLeft != (s.eaten(AndyState.RIGHT_ARM, now) > 0f), "one hand fired first");
		AndyAbilities.partsBullet(p);
		s = Andy.state(p);
		helper.assertTrue(s.eaten(AndyState.LEFT_ARM, now) > 0f && s.eaten(AndyState.RIGHT_ARM, now) > 0f, "the second shot came from the other hand");
		AndyAbilities.partsBullet(p);
		s = Andy.state(p);
		int arm = firstLeft ? AndyState.LEFT_ARM : AndyState.RIGHT_ARM;
		helper.assertTrue(Math.abs(s.eaten(arm, now) - 2f * AndyConfig.PARTS_EAT) < 1.0e-3f, "and the third from the first again: " + s.eaten(arm, now));
		// with one arm gone, the other fires every time
		Andy.breakPart(p, AndyState.LEFT_ARM, AndyState.KIND_BURST);
		helper.assertTrue(Andy.shootingArm(p) == AndyState.RIGHT_ARM, "one arm left: always that one");
		helper.succeed();
	}

	/** Tester: "Make deadline do damage less consistently" -- rolls vary and some miss, about 4 on average. */
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void deadLineRollsVary(GameTestHelper helper) {
		net.minecraft.util.RandomSource r = net.minecraft.util.RandomSource.create(42L);
		float sum = 0f, min = 99f, max = 0f;
		int misses = 0, n = 4000;
		for (int i = 0; i < n; i++) {
			float d = AndyAbilities.deadLineRoll(r);
			sum += d;
			if (d <= 0f) {
				misses++;
			} else {
				min = Math.min(min, d);
				max = Math.max(max, d);
			}
		}
		float avg = sum / n;
		helper.assertTrue(avg > 3.6f && avg < 4.4f, "about 4 a hit on average: " + avg);
		helper.assertTrue(misses > n / 10 && misses < n / 5, "some miss: " + misses);
		helper.assertTrue(min < 3f && max > 6.5f, "hits range widely: " + min + ".." + max);
		helper.succeed();
	}

	/** Tester: "A way people can naturally pick up the head": right-click the thrown head and it goes back on. */
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void theThrownHeadCanBePickedUp(GameTestHelper helper) {
		ServerPlayer p = andy(helper);
		AndyAbilities.headBullet(p);
		helper.assertTrue(Andy.missing(p, AndyState.HEAD), "the head is thrown");
		AndyPartEntity head = helper.getLevel().getEntitiesOfClass(AndyPartEntity.class, new AABB(p.blockPosition()).inflate(8),
				e -> e.ownerIs(p) && e.bodyPart() == AndyState.HEAD).stream().findFirst().orElse(null);
		helper.assertTrue(head != null, "the head entity is out");
		helper.assertTrue(head.isPickable(), "and can be clicked");
		ServerPlayer friend = mock(helper);
		head.interact(friend, InteractionHand.MAIN_HAND);
		helper.assertTrue(!Andy.missing(p, AndyState.HEAD), "a friend right-clicking it puts it straight back on");
		helper.assertTrue(head.isRemoved(), "and the thrown one is gone");
		helper.succeed();
	}

	/** Tester: "A way people can naturally pick up the head": an Andy who is only a head can be picked up and carried. */
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void aHeadOnlyAndyCanBeCarried(GameTestHelper helper) {
		ServerPlayer p = andy(helper);
		ServerPlayer friend = mock(helper);
		helper.assertTrue(com.projecthero.mod.andy.AndyCarry.tryPickUp(friend, InteractionHand.MAIN_HAND, p) == net.minecraft.world.InteractionResult.PASS,
				"a whole Andy can not be picked up");
		for (int i = AndyState.BODY; i < AndyState.PARTS; i++) {
			Andy.breakPart(p, i, AndyState.KIND_BURST);
		}
		com.projecthero.mod.andy.AndyCarry.tryPickUp(friend, InteractionHand.MAIN_HAND, p);
		helper.assertTrue(p.getVehicle() == friend, "only a head: the friend picks him up");
		Vec3 seat = com.projecthero.mod.andy.AndyCarry.seat(friend);
		helper.assertTrue(seat.y < 1.2 && seat.horizontalDistance() > 0.3, "carried in front of the chest, not on the head: " + seat);
		Andy.restore(p);
		Andy.tick(p);
		helper.assertTrue(p.getVehicle() == null, "with his body back he climbs down");
		helper.succeed();
	}

	/** Tester: the clones -- the bones self has its own skeleton texture (the blank white one is gone). */
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void theBoneCloneHasASkeletonTexture(GameTestHelper helper) {
		helper.assertTrue(AndyGameTests.class.getResource("/assets/projecthero/textures/entity/andy/bone_clone.png") != null, "bone_clone.png ships");
		helper.succeed();
	}

	// ---------------------------------------------------------------- R: finger shots eat the arm, the stretch punch

	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void fingerShotNumbers(GameTestHelper helper) {
		helper.assertTrue(AndyConfig.PARTS_BULLET_COOLDOWN == 0, "Parts Bullet: no cooldown");
		helper.assertTrue(com.projecthero.mod.andy.AndyAbilityManager.maxCooldown(AndyAbilities.PARTS_BULLET) == 0, "and the HUD shows none");
		helper.assertTrue(AndyConfig.CRIMSON_DAMAGE == 10f && AndyConfig.CRIMSON_COOLDOWN == 15, "Crimson Bullet: 10 damage, 0.75 s");
		helper.assertTrue(AndyConfig.PUNCH_DAMAGE == 14f && AndyConfig.PUNCH_COOLDOWN == 15, "Parts Bullet: Arm: 14 damage (tester: 20 is a bit much), 0.75 s");
		helper.assertTrue(AndyConfig.HEAD_BULLET_DAMAGE > AndyConfig.PUNCH_DAMAGE, "the head hits harder than the arm");
		helper.assertTrue(Math.abs(AndyConfig.CRIMSON_EAT - 4f * AndyConfig.PARTS_EAT) < 1.0e-5f, "a Crimson Bullet takes four times the bite");
		helper.assertTrue(AndyConfig.EATEN_REGROW_WAIT == 40, "regrowth waits 2 s without a shot");
		helper.succeed();
	}

	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = 200)
	public void fingerShotsEatTheArmAndItWaitsToRegrow(GameTestHelper helper) {
		ServerPlayer p = andy(helper);
		p.setXRot(-85f);
		int arm = Andy.mainArm(p);
		AndyAbilities.partsBullet(p);
		float one = Andy.state(p).eaten(arm, helper.getLevel().getGameTime());
		helper.assertTrue(Math.abs(one - AndyConfig.PARTS_EAT) < 0.002f, "one shot takes a twelfth: " + one);
		edit(p, n -> { n.abilityReadyAt.clear(); n.setFlag(AndyState.FLAG_NEXT_OFF, false); });
		AndyAbilities.partsBullet(p);
		edit(p, n -> { n.abilityReadyAt.clear(); n.setFlag(AndyState.FLAG_NEXT_OFF, false); });
		AndyAbilities.crimsonBullet(p);
		float six = Andy.state(p).eaten(arm, helper.getLevel().getGameTime());
		helper.assertTrue(Math.abs(six - 6f / 12f) < 0.003f, "two Parts and a Crimson: half the arm: " + six);
		long shot = helper.getLevel().getGameTime();
		helper.onEachTick(() -> Andy.tick(p));
		helper.runAfterDelay(AndyConfig.EATEN_REGROW_WAIT - 4, () -> helper.assertTrue(
				Math.abs(Andy.state(p).eaten(arm, helper.getLevel().getGameTime()) - six) < 0.001f, "nothing grows back before 2 s"));
		helper.runAfterDelay(AndyConfig.EATEN_REGROW_WAIT + 2, () -> helper.assertTrue(Andy.state(p).eatenRegrow(arm, helper.getLevel().getGameTime()) > 0f,
				"it starts growing back after 2 s without a shot (" + (helper.getLevel().getGameTime() - shot) + " ticks)"));
		helper.runAfterDelay(AndyConfig.EATEN_REGROW_WAIT + 4, () -> {
			// shooting again stops it and the bite carries on from what is still gone
			edit(p, n -> { n.abilityReadyAt.clear(); n.setFlag(AndyState.FLAG_NEXT_OFF, false); });
			AndyAbilities.partsBullet(p);
			AndyState s = Andy.state(p);
			helper.assertTrue(s.eatenRegrow(arm, helper.getLevel().getGameTime()) == 0f, "a new shot restarts the wait");
			// eat the rest: the arm is used up and gone, and the other arm shoots
			for (int i = 0; i < 8 && !Andy.missing(p, arm); i++) {
				edit(p, n -> { n.abilityReadyAt.clear(); n.setFlag(AndyState.FLAG_NEXT_OFF, false); });
				AndyAbilities.crimsonBullet(p);
			}
			helper.assertTrue(Andy.missing(p, arm) && Andy.state(p).kind(arm) == AndyState.KIND_SPENT, "a used-up arm is gone");
			helper.assertTrue(Andy.shootingArm(p) == Andy.otherArm(arm), "the other arm shoots now");
			helper.succeed();
		});
	}

	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void noArmsNoShot(GameTestHelper helper) {
		ServerPlayer p = andy(helper);
		Andy.breakPart(p, AndyState.RIGHT_ARM, AndyState.KIND_BURST);
		Andy.breakPart(p, AndyState.LEFT_ARM, AndyState.KIND_BURST);
		AndyAbilities.partsBullet(p);
		AndyAbilities.crimsonBullet(p);
		AABB box = new AABB(p.blockPosition()).inflate(4);
		helper.assertTrue(helper.getLevel().getEntitiesOfClass(AndyPartEntity.class, box, e -> true).isEmpty(), "nothing fires with no arms");
		helper.assertTrue(!AndyAbilities.startPunch(p), "and no punch winds up");
		helper.succeed();
	}

	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = 100)
	public void holdRThrowsTheArmAndUsesTheOtherWhenTheMainIsGone(GameTestHelper helper) {
		ServerPlayer p = andy(helper);
		p.setXRot(-85f);
		int main = Andy.mainArm(p);
		Andy.breakPart(p, main, AndyState.KIND_BURST);
		com.projecthero.mod.hero.AbilitySlot r = com.projecthero.mod.hero.AbilitySlot.SLOT_1;
		com.projecthero.mod.andy.AndyAbilityManager.handle(p, r, true);
		helper.onEachTick(() -> Andy.tick(p));
		helper.runAfterDelay(AndyConfig.PUNCH_HOLD_TICKS + 3, () -> helper.assertTrue(Andy.state(p).flag(AndyState.FLAG_PUNCH), "held: the arm draws back"));
		helper.runAfterDelay(AndyConfig.PUNCH_MIN_HOLD + 4, () -> {
			com.projecthero.mod.andy.AndyAbilityManager.handle(p, r, false);
			int other = Andy.otherArm(main);
			helper.assertTrue(Andy.missing(p, other) && Andy.state(p).kind(other) == AndyState.KIND_LAUNCHED, "the other arm was thrown");
			AABB box = new AABB(p.blockPosition()).inflate(6);
			helper.assertTrue(helper.getLevel().getEntitiesOfClass(AndyPartEntity.class, box, e -> e.style() == AndyPartEntity.STYLE_PUNCH && e.bodyPart() == other)
					.size() == 1, "it flies as the punch");
			helper.assertTrue(Andy.cooldownRemaining(p, AndyAbilities.PUNCH) > 0, "1 s cooldown");
			helper.succeed();
		});
	}

	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = 60)
	public void aTapOfRIsStillAPartsBullet(GameTestHelper helper) {
		ServerPlayer p = andy(helper);
		p.setXRot(-85f);
		com.projecthero.mod.hero.AbilitySlot r = com.projecthero.mod.hero.AbilitySlot.SLOT_1;
		com.projecthero.mod.andy.AndyAbilityManager.handle(p, r, true);
		helper.onEachTick(() -> Andy.tick(p));
		helper.runAfterDelay(2, () -> {
			com.projecthero.mod.andy.AndyAbilityManager.handle(p, r, false);
			AABB box = new AABB(p.blockPosition()).inflate(6);
			helper.assertTrue(helper.getLevel().getEntitiesOfClass(AndyPartEntity.class, box, e -> e.style() == AndyPartEntity.STYLE_FINGER).size() == 1,
					"a tap fires a finger");
			helper.assertTrue(!Andy.state(p).flag(AndyState.FLAG_PUNCH), "and winds nothing up");
			helper.succeed();
		});
	}

	// ---------------------------------------------------------------- flight, the boost, the head-only regrowth

	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void flightSpeeds(GameTestHelper helper) {
		helper.assertTrue(Math.abs(AndyConfig.PROPULSION_SPEED * 20 - 25) < 1.0e-6, "25 b/s");
		helper.assertTrue(Math.abs(AndyConfig.BOOST_SPEED * 20 - 40) < 1.0e-6, "40 b/s boosting");
		helper.assertTrue(Math.abs(AndyConfig.HEAD_FLIGHT_SPEED * 20 - 32) < 1.0e-6, "32 b/s as a head");
		ServerPlayer p = andy(helper);
		helper.assertTrue(com.projecthero.mod.andy.AndyFlight.start(p) && com.projecthero.mod.andy.AndyFlight.isFlying(p), "takes off");
		AndyState s = Andy.state(p);
		long now = helper.getLevel().getGameTime();
		helper.assertTrue(com.projecthero.mod.andy.AndyFlight.speed(s, now) == AndyConfig.PROPULSION_SPEED, "flying at 25");
		com.projecthero.mod.andy.AndyFlight.setBoost(p, true);
		helper.assertTrue(com.projecthero.mod.andy.AndyFlight.speed(Andy.state(p), now) == AndyConfig.BOOST_SPEED, "boosting at 40");
		for (int i = AndyState.BODY; i < AndyState.PARTS; i++) {
			int part = i;
			edit(p, n -> n.setTimes(part, now, 0L, 0L));
		}
		helper.assertTrue(com.projecthero.mod.andy.AndyFlight.speed(Andy.state(p), now) == AndyConfig.HEAD_FLIGHT_SPEED, "a head flies at 32, boost or not");
		helper.assertTrue(!Andy.state(p).headOnly(now) || Andy.stage(p) == com.projecthero.mod.andy.AndyStages.ROLL, "and is a rolling head");
		com.projecthero.mod.andy.AndyFlight.stop(p);
		helper.assertTrue(!com.projecthero.mod.andy.AndyFlight.isFlying(p) && !Andy.state(p).flag(AndyState.FLAG_BOOST), "lands");
		helper.succeed();
	}

	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = 400)
	public void theBoostBurnsLegsThenArmsThenBodyDownToTheHead(GameTestHelper helper) {
		ServerPlayer p = andy(helper);
		p.setXRot(-85f);
		float health = p.getHealth();
		com.projecthero.mod.andy.AndyFlight.start(p);
		com.projecthero.mod.andy.AndyFlight.setBoost(p, true);
		int legs = AndyConfig.BURN_TICKS[0], arms = legs + AndyConfig.BURN_TICKS[1], body = arms + AndyConfig.BURN_TICKS[2];
		helper.assertTrue(body == 300, "15 s from whole to a head");
		int[] ticks = { 0 };
		helper.onEachTick(() -> {
			edit(p, n -> n.holdStart = helper.getLevel().getGameTime()); // no time limit in the test
			Andy.tick(p);
			ticks[0]++;
			AndyState s = Andy.state(p);
			if (ticks[0] == legs - 2) {
				helper.assertTrue(!s.broken(AndyState.RIGHT_LEG) && !s.broken(AndyState.LEFT_LEG), "the legs are still burning");
			}
			if (ticks[0] == legs + 2) {
				helper.assertTrue(s.broken(AndyState.RIGHT_LEG) && s.broken(AndyState.LEFT_LEG) && s.kind(AndyState.LEFT_LEG) == AndyState.KIND_BURNT,
						"the legs went first");
				helper.assertTrue(!s.broken(AndyState.RIGHT_ARM) && !s.broken(AndyState.BODY), "nothing else yet");
			}
			if (ticks[0] == arms + 2) {
				helper.assertTrue(s.broken(AndyState.RIGHT_ARM) && s.broken(AndyState.LEFT_ARM) && !s.broken(AndyState.BODY), "then the arms");
			}
			if (ticks[0] == body + 2) {
				helper.assertTrue(s.broken(AndyState.BODY) && !s.broken(AndyState.HEAD), "then the body: just the head");
				helper.assertTrue(s.regrowStart(AndyState.BODY) == 0L, "nothing regrows mid-flight");
				helper.assertTrue(p.isAlive() && p.getHealth() >= health, "burning never hurts him");
				com.projecthero.mod.andy.AndyFlight.stop(p);
			}
			if (ticks[0] == body + 4) {
				helper.assertTrue(Andy.state(p).regrowStart(AndyState.BODY) > 0L, "landed: the head-only regrowth is planned");
				helper.succeed();
			}
		});
	}

	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void victorIsDisabled(GameTestHelper helper) {
		// v0.17.3 (user: "remove the Victor ability for now"): no key pulls the card, the kit never swaps, a stale save clears
		ServerPlayer p = andy(helper);
		helper.assertTrue(!AndyConfig.VICTOR_ENABLED, "Victor is off");
		double base = p.getAttributeValue(Attributes.ATTACK_DAMAGE);
		AndyAbilities.pullCard(p);
		helper.assertTrue(!Andy.victor(p) && Andy.state(p).victorUntil == 0L, "pulling the card does nothing");
		com.projecthero.mod.hero.AbilitySlot v = com.projecthero.mod.hero.AbilitySlot.SLOT_5;
		helper.assertTrue(AndyAbilities.REFORM.equals(com.projecthero.mod.andy.AndyAbilityManager.idsOf(p, v)[0])
				&& AndyAbilities.REGEN.equals(com.projecthero.mod.andy.AndyAbilityManager.idsOf(p, v)[1]), "V is Reform, Shift+V the speed");
		edit(p, n -> n.victorUntil = helper.getLevel().getGameTime() + 400);
		helper.assertTrue(!Andy.victor(p), "an old save with the card out is not Victor");
		helper.assertTrue(AndyAbilities.PARTS_BULLET.equals(com.projecthero.mod.andy.AndyAbilityManager.idsOf(p, com.projecthero.mod.hero.AbilitySlot.SLOT_1)[0])
				&& AndyAbilities.CRESCENT.equals(com.projecthero.mod.andy.AndyAbilityManager.idsOf(p, com.projecthero.mod.hero.AbilitySlot.SLOT_2)[0])
				&& AndyAbilities.DEAD_LINE.equals(com.projecthero.mod.andy.AndyAbilityManager.idsOf(p, com.projecthero.mod.hero.AbilitySlot.SLOT_6)[0]),
				"the kit never swaps to Victor's");
		Andy.tick(p);
		helper.assertTrue(Andy.state(p).victorUntil == 0L, "and the tick clears it");
		helper.assertTrue(Math.abs(p.getAttributeValue(Attributes.ATTACK_DAMAGE) - base) < 0.01, "still Bloody Arts");
		helper.succeed();
	}

	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void slashesHitWhatIsInFront(GameTestHelper helper) {
		ServerPlayer p = andy(helper);
		Zombie z = zombie(helper, 2, 4);
		AndyAbilities.crescent(p);
		float after = z.getHealth();
		helper.assertTrue(after < 200f, "Crimson Crescent Moon hits");
		helper.succeed();
	}

	// ---------------------------------------------------------------- R aim, Shift+V: the Bone Sword and its kit (v0.17.3)

	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = 60)
	public void thePartsBulletArmFliesAtTheCrosshair(GameTestHelper helper) {
		ServerPlayer p = andy(helper);
		p.setXRot(0f);
		Zombie z = zombie(helper, 2, 9); // straight ahead, the eye line through its head
		AndyAbilities.startPunch(p);
		edit(p, n -> n.setEx(AndyState.X_PUNCH_START, helper.getLevel().getGameTime() - 20));
		AndyAbilities.throwPunch(p);
		AABB box = new AABB(p.blockPosition()).inflate(6);
		java.util.List<AndyPartEntity> arms = helper.getLevel().getEntitiesOfClass(AndyPartEntity.class, box, e -> e.style() == AndyPartEntity.STYLE_PUNCH);
		helper.assertTrue(arms.size() == 1, "the arm flies");
		AndyPartEntity a = arms.get(0);
		Vec3 centre = a.position().add(0, a.getBbHeight() * 0.5, 0);
		Vec3 aimAt = new Vec3(z.getX(), p.getEyeY(), z.getBoundingBox().minZ);
		double cos = a.getDeltaMovement().normalize().dot(aimAt.subtract(centre).normalize());
		helper.assertTrue(cos > Math.cos(Math.toRadians(2.5)), "it flies at the crosshair: " + Math.toDegrees(Math.acos(Math.min(1, cos))) + " degrees off");
		helper.assertTrue(!a.isLeftBehind(), "and is not drawn as a part left standing");
		// the finger shots aim the same way
		edit(p, n -> n.abilityReadyAt.clear());
		Andy.restore(p);
		AndyAbilities.partsBullet(p);
		AndyPartEntity f = helper.getLevel().getEntitiesOfClass(AndyPartEntity.class, box, e -> e.style() == AndyPartEntity.STYLE_FINGER).get(0);
		Vec3 fc = f.position().add(0, f.getBbHeight() * 0.5, 0);
		double fcos = f.getDeltaMovement().normalize().dot(aimAt.subtract(fc).normalize());
		helper.assertTrue(fcos > Math.cos(Math.toRadians(2.5)), "a Parts Bullet too: " + Math.toDegrees(Math.acos(Math.min(1, fcos))));
		AndyAbilities.partsBullet(p);
		helper.assertTrue(helper.getLevel().getEntitiesOfClass(AndyPartEntity.class, box, e -> e.style() == AndyPartEntity.STYLE_FINGER).size() == 2,
				"and fires again at once (no cooldown)");
		helper.succeed();
	}

	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = 80)
	public void theThrownArmSurvivesItsRegrowthStarting(GameTestHelper helper) {
		// v0.17.3 bug (user: "the part bullet arm ability doesnt seem to shoot properly anymore"): since the NBC regrowth a
		// launched arm starts growing back the very next tick, and the start of the regrowth burst the thrown arm -- it was gone
		// a tick after leaving the shoulder, nothing flew and nothing was hit. Held R through the key flow, his tick running,
		// thrown straight up (open sky): it must keep flying while the arm regrows, and burst once the arm is whole.
		ServerPlayer p = andy(helper);
		p.setXRot(-85f);
		com.projecthero.mod.hero.AbilitySlot r = com.projecthero.mod.hero.AbilitySlot.SLOT_1;
		com.projecthero.mod.andy.AndyAbilityManager.handle(p, r, true);
		helper.onEachTick(() -> Andy.tick(p));
		int[] arm = { -1 };
		java.util.function.Supplier<java.util.List<AndyPartEntity>> thrown = () -> helper.getLevel().getEntitiesOfClass(AndyPartEntity.class,
				new AABB(p.blockPosition()).inflate(64), e -> e.style() == AndyPartEntity.STYLE_PUNCH && e.bodyPart() == arm[0]);
		int release = AndyConfig.PUNCH_MIN_HOLD + 2;
		helper.runAfterDelay(release, () -> {
			com.projecthero.mod.andy.AndyAbilityManager.handle(p, r, false);
			arm[0] = Andy.mainArm(p);
			helper.assertTrue(Andy.missing(p, arm[0]) && thrown.get().size() == 1, "the arm was thrown");
		});
		helper.runAfterDelay(release + 5, () -> {
			helper.assertTrue(Andy.state(p).regrowStart(arm[0]) > 0L, "it is already growing back (NBC: at once)");
			java.util.List<AndyPartEntity> a = thrown.get();
			helper.assertTrue(a.size() == 1, "and the thrown arm is still flying");
			helper.assertTrue(a.get(0).position().distanceTo(p.getEyePosition()) > 2.5, "away from the shoulder: " + a.get(0).position().distanceTo(p.getEyePosition()));
		});
		helper.runAfterDelay(release + AndyConfig.FRAMES[AndyState.RIGHT_ARM] + 4, () -> {
			helper.assertTrue(!Andy.missing(p, arm[0]), "the arm has grown back");
			helper.assertTrue(thrown.get().isEmpty(), "and the thrown one burst once it was whole");
			helper.succeed();
		});
	}

	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = 300)
	public void holdVRipsAnArmIntoTheBoneSword(GameTestHelper helper) {
		// v0.17.3 (user: "make the bone sword hold V for 2 seconds"): the hold is the struggle; at its end the arm tears off
		ServerPlayer p = andy(helper);
		int main = Andy.mainArm(p), off = Andy.otherArm(main);
		double baseReach = p.getAttributeValue(Attributes.ENTITY_INTERACTION_RANGE);
		com.projecthero.mod.hero.AbilitySlot v = com.projecthero.mod.hero.AbilitySlot.SLOT_5;
		com.projecthero.mod.andy.AndyAbilityManager.handle(p, v, true);
		holdV(helper, p, AndyConfig.BONE_SWORD_TAP + 3);
		helper.assertTrue(Andy.state(p).animId == AndyState.ANIM_SWORD_STRUGGLE && !AndyBoneSword.held(p) && !Andy.missing(p, off),
				"past a tap he struggles with the arm; it is still on");
		holdV(helper, p, AndyConfig.BONE_SWORD_HOLD + 1);
		com.projecthero.mod.andy.AndyAbilityManager.handle(p, v, false);
		helper.assertTrue(AndyBoneSword.held(p), "held 2 s, V draws the sword");
		helper.assertTrue(Andy.missing(p, off) && !Andy.missing(p, main) && Andy.state(p).boneSwordArm() == off, "the off arm was torn off");
		helper.assertTrue(Math.abs(p.getAttributeValue(Attributes.ATTACK_DAMAGE) - AndyConfig.BONE_SWORD_DAMAGE) < 0.01,
				"a sword's damage: " + p.getAttributeValue(Attributes.ATTACK_DAMAGE));
		helper.assertTrue(Math.abs(p.getAttributeValue(Attributes.ENTITY_INTERACTION_RANGE) - baseReach - AndyConfig.BONE_SWORD_REACH_BONUS) < 0.01, "longer reach");
		helper.assertTrue(Math.abs(p.getAttributeValue(Attributes.ATTACK_SPEED) - AndyConfig.BONE_SWORD_ATTACK_SPEED) < 0.01, "a sword's speed");
		helper.assertTrue(AndyBoneSword.SLASH.equals(com.projecthero.mod.andy.AndyAbilityManager.idsOf(p, com.projecthero.mod.hero.AbilitySlot.SLOT_1)[0])
				&& AndyBoneSword.DASH.equals(com.projecthero.mod.andy.AndyAbilityManager.idsOf(p, com.projecthero.mod.hero.AbilitySlot.SLOT_3)[0])
				&& AndyBoneSword.PARRY.equals(com.projecthero.mod.andy.AndyAbilityManager.idsOf(p, com.projecthero.mod.hero.AbilitySlot.SLOT_6)[0]),
				"the keys are the sword kit");
		helper.onEachTick(() -> Andy.tick(p));
		helper.runAfterDelay(30, () -> {
			AndyState s = Andy.state(p);
			helper.assertTrue(s.broken(off) && s.regrowStart(off) == 0L, "the torn-off arm does not grow back while the sword is out");
			com.projecthero.mod.andy.AndyAbilityManager.handle(p, com.projecthero.mod.hero.AbilitySlot.SLOT_5, true);
			com.projecthero.mod.andy.AndyAbilityManager.handle(p, com.projecthero.mod.hero.AbilitySlot.SLOT_5, false);
			helper.assertTrue(!AndyBoneSword.held(p), "V again puts it away");
			helper.assertTrue(Andy.cooldownRemaining(p, AndyBoneSword.BONE_SWORD) > 0, "a short cooldown");
			helper.assertTrue(AndyAbilities.PARTS_BULLET.equals(com.projecthero.mod.andy.AndyAbilityManager.idsOf(p, com.projecthero.mod.hero.AbilitySlot.SLOT_1)[0]),
					"the keys are his own again");
			helper.assertTrue(p.getAttribute(Attributes.ENTITY_INTERACTION_RANGE).getModifiers().stream().noneMatch(m -> m.id().getPath().contains("bone_sword")),
					"the reach goes back");
		});
		helper.runAfterDelay(35, () -> {
			helper.assertTrue(Andy.state(p).regrowStart(off) > 0L, "then the arm regrows");
			helper.succeed();
		});
	}

	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void noSpareArmNoBoneSword(GameTestHelper helper) {
		ServerPlayer p = andy(helper);
		Andy.breakPart(p, Andy.otherArm(Andy.mainArm(p)), AndyState.KIND_BURST);
		helper.assertTrue(!AndyBoneSword.draw(p) && !AndyBoneSword.held(p), "one arm: nothing to tear off and hold");
		Andy.restore(p);
		Andy.breakPart(p, Andy.mainArm(p), AndyState.KIND_BURST);
		helper.assertTrue(!AndyBoneSword.draw(p), "no hand to hold it with");
		helper.succeed();
	}

	/** V held down for {@code ticks} game ticks' worth of the hold (the hold is timed off the game time: it is wound back). */
	private static void holdV(GameTestHelper helper, ServerPlayer p, int ticks) {
		com.projecthero.mod.andy.AndyAbilityManager.forTestsHoldV(p, ticks);
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
		helper.assertTrue(Andy.cooldownRemaining(p, AndyAbilities.REFORM) == 0, "and it was no tap: no Reform");
		helper.succeed();
	}

	// ---------------------------------------------------------------- tap V: Reform (replaced Tear Off)

	private static void feed(ServerPlayer p, int food, float saturation) {
		p.getFoodData().setFoodLevel(food);
		p.getFoodData().setSaturation(saturation);
	}

	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void tapVReformFinishesEveryPartAndCleanses(GameTestHelper helper) {
		ServerPlayer p = andy(helper);
		feed(p, 20, 5f);
		long now = helper.getLevel().getGameTime();
		int off = Andy.otherArm(Andy.mainArm(p));
		Andy.breakPart(p, AndyState.LEFT_LEG, AndyState.KIND_BURST); // gone, not started
		Andy.breakPart(p, off, AndyState.KIND_BURST);
		edit(p, n -> n.setTimes(off, Math.max(1L, now - 30), now - 5, now + 100)); // regrowing
		p.setHealth(10f);
		p.igniteForSeconds(8f);
		p.addEffect(new net.minecraft.world.effect.MobEffectInstance(MobEffects.POISON, 200, 1));
		p.addEffect(new net.minecraft.world.effect.MobEffectInstance(MobEffects.WITHER, 200, 1));
		p.addEffect(new net.minecraft.world.effect.MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 200, 0));
		com.projecthero.mod.hero.AbilitySlot v = com.projecthero.mod.hero.AbilitySlot.SLOT_5;
		com.projecthero.mod.andy.AndyAbilityManager.handle(p, v, true);
		com.projecthero.mod.andy.AndyAbilityManager.handle(p, v, false);
		AndyState s = Andy.state(p);
		for (int i = 0; i < AndyState.PARTS; i++) {
			helper.assertTrue(!s.broken(i) && Math.abs(s.hp(i) - AndyConfig.PART_HP[i]) < 0.01f, "part " + i + " is whole");
		}
		helper.assertTrue(s.animId == AndyState.ANIM_REFORM, "the snap-together animation");
		helper.assertTrue(s.ex(AndyState.X_REFORMED) == ((1L << AndyState.LEFT_LEG) | (1L << off)), "the flash on the two parts that closed");
		helper.assertTrue(Math.abs(p.getHealth() - (10f + AndyConfig.REFORM_HEAL)) < 0.01f, "healed 6: " + p.getHealth());
		helper.assertTrue(!p.isOnFire() && !p.hasEffect(MobEffects.POISON) && !p.hasEffect(MobEffects.WITHER), "fire, poison and wither gone");
		helper.assertTrue(p.hasEffect(MobEffects.DAMAGE_RESISTANCE), "a good effect stays");
		helper.assertTrue(Andy.cooldownRemaining(p, AndyAbilities.REFORM) == AndyConfig.REFORM_COOLDOWN, "20s cooldown");
		helper.assertTrue(com.projecthero.mod.andy.AndyAbilityManager.maxCooldown(AndyAbilities.REFORM) == AndyConfig.REFORM_COOLDOWN, "the HUD's cooldown");
		Andy.tick(p);
		helper.assertTrue(!Andy.state(p).broken(off) && !Andy.state(p).broken(AndyState.LEFT_LEG), "and the tick leaves it whole");
		helper.succeed();
	}

	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void reformHasACooldownWorksWithRegenOffAndNotWithTheSword(GameTestHelper helper) {
		ServerPlayer p = andy(helper);
		feed(p, 20, 20f);
		Andy.setRegenOff(p, true);
		Andy.breakPart(p, AndyState.LEFT_LEG, AndyState.KIND_BURST);
		helper.assertTrue(Andy.reform(p) && !Andy.missing(p, AndyState.LEFT_LEG), "regeneration Off: Reform still works");
		Andy.breakPart(p, AndyState.LEFT_LEG, AndyState.KIND_BURST);
		helper.assertTrue(!Andy.reform(p) && Andy.missing(p, AndyState.LEFT_LEG), "cooling down");
		edit(p, n -> n.abilityReadyAt.put(AndyAbilities.REFORM, helper.getLevel().getGameTime()));
		helper.assertTrue(Andy.reform(p) && !Andy.missing(p, AndyState.LEFT_LEG), "ready again");
		// the sword out: V puts it away and Reform does nothing
		Andy.setRegenOff(p, false);
		edit(p, n -> n.abilityReadyAt.clear());
		helper.assertTrue(AndyBoneSword.draw(p), "the sword is drawn");
		int off = Andy.otherArm(Andy.mainArm(p));
		helper.assertTrue(!Andy.reform(p) && Andy.missing(p, off), "Reform does not grow the sword arm back");
		com.projecthero.mod.hero.AbilitySlot v = com.projecthero.mod.hero.AbilitySlot.SLOT_5;
		com.projecthero.mod.andy.AndyAbilityManager.handle(p, v, true);
		com.projecthero.mod.andy.AndyAbilityManager.handle(p, v, false);
		helper.assertTrue(!AndyBoneSword.held(p) && Andy.cooldownRemaining(p, AndyAbilities.REFORM) == 0, "V puts the sword away, no Reform with it");
		helper.succeed();
	}

	private static ServerPlayer swordsman(GameTestHelper helper) {
		ServerPlayer p = andy(helper);
		helper.assertTrue(AndyBoneSword.draw(p), "the sword is drawn");
		return p;
	}

	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void theBoneSwordHitsLikeASwordAndSweeps(GameTestHelper helper) {
		ServerPlayer p = swordsman(helper);
		Zombie z = zombie(helper, 2, 4);
		Zombie side = zombie(helper, 3, 4);
		z.getAttribute(Attributes.ARMOR).setBaseValue(0);
		try {
			java.lang.reflect.Field f = net.minecraft.world.entity.LivingEntity.class.getDeclaredField("attackStrengthTicker");
			f.setAccessible(true);
			f.setInt(p, 100);
		} catch (ReflectiveOperationException e) {
			throw new IllegalStateException(e);
		}
		helper.assertTrue(p.getAttackStrengthScale(0.5f) > 0.99f, "a full swing");
		AndyBoneSword.melee(p, z, p.getAttackStrengthScale(0.5f)); // what the attack hook does before the hit
		p.attack(z);
		float dealt = 200f - z.getHealth();
		helper.assertTrue(dealt >= AndyConfig.BONE_SWORD_DAMAGE - 0.5f, "a sword hit: " + dealt);
		helper.assertTrue(side.getHealth() < 200f, "the sweep catches the one beside it");
		helper.succeed();
	}

	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = 100)
	public void theSwordKitMovesHit(GameTestHelper helper) {
		ServerPlayer p = swordsman(helper);
		Zombie z = zombie(helper, 2, 4);
		// R: the combo, the third a sweep
		AndyBoneSword.slash(p);
		float h1 = z.getHealth();
		helper.assertTrue(h1 < 200f, "Bone Slash hits");
		edit(p, n -> n.abilityReadyAt.remove(AndyBoneSword.SLASH));
		z.invulnerableTime = 0;
		AndyBoneSword.slash(p);
		edit(p, n -> n.abilityReadyAt.remove(AndyBoneSword.SLASH));
		z.invulnerableTime = 0;
		AndyBoneSword.slash(p);
		helper.assertTrue(Andy.state(p).animId == AndyState.ANIM_SWORD_SLASH3, "the third cut is the sweep");
		helper.assertTrue(Andy.cooldownRemaining(p, AndyBoneSword.SLASH) > AndyBoneSword.SLASH_COOLDOWN, "with the longer wait after it");
		// Shift+R and X: lunges that cut what they pass
		float h2 = z.getHealth();
		z.invulnerableTime = 0;
		AndyBoneSword.thrust(p);
		helper.assertTrue(z.getHealth() < h2, "Marrow Thrust hits");
		// Z: shards; Shift+G: the guard blocks; C: a parried hit is countered
		AndyBoneSword.splinters(p);
		AABB box = new AABB(p.blockPosition()).inflate(5);
		helper.assertTrue(helper.getLevel().getEntitiesOfClass(AndyPartEntity.class, box, AndyPartEntity::isSplinter).size() == AndyBoneSword.SPLINTERS,
				"seven bone shards");
		AndyBoneSword.guard(p);
		helper.assertTrue(AndyBoneSword.guarding(p), "the ribs are up");
		float hp = p.getHealth();
		p.invulnerableTime = 0;
		p.hurt(helper.getLevel().damageSources().mobAttack(z), 6f);
		helper.assertTrue(p.getHealth() == hp, "the guard blocks a hit");
		AndyBoneSword.forTestsEndGuard(p);
		AndyBoneSword.parry(p);
		float zh = z.getHealth();
		z.invulnerableTime = 0;
		p.invulnerableTime = 0;
		p.hurt(helper.getLevel().damageSources().mobAttack(z), 6f);
		helper.assertTrue(p.getHealth() == hp, "a parried hit does nothing");
		helper.assertTrue(z.getHealth() < zh, "and is countered");
		// G: the wave runs out and hits
		Zombie far = zombie(helper, 2, 9);
		AndyBoneSword.edgeWave(p);
		helper.onEachTick(() -> Andy.tick(p));
		helper.runAfterDelay(14, () -> {
			helper.assertTrue(far.getHealth() < 200f, "Blood Edge Wave reaches 7 blocks out");
			// X: the dash
			z.invulnerableTime = 0;
			float hz = z.getHealth();
			AndyBoneSword.dash(p);
			helper.assertTrue(z.getHealth() < hz || Andy.cooldownRemaining(p, AndyBoneSword.DASH) > 0, "Blood-jet Dash runs");
			// Shift+Z: the sword is thrown, which ends it
			p.setXRot(-85f);
			AndyBoneSword.impale(p);
			helper.assertTrue(!AndyBoneSword.held(p), "Impale Throw ends the sword");
			helper.assertTrue(helper.getLevel().getEntitiesOfClass(AndyPartEntity.class, new AABB(p.blockPosition()).inflate(5), AndyPartEntity::isSword).size() == 1,
					"and the sword flies");
			helper.getLevel().getEntitiesOfClass(AndyPartEntity.class, new AABB(p.blockPosition()).inflate(8), e -> true).forEach(e -> e.discard());
			helper.succeed();
		});
	}

	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void theThrownSwordPins(GameTestHelper helper) {
		ServerPlayer p = swordsman(helper);
		Zombie z = zombie(helper, 2, 4);
		AndyBoneSword.pin(p, z);
		helper.assertTrue(z.hasEffect(MobEffects.MOVEMENT_SLOWDOWN), "pinned");
		helper.succeed();
	}

	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void fingerAndCrimsonBulletsFly(GameTestHelper helper) {
		ServerPlayer p = andy(helper);
		AndyAbilities.partsBullet(p);
		AABB box = new AABB(p.blockPosition()).inflate(4);
		helper.assertTrue(helper.getLevel().getEntitiesOfClass(AndyPartEntity.class, box, e -> e.style() == AndyPartEntity.STYLE_FINGER).size() == 1,
				"a finger flies");
		AndyAbilities.crimsonBullet(p);
		helper.assertTrue(helper.getLevel().getEntitiesOfClass(AndyPartEntity.class, box, e -> e.style() == AndyPartEntity.STYLE_CRIMSON).size() == 1,
				"a crimson bullet flies");
		helper.succeed();
	}

	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void vortexBulletLaunchesTheArmAndHeadBulletTheHead(GameTestHelper helper) {
		ServerPlayer p = andy(helper);
		int arm = Andy.mainArm(p);
		AndyAbilities.vortexBullet(p);
		helper.assertTrue(Andy.missing(p, arm) && Andy.state(p).kind(arm) == AndyState.KIND_LAUNCHED, "the arm is gone, as the projectile");
		AABB box = new AABB(p.blockPosition()).inflate(4);
		helper.assertTrue(helper.getLevel().getEntitiesOfClass(AndyPartEntity.class, box, e -> e.bodyPart() == arm).size() == 1, "the drill flies");
		edit(p, n -> n.abilityReadyAt.clear());
		AndyAbilities.partsBullet(p);
		// v0.17.3: a one-arm move falls back to the other arm -- the finger comes from the hand he still has
		helper.assertTrue(helper.getLevel().getEntitiesOfClass(AndyPartEntity.class, box, e -> e.isFinger()).size() == 1, "the other hand shoots instead");
		helper.assertTrue(Andy.shootingArm(p) == (arm == AndyState.RIGHT_ARM ? AndyState.LEFT_ARM : AndyState.RIGHT_ARM), "from the other arm");
		AndyAbilities.headBullet(p);
		helper.assertTrue(Andy.missing(p, AndyState.HEAD), "the head is gone");
		Andy.reconcile(p);
		helper.assertTrue(p.hasEffect(MobEffects.BLINDNESS), "and he is blind");
		helper.assertTrue(com.projecthero.mod.andy.AndyMod.handGone(p, InteractionHand.MAIN_HAND), "the missing hand can't be used");
		helper.succeed();
	}

	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void quardeadSplitsIntoThreeClones(GameTestHelper helper) {
		ServerPlayer p = andy(helper);
		AndyAbilities.quardead(p);
		AABB box = new AABB(p.blockPosition()).inflate(4);
		var clones = helper.getLevel().getEntitiesOfClass(AndyCloneEntity.class, box, e -> true);
		helper.assertTrue(clones.size() == 3, "three copies: " + clones.size());
		helper.assertTrue(clones.stream().map(AndyCloneEntity::tint).distinct().count() == 3, "skin, blood and bones");
		helper.assertTrue(!com.projecthero.mod.combat.HeroTargets.canHarm(p, clones.get(0)), "his own moves pass through them");
		clones.forEach(e -> e.discard());
		helper.succeed();
	}

	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void deadLineIsABeamThatHurts(GameTestHelper helper) {
		ServerPlayer p = andy(helper);
		Zombie z = zombie(helper, 2, 5);
		p.setXRot(8f);
		AndyAbilities.startDeadLine(p);
		helper.assertTrue(Andy.state(p).flag(AndyState.FLAG_DEAD_LINE), "the jet is on");
		helper.onEachTick(() -> Andy.tick(p));
		helper.runAfterDelay(20, () -> {
			helper.assertTrue(z.getHealth() < 200f, "Dead Line hurts what it touches: " + z.getHealth());
			AndyAbilities.stopDeadLine(p);
			helper.assertTrue(!Andy.state(p).flag(AndyState.FLAG_DEAD_LINE), "and stops on release");
			helper.succeed();
		});
	}

	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void propulsionAndWavesRun(GameTestHelper helper) {
		ServerPlayer p = andy(helper);
		Zombie z = zombie(helper, 2, 5);
		AndyAbilities.startPropulsion(p);
		helper.assertTrue(Andy.state(p).flag(AndyState.FLAG_PROPELLING), "flying");
		Andy.tick(p);
		helper.assertTrue(Math.abs(p.getDeltaMovement().length() - AndyConfig.PROPULSION_SPEED) < 1.0e-6, "along the look at 25 b/s");
		AndyAbilities.stopPropulsion(p);
		helper.assertTrue(!Andy.state(p).flag(AndyState.FLAG_PROPELLING), "stops on release");
		p.setDeltaMovement(Vec3.ZERO);
		p.moveTo(helper.absoluteVec(new Vec3(2.5, 2.0, 1.5)));
		AndyAbilities.greatCrescent(p);
		helper.onEachTick(() -> Andy.tick(p));
		helper.runAfterDelay(14, () -> {
			helper.assertTrue(z.getHealth() < 200f, "the blood waves reach the zombie");
			helper.succeed();
		});
	}

	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void victorVulcanAndDivisionFire(GameTestHelper helper) {
		ServerPlayer p = andy(helper);
		p.setXRot(-85f); // up, out of the walled test area
		AndyAbilities.pullCard(p);
		AndyAbilities.vulcan(p);
		AndyAbilities.division(p);
		AABB box = new AABB(p.blockPosition()).inflate(6);
		helper.assertTrue(helper.getLevel().getEntitiesOfClass(AndyPartEntity.class, box, e -> e.style() == AndyPartEntity.STYLE_DIVISION).size() == 3,
				"three Division fingers");
		AndyAbilities.divisionClone(p, p.position());
		helper.assertTrue(!helper.getLevel().getEntitiesOfClass(AndyCloneEntity.class, box, e -> true).isEmpty(), "a finger grows into a copy");
		helper.onEachTick(() -> Andy.tick(p));
		helper.runAfterDelay(6, () -> {
			helper.assertTrue(AndyAbilities.vulcanLeft(p) > 0 && AndyAbilities.vulcanLeft(p) <= AndyConfig.VULCAN_SHOTS - 2, "Dead Vulcan keeps firing: " + AndyAbilities.vulcanLeft(p));
			AndyAbilities.deadRoad(p);
			helper.assertTrue(Andy.state(p).flag(AndyState.FLAG_DEAD_ROAD), "Dead Road fires");
			helper.getLevel().getEntitiesOfClass(AndyCloneEntity.class, box, e -> true).forEach(e -> e.discard());
			helper.succeed();
		});
	}

	// ---------------------------------------------------------------- X: Leg Launch, Head Launch, Shift + hold flight (v0.17.3)

	private static final com.projecthero.mod.hero.AbilitySlot X = com.projecthero.mod.hero.AbilitySlot.SLOT_3;

	private static java.util.List<AndyPartEntity> leftBehind(GameTestHelper helper, ServerPlayer p) {
		return helper.getLevel().getEntitiesOfClass(AndyPartEntity.class, new AABB(p.blockPosition()).inflate(8), e -> e.isLeftBehind() && e.ownerIs(p));
	}

	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void tapXLaunchesTheBodyOffTheLegs(GameTestHelper helper) {
		ServerPlayer p = andy(helper); // facing +z
		helper.onEachTick(() -> Andy.tick(p));
		com.projecthero.mod.andy.AndyAbilityManager.handle(p, X, true);
		helper.runAfterDelay(4, () -> {
			helper.assertTrue(!Andy.missing(p, AndyState.RIGHT_LEG), "nothing yet while X is still down");
			com.projecthero.mod.andy.AndyAbilityManager.handle(p, X, false);
			AndyState s = Andy.state(p);
			helper.assertTrue(s.broken(AndyState.RIGHT_LEG) && s.broken(AndyState.LEFT_LEG), "a tap breaks both legs");
			helper.assertTrue(s.kind(AndyState.RIGHT_LEG) == AndyState.KIND_LAUNCHED, "left behind, not burst");
			helper.assertTrue(!s.broken(AndyState.HEAD) && !s.broken(AndyState.BODY), "the rest of him goes");
			Vec3 v = p.getDeltaMovement();
			helper.assertTrue(v.z > 1.5 && Math.abs(v.y) < 0.05, "dashed straight along the aim (level look: no arc): " + v);
			helper.assertTrue(com.projecthero.mod.andy.AndyLaunch.flying(p), "the launch runs");
			helper.assertTrue(com.projecthero.mod.andy.AndyLaunch.fallImmune(p), "no fall damage from it");
			helper.assertTrue(leftBehind(helper, p).size() == 2, "the two legs stay where he stood: " + leftBehind(helper, p).size());
			helper.assertTrue(Andy.cooldownRemaining(p, com.projecthero.mod.andy.AndyLaunch.LEG_LAUNCH) > 0, "on cooldown");
			leftBehind(helper, p).forEach(e -> e.discard());
			helper.succeed();
		});
	}

	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void holdXLaunchesTheHeadAndLeavesTheBody(GameTestHelper helper) {
		ServerPlayer p = andy(helper);
		helper.onEachTick(() -> Andy.tick(p));
		com.projecthero.mod.andy.AndyAbilityManager.handle(p, X, true);
		helper.runAfterDelay(AndyConfig.LAUNCH_CHARGE_SHOW + 3, () -> {
			helper.assertTrue(Andy.state(p).animId == AndyState.ANIM_HEAD_CHARGE, "the wind-up shows while X is held");
			helper.assertTrue(!Andy.state(p).anyBroken(), "nothing breaks during the wind-up");
		});
		helper.runAfterDelay(AndyConfig.HEAD_LAUNCH_HOLD + 3, () -> {
			// still held: it fired on its own at the full hold
			AndyState s = Andy.state(p);
			helper.assertTrue(!s.broken(AndyState.HEAD), "the player is the head");
			for (int i = AndyState.BODY; i < AndyState.PARTS; i++) {
				helper.assertTrue(s.broken(i), "part " + i + " is left behind");
			}
			helper.assertTrue(leftBehind(helper, p).size() == 5, "the body, arms and legs stay where he stood: " + leftBehind(helper, p).size());
			helper.assertTrue(Andy.cooldownRemaining(p, com.projecthero.mod.andy.AndyLaunch.HEAD_LAUNCH) > 0, "on cooldown");
			com.projecthero.mod.andy.AndyAbilityManager.handle(p, X, false);
			helper.assertTrue(Andy.cooldownRemaining(p, com.projecthero.mod.andy.AndyLaunch.LEG_LAUNCH) == 0, "letting go after it does not also Leg Launch");
			leftBehind(helper, p).forEach(e -> e.discard());
			helper.succeed();
		});
	}

	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void headLaunchGivesTheHeadFlight(GameTestHelper helper) {
		ServerPlayer p = andy(helper);
		com.projecthero.mod.andy.AndyLaunch.headLaunch(p);
		Vec3 v = p.getDeltaMovement();
		helper.assertTrue(v.z > 2.0 && Math.abs(v.y) < 0.05, "the head dashes straight along the aim (level look: no arc): " + v);
		helper.assertTrue(com.projecthero.mod.andy.AndyLaunch.flying(p), "the launch runs");
		leftBehind(helper, p).forEach(e -> e.discard());
		helper.succeed();
	}

	/** v0.17.3 (user: "make the player keep the momentum"): at the end of its range the dash lets go at full speed, still fall-safe. */
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void aLaunchKeepsItsMomentumPastItsRange(GameTestHelper helper) {
		ServerPlayer p = andy(helper); // facing +z
		com.projecthero.mod.andy.AndyLaunch.legLaunch(p);
		helper.assertTrue(com.projecthero.mod.andy.AndyLaunch.flying(p), "the launch runs");
		// past its range, up in the open air
		p.teleportTo(p.getX(), p.getY() + 6, p.getZ() + AndyConfig.LEG_LAUNCH_RANGE + 2);
		p.setOnGround(false);
		com.projecthero.mod.andy.AndyLaunch.tick(p);
		Vec3 v = p.getDeltaMovement();
		helper.assertTrue(!com.projecthero.mod.andy.AndyLaunch.flying(p), "the scripted dash has ended");
		helper.assertTrue(v.z > 1.5, "but he flies on at full speed, not stopped in mid-air: " + v);
		helper.assertTrue(com.projecthero.mod.andy.AndyLaunch.fallImmune(p), "and no fall damage while momentum carries him");
		leftBehind(helper, p).forEach(e -> e.discard());
		helper.succeed();
	}

	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void shiftHoldXFliesAndGlideIsGone(GameTestHelper helper) {
		ServerPlayer p = andy(helper);
		helper.assertTrue(java.util.Arrays.equals(com.projecthero.mod.andy.AndyAbilityManager.idsOf(p, X),
				new String[] { com.projecthero.mod.andy.AndyLaunch.LEG_LAUNCH, AndyAbilities.PROPULSION }), "X: Leg Launch, Shift: the flight");
		boolean glide = true;
		try {
			AndyAbilities.class.getField("GLIDE");
		} catch (NoSuchFieldException e) {
			glide = false;
		}
		helper.assertTrue(!glide, "Repair Glide is gone");
		helper.assertTrue(com.projecthero.mod.andy.AndyAbilityManager.maxCooldown(com.projecthero.mod.andy.AndyLaunch.LEG_LAUNCH) == AndyConfig.LEG_LAUNCH_COOLDOWN
				&& AndyConfig.LEG_LAUNCH_COOLDOWN == 40, "Leg Launch 2s cooldown");
		helper.assertTrue(com.projecthero.mod.andy.AndyAbilityManager.maxCooldown(com.projecthero.mod.andy.AndyLaunch.HEAD_LAUNCH) == AndyConfig.HEAD_LAUNCH_COOLDOWN
				&& AndyConfig.HEAD_LAUNCH_COOLDOWN == 160, "Head Launch 8s cooldown");
		helper.assertTrue(AndyConfig.HEAD_LAUNCH_HOLD == 20, "the hold is a second");
		p.setShiftKeyDown(true);
		helper.onEachTick(() -> Andy.tick(p));
		com.projecthero.mod.andy.AndyAbilityManager.handle(p, X, true);
		helper.runAfterDelay(1, () -> helper.assertTrue(!Andy.state(p).flag(AndyState.FLAG_PROPELLING), "a Shift tap does not fly yet"));
		helper.runAfterDelay(AndyConfig.FLIGHT_HOLD_TICKS + 3, () -> {
			helper.assertTrue(Andy.state(p).flag(AndyState.FLAG_PROPELLING), "Shift + hold X flies");
			for (int i = 0; i < AndyState.PARTS; i++) {
				helper.assertTrue(!Andy.state(p).broken(i), "and launches nothing");
			}
			helper.assertTrue(Andy.state(p).broken(AndyState.RIGHT_HAND) && Andy.state(p).broken(AndyState.LEFT_FOOT), "the hands and feet burst off");
			com.projecthero.mod.andy.AndyAbilityManager.handle(p, X, false);
			helper.assertTrue(!Andy.state(p).flag(AndyState.FLAG_PROPELLING), "letting go stops it");
			helper.assertTrue(Andy.cooldownRemaining(p, com.projecthero.mod.andy.AndyLaunch.LEG_LAUNCH) == 0, "no Leg Launch on release");
			helper.succeed();
		});
	}
}
