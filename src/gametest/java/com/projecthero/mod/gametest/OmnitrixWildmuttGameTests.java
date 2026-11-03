package com.projecthero.mod.gametest;

import java.util.List;

import com.mojang.serialization.JsonOps;
import com.projecthero.mod.omnitrix.Omnitrix;
import com.projecthero.mod.omnitrix.alien.AlienMove;
import com.projecthero.mod.omnitrix.alien.Aliens;
import com.projecthero.mod.omnitrix.alien.wildmutt.WildmuttKit;
import com.projecthero.mod.omnitrix.data.OmnitrixState;
import com.projecthero.mod.omnitrix.entity.AlienProjectileEntity;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * v0.17.1 Wildmutt (Wildvine's replacement on the dial): his body (34 health, x1.35 speed, the 0.9 x 1.55 quadruped hitbox with
 * the eyes at 1.25 -- rework 2's slim, leggy body -- the paws that use no tools or weapons, and the player's box back after turning back), every move's damage or effect and its cooldown, the Scent Sense
 * range (24, 48 sniffing, 6 underwater, 0 deafened) and the blind immunities, Wall Climb on / off, and that a save made as
 * Wildvine loads as Wildmutt. Mock players are not ticked by the server: tests over time drive {@link Omnitrix#tick} themselves
 * (and {@code refreshDimensions}, which {@code Player.tick} does through {@code OmnitrixAlienBodyMixin}).
 */
public class OmnitrixWildmuttGameTests implements FabricGameTest {
	private static ServerPlayer mock(GameTestHelper helper, double x, double y, double z) {
		ServerPlayer p = helper.makeMockServerPlayerInLevel();
		p.setGameMode(GameType.SURVIVAL);
		Vec3 at = helper.absoluteVec(new Vec3(x, y, z));
		p.moveTo(at.x, at.y, at.z, 0.0f, 0.0f);
		p.setYHeadRot(0f);
		try {
			java.lang.reflect.Field f = ServerPlayer.class.getDeclaredField("spawnInvulnerableTime");
			f.setAccessible(true);
			f.setInt(p, 0);
		} catch (ReflectiveOperationException e) {
			throw new IllegalStateException(e);
		}
		return p;
	}

	private static ServerPlayer wildmutt(GameTestHelper helper) {
		ServerPlayer p = mock(helper, 3.5, 2, 1.5);
		helper.assertTrue(Omnitrix.grant(p), "the Omnitrix goes on");
		Omnitrix.select(p, WildmuttKit.ID);
		helper.assertTrue(Omnitrix.transform(p), "transformed into Wildmutt");
		p.setOnGround(true);
		p.refreshDimensions();
		return p;
	}

	private static WildmuttKit kit() {
		return (WildmuttKit) Aliens.kit(WildmuttKit.ID);
	}

	private static void pump(GameTestHelper helper, ServerPlayer p) {
		helper.onEachTick(() -> {
			p.tickCount++;
			Omnitrix.tick(p);
		});
	}

	private static Zombie zombie(GameTestHelper helper, double x, double y, double z, float health) {
		helper.getLevel().getServer().setDifficulty(Difficulty.HARD, true);
		Zombie m = EntityType.ZOMBIE.create(helper.getLevel());
		Vec3 at = helper.absoluteVec(new Vec3(x, y, z));
		m.moveTo(at.x, at.y, at.z, 180f, 0f);
		m.setNoAi(true);
		m.setPersistenceRequired();
		m.getAttribute(Attributes.MAX_HEALTH).setBaseValue(health);
		m.setHealth(health);
		m.getAttribute(Attributes.ARMOR).setBaseValue(0);
		m.getAttribute(Attributes.KNOCKBACK_RESISTANCE).setBaseValue(1.0);
		m.setItemSlot(net.minecraft.world.entity.EquipmentSlot.HEAD, new ItemStack(Items.CARVED_PUMPKIN));
		helper.getLevel().addFreshEntity(m);
		return m;
	}

	private static void floor(GameTestHelper helper) {
		for (int x = 0; x <= 7; x++) {
			for (int z = 0; z <= 7; z++) {
				helper.getLevel().setBlock(helper.absolutePos(new BlockPos(x, 1, z)), Blocks.STONE.defaultBlockState(), 3);
			}
		}
	}

	private static void near(GameTestHelper helper, double actual, double expected, String what) {
		helper.assertTrue(Math.abs(actual - expected) < 0.05, what + " (expected " + expected + ", got " + actual + ")");
	}

	/** Puts {@code z} {@code dist} blocks straight ahead of {@code p} (who faces +Z). */
	private static void ahead(Zombie z, ServerPlayer p, double dist) {
		z.moveTo(p.getX(), p.getY(), p.getZ() + dist, 180f, 0f);
		z.setDeltaMovement(Vec3.ZERO);
		z.invulnerableTime = 0;
	}

	// ================================================================ the body

	@GameTest(template = EMPTY_STRUCTURE)
	public void wildmuttsBodyIsALowWideQuadrupedAndComesBack(GameTestHelper helper) {
		floor(helper);
		ServerPlayer p = wildmutt(helper);
		near(helper, p.getMaxHealth(), WildmuttKit.HEALTH, "34 health");
		near(helper, p.getAttributeValue(Attributes.MOVEMENT_SPEED), p.getAttributeBaseValue(Attributes.MOVEMENT_SPEED) * WildmuttKit.SPEED, "x1.35 speed");
		near(helper, p.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE), 0.3, "knockback resistance 0.3");
		near(helper, p.getAttributeValue(Attributes.STEP_HEIGHT), 1.0, "steps up a full block");
		near(helper, p.getAttributeValue(Attributes.ATTACK_DAMAGE), 1.0 + WildmuttKit.ATTACK, "claws +6");
		near(helper, p.getBbWidth(), WildmuttKit.BODY_WIDTH, "a wide body");
		near(helper, p.getBbHeight(), WildmuttKit.BODY_HEIGHT, "a low body");
		near(helper, p.getEyeHeight(), WildmuttKit.EYE_HEIGHT, "the eyes (gills) low");
		helper.assertTrue(p.getBbWidth() > 0.6f && p.getBbHeight() < 1.8f, "wider and lower than a player");
		Omnitrix.revert(p, Omnitrix.Reason.SHIFT_V);
		helper.assertFalse(Omnitrix.state(p).transformed(), "turned back");
		p.refreshDimensions();
		near(helper, p.getBbWidth(), 0.6, "the player's width back");
		near(helper, p.getBbHeight(), 1.8, "the player's height back");
		near(helper, p.getEyeHeight(), 1.62, "the player's eyes back");
		near(helper, p.getMaxHealth(), 20.0, "20 health back");
		helper.succeed();
	}

	/**
	 * v0.17.1 rework 2 (user: "make it so that the player cant use tools"): paws -- a held pickaxe mines stone like a bare
	 * paw and harvests nothing, a held sword adds nothing to the claw hit; all back once he turns back.
	 */
	@GameTest(template = EMPTY_STRUCTURE)
	public void wildmuttsPawsUseNoToolsOrWeapons(GameTestHelper helper) {
		floor(helper);
		ServerPlayer p = wildmutt(helper);
		helper.assertTrue(com.projecthero.mod.omnitrix.alien.AlienKit.pawsOnlyNow(p), "paws only");
		net.minecraft.world.level.block.state.BlockState stone = Blocks.STONE.defaultBlockState();
		p.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, new ItemStack(Items.DIAMOND_PICKAXE));
		near(helper, p.getDestroySpeed(stone), 1.0, "a pickaxe mines like a bare paw");
		helper.assertFalse(p.hasCorrectToolForDrops(stone), "and harvests no stone");
		// v0.17.2: right-click is his own again -- food in the paw is eaten, not swallowed by a Sniff
		p.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, new ItemStack(Items.BREAD));
		helper.assertTrue(kit().onUse(p, net.minecraft.world.InteractionHand.MAIN_HAND) == net.minecraft.world.InteractionResult.PASS, "right-click passes through (he can eat)");
		helper.assertTrue(com.projecthero.mod.omnitrix.alien.AlienKit.pawsCanUse(p.getMainHandItem()), "and paws may eat bread");
		// the claw hit with a sword in the slot: his own claws only
		p.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, new ItemStack(Items.DIAMOND_SWORD));
		Zombie z = zombie(helper, 3.5, 2, 3.5, 100f);
		ahead(z, p, 1.5);
		helper.assertTrue(kit().onAttack(p, z), "the kit makes the hit itself");
		helper.assertTrue(z.getHealth() > 100f - (1f + WildmuttKit.ATTACK) - 0.01f, "no sword damage on top (" + z.getHealth() + ")");
		helper.assertTrue(z.getHealth() < 100f, "but the claws hit");
		Omnitrix.revert(p, Omnitrix.Reason.SHIFT_V);
		p.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, new ItemStack(Items.DIAMOND_PICKAXE));
		helper.assertFalse(com.projecthero.mod.omnitrix.alien.AlienKit.pawsOnlyNow(p), "hands again");
		helper.assertTrue(p.getDestroySpeed(stone) > 4f, "the pickaxe works again");
		helper.assertTrue(p.hasCorrectToolForDrops(stone), "and harvests stone");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void everyMoveHasItsKeyAndNeverHOrN(GameTestHelper helper) {
		List<AlienMove> moves = kit().moves();
		// v0.17.2: 15 -- the Paw Swipe joined R (tap), the Pounce moved to Hold R
		helper.assertTrue(moves.size() == 15, "15 moves, Dig on V, Paw Swipe on R (" + moves.size() + ")");
		for (AlienMove m : moves) {
			if (m.onSlot()) {
				char k = m.slot().defaultKey();
				helper.assertTrue("RGXZCV".indexOf(Character.toUpperCase(k)) >= 0, m.id() + " sits on R / G / X / Z / C / V (" + k + ")");
			}
		}
		helper.assertTrue(kit().moveOn(com.projecthero.mod.hero.AbilitySlot.SLOT_3, false, true) != null, "a hold move on X");
		// v0.17.2 revamp: Ricochet Hunt took Z; Ground Slam is the Coiled Leap's landing now
		AlienMove z = kit().moveOn(com.projecthero.mod.hero.AbilitySlot.SLOT_4, false, false);
		helper.assertTrue(z != null && WildmuttKit.HUNT.equals(z.id()), "Ricochet Hunt on Z (" + (z == null ? null : z.id()) + ")");
		helper.assertTrue(kit().move("ground_slam") == null, "no Ground Slam key any more");
		helper.succeed();
	}

	// ================================================================ v0.17.2: R = Paw Swipe combo, Hold R = Pounce

	/**
	 * v0.17.2 (user: "Wildmutt should have a 3 hit paw swipe attack by pressing R"): each R press hits everything in the short
	 * frontal arc -- right paw (11), left paw (11), then the two-paw rake (24, knockback, slowed) a few ticks after the 3rd press;
	 * the chain resets after the rake and when the 1.5 s window runs out.
	 */
	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 200)
	public void rTapIsAThreeHitPawSwipeCombo(GameTestHelper helper) {
		floor(helper);
		ServerPlayer p = wildmutt(helper);
		WildmuttKit k = kit();
		p.moveTo(p.getX(), p.getY(), p.getZ() + 1.5, 0f, 0f); // room on the floor behind him (a creature off it takes fall damage)
		com.projecthero.mod.hero.AbilitySlot r = com.projecthero.mod.hero.AbilitySlot.SLOT_1;
		Zombie a = zombie(helper, 3.5, 2, 3.0, 200f);
		Zombie b = zombie(helper, 3.5, 2, 3.0, 200f);
		Zombie behind = zombie(helper, 3.5, 2, 0.0, 200f);
		ahead(a, p, 1.6);
		b.moveTo(p.getX() + 1.0, p.getY(), p.getZ() + 1.8, 180f, 0f); // off to one side, still in the arc
		behind.moveTo(p.getX(), p.getY(), p.getZ() - 2.0, 0f, 0f);
		pump(helper, p);
		// 1: the right paw
		k.press(p, r, false);
		helper.assertTrue(Omnitrix.state(p).animId == WildmuttKit.ANIM_PAW_R, "the 1st press is the right paw swipe");
		near(helper, 200f - a.getHealth(), WildmuttKit.SWIPE_DAMAGE, "the right paw hits for 11");
		near(helper, 200f - b.getHealth(), WildmuttKit.SWIPE_DAMAGE, "and the one beside it in the arc");
		near(helper, behind.getHealth(), 200f, "nothing behind him");
		helper.assertTrue(k.comboStep(p) == 1, "the combo is on blow 2");
		helper.assertFalse(k.flag(p, WildmuttKit.F_POUNCE), "a tap never pounces");
		helper.runAfterDelay(WildmuttKit.COMBO_GAP + 1, () -> {
			// 2: the left paw
			ahead(a, p, 1.6);
			k.press(p, r, false);
			helper.assertTrue(Omnitrix.state(p).animId == WildmuttKit.ANIM_PAW_L, "the 2nd press is the left paw swipe");
			near(helper, 200f - a.getHealth(), 2 * WildmuttKit.SWIPE_DAMAGE, "the left paw hits for 11 more");
			helper.assertTrue(k.comboStep(p) == 2, "the combo is on the finisher");
			helper.runAfterDelay(WildmuttKit.COMBO_GAP + 1, () -> {
				// 3: the two-paw rake -- it lands on the clip's strike, a few ticks later
				ahead(a, p, 1.6);
				k.press(p, r, false);
				helper.assertTrue(Omnitrix.state(p).animId == WildmuttKit.ANIM_RAKE, "the 3rd press is the two-paw rake");
				helper.assertTrue(k.comboStep(p) == 0, "the chain resets after the finisher");
				helper.assertTrue(k.cooldownLeft(p, WildmuttKit.PAW_SWIPE) > WildmuttKit.COMBO_GAP, "a short wait after the rake");
				helper.runAfterDelay(WildmuttKit.RAKE_DELAY + 2, () -> {
					near(helper, 200f - a.getHealth(), 2 * WildmuttKit.SWIPE_DAMAGE + WildmuttKit.RAKE_DAMAGE, "the rake hits for 24");
					helper.assertTrue(a.hasEffect(MobEffects.MOVEMENT_SLOWDOWN), "and slows it");
					near(helper, behind.getHealth(), 200f, "still nothing behind him");
					// the window: one swipe, then wait past 1.5 s -- the next press starts over with the right paw
					helper.runAfterDelay(WildmuttKit.COMBO_COOLDOWN, () -> {
						ahead(a, p, 1.6);
						a.setHealth(200f);
						k.press(p, r, false);
						helper.assertTrue(k.comboStep(p) == 1, "a new chain");
						helper.runAfterDelay(WildmuttKit.COMBO_WINDOW + 2, () -> {
							helper.assertTrue(k.comboStep(p) == 0, "the chain lapses after 1.5 s");
							ahead(a, p, 1.6);
							k.press(p, r, false);
							helper.assertTrue(Omnitrix.state(p).animId == WildmuttKit.ANIM_PAW_R, "and starts again with the right paw");
							near(helper, 200f - a.getHealth(), 2 * WildmuttKit.SWIPE_DAMAGE, "a plain swipe again (11)");
							a.discard();
							b.discard();
							behind.discard();
							helper.succeed();
						});
					});
				});
			});
		});
	}

	/** v0.17.2 (user: "make holding R be his pounce ability please"): Hold R springs the Pounce, with its old numbers. */
	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 60)
	public void holdingRPounces(GameTestHelper helper) {
		floor(helper);
		ServerPlayer p = wildmutt(helper);
		WildmuttKit k = kit();
		com.projecthero.mod.hero.AbilitySlot r = com.projecthero.mod.hero.AbilitySlot.SLOT_1;
		AlienMove tap = k.moveOn(r, false, false), hold = k.moveOn(r, false, true), shift = k.moveOn(r, true, false);
		helper.assertTrue(tap != null && WildmuttKit.PAW_SWIPE.equals(tap.id()), "R is the Paw Swipe");
		helper.assertTrue(hold != null && WildmuttKit.POUNCE.equals(hold.id()), "Hold R is the Pounce");
		helper.assertTrue(shift != null && WildmuttKit.MAUL.equals(shift.id()), "Shift+R is still the Maul");
		helper.assertTrue(k.holdable(r) && k.holdable(com.projecthero.mod.hero.AbilitySlot.SLOT_3), "R and X both hold");
		Zombie z = zombie(helper, 3.5, 2, 3.0, 200f);
		p.moveTo(p.getX(), p.getY(), p.getZ(), 0f, 5f);
		ahead(z, p, 1.3);
		k.holdStart(p, r);
		helper.assertTrue(k.flag(p, WildmuttKit.F_POUNCE) && p.getDeltaMovement().z > 0.3, "holding R lunges");
		helper.assertTrue(k.cooldownLeft(p, WildmuttKit.POUNCE) > 0, "the pounce cools down");
		p.tickCount++;
		Omnitrix.tick(p);
		near(helper, 200f - z.getHealth(), WildmuttKit.POUNCE_DAMAGE, "the bite does 26");
		helper.assertTrue(z.hasEffect(MobEffects.MOVEMENT_SLOWDOWN), "and pins it");
		k.holdEnd(p, r, 10);
		helper.assertFalse(k.flag(p, WildmuttKit.F_COIL), "letting go of R does not coil a leap");
		// a second hold while it cools down does nothing
		z.setHealth(200f);
		ahead(z, p, 1.3);
		k.holdStart(p, r);
		helper.assertFalse(k.flag(p, WildmuttKit.F_POUNCE), "no second pounce while it cools down");
		z.discard();
		helper.succeed();
	}

	// ================================================================ the moves

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 120)
	public void wildmuttsMovesHitAndCoolDown(GameTestHelper helper) {
		floor(helper);
		ServerPlayer p = wildmutt(helper);
		WildmuttKit k = kit();
		Zombie z = zombie(helper, 3.5, 2, 3.0, 200f);
		p.moveTo(p.getX(), p.getY(), p.getZ(), 0f, 5f);
		// Pounce: a lunge, then the bite on contact
		ahead(z, p, 1.3);
		helper.assertTrue(k.pounce(p) && k.flag(p, WildmuttKit.F_POUNCE), "Pounce");
		helper.assertTrue(p.getDeltaMovement().z > 0.3, "lunging at it");
		helper.assertFalse(k.pounce(p), "Pounce cools down");
		p.tickCount++;
		Omnitrix.tick(p);
		near(helper, z.getHealth(), 200f - WildmuttKit.POUNCE_DAMAGE, "the bite does 14");
		helper.assertTrue(z.hasEffect(MobEffects.MOVEMENT_SLOWDOWN), "and pins it");
		helper.assertFalse(k.flag(p, WildmuttKit.F_POUNCE), "the pounce is over");
		// the timed moves: Maul, Quill Volley, Savage Roar (v0.17.2: Ground Slam left Z -- see aCoiledLeapLandsInAGroundSlam)
		ahead(z, p, 2.0);
		helper.assertTrue(k.maul(p) && !k.maul(p), "Maul, then it cools down");
		helper.assertTrue(k.quills(p) && !k.quills(p), "Quill Volley, then it cools down");
		helper.assertTrue(k.roar(p) && !k.roar(p), "Savage Roar, then it cools down");
		float before = z.getHealth();
		pump(helper, p);
		helper.runAfterDelay(WildmuttKit.MAUL_HITS[WildmuttKit.MAUL_HITS.length - 1] + 3, () -> {
			float lost = before - z.getHealth();
			float maul = 3 * WildmuttKit.MAUL_RAKE + WildmuttKit.MAUL_SLAM;
			// everything lands on the one zombie: the maul (18) and the roar (6); quills may add
			helper.assertTrue(lost >= maul + WildmuttKit.ROAR_DAMAGE - 0.1f, "the maul and the roar both hit (" + lost + ")");
			helper.assertTrue(z.hasEffect(MobEffects.WEAKNESS), "the roar weakens");
			AABB box = p.getBoundingBox().inflate(20);
			int quills = helper.getLevel().getEntitiesOfClass(AlienProjectileEntity.class, box, e -> "wildmutt_quill".equals(e.kind())).size();
			helper.assertTrue(quills > 0 || z.getHealth() < before - maul - WildmuttKit.ROAR_DAMAGE, "the quills flew (" + quills + " still out)");
			// Quill Roll: rolls through it once
			z.setHealth(200f);
			ahead(z, p, 0.9);
			helper.assertTrue(k.roll(p) && k.flag(p, WildmuttKit.F_ROLL), "Quill Roll");
			helper.assertFalse(k.roll(p), "the roll cools down");
			helper.runAfterDelay(2, () -> {
				near(helper, z.getHealth(), 200f - WildmuttKit.ROLL_DAMAGE, "the roll hits it once for 12");
				float hp = p.getHealth();
				p.invulnerableTime = 0;
				p.hurt(helper.getLevel().damageSources().generic(), 10f);
				helper.assertTrue(hp - p.getHealth() < 10f * WildmuttKit.ROLL_FACTOR + 0.6f, "rolling, he takes 40% less (" + (hp - p.getHealth()) + ")");
				// Feral Frenzy
				double base = p.getAttributeValue(Attributes.ATTACK_DAMAGE);
				helper.assertTrue(k.frenzy(p) && k.flag(p, WildmuttKit.F_FRENZY), "Feral Frenzy");
				near(helper, p.getAttributeValue(Attributes.ATTACK_DAMAGE), base + WildmuttKit.FRENZY_CLAWS, "+6 claws");
				helper.assertFalse(k.frenzy(p), "the frenzy cools down");
				// Scent Track: the zombie in front
				helper.assertTrue(k.track(p) && k.tracked(p) == z, "Scent Track locks onto the zombie");
				helper.assertFalse(k.track(p), "the track cools down");
				// Leap
				helper.assertTrue(k.leap(p) && k.flag(p, WildmuttKit.F_LEAPING) && p.getDeltaMovement().y > 0.4, "Leap");
				helper.assertFalse(k.leap(p), "the leap cools down");
				helper.assertFalse(p.hurt(helper.getLevel().damageSources().fall(), 6f), "no fall damage from his own leap");
				// Sniff
				helper.assertTrue(k.sniff(p) && k.flag(p, WildmuttKit.F_SNIFF), "Sniff");
				helper.assertFalse(k.sniff(p), "the sniff cools down");
				z.discard();
				helper.succeed();
			});
		});
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 80)
	public void theCoiledLeapGoesFurtherTheLongerItIsHeld(GameTestHelper helper) {
		floor(helper);
		ServerPlayer p = wildmutt(helper);
		WildmuttKit k = kit();
		p.moveTo(p.getX(), p.getY(), p.getZ(), 0f, 0f);
		k.holdStart(p, com.projecthero.mod.hero.AbilitySlot.SLOT_3);
		helper.assertTrue(k.flag(p, WildmuttKit.F_COIL), "coiling");
		helper.runAfterDelay(WildmuttKit.COIL_MAX, () -> {
			k.holdEnd(p, com.projecthero.mod.hero.AbilitySlot.SLOT_3, WildmuttKit.COIL_MAX);
			helper.assertFalse(k.flag(p, WildmuttKit.F_COIL), "released");
			double full = p.getDeltaMovement().horizontalDistance();
			helper.assertTrue(full > 1.15 * 1.6, "a full coil springs ~1.8x as far (" + full + ")");
			helper.assertTrue(k.cooldownLeft(p, WildmuttKit.COILED_LEAP) > 0, "the coiled leap cools down");
			helper.succeed();
		});
	}

	// ================================================================ v0.17.2 revamp

	/** Ground Slam is the Coiled Leap's landing now: a plain leap lands softly, a coiled one slams (coil and fall scale it). */
	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 100)
	public void aCoiledLeapLandsInAGroundSlam(GameTestHelper helper) {
		floor(helper);
		ServerPlayer p = wildmutt(helper);
		WildmuttKit k = kit();
		p.moveTo(p.getX(), p.getY(), p.getZ(), 0f, 0f);
		Zombie z = zombie(helper, 3.5, 2, 3.5, 200f);
		ahead(z, p, 2.0);
		// the slam's numbers (v0.17.2 damage pass): no coil on the flat = r 3, 16 at the centre; a full coil after an 8-block fall = r 6, 32
		near(helper, 200f - z.getHealth(), 0, "untouched yet");
		k.leapSlam(p, 0f, 0);
		near(helper, 200f - z.getHealth(), 16f - 6.4f * (2f / 3f), "a bare slam: 16 falling to 9.6 at r 3 (2 blocks out)");
		helper.assertTrue(z.hasEffect(MobEffects.MOVEMENT_SLOWDOWN), "and slows");
		z.setHealth(200f);
		ahead(z, p, 2.0);
		k.leapSlam(p, 1f, 8);
		near(helper, 200f - z.getHealth(), 32f - 12.8f * (2f / 6f), "a full coil and a long fall: 32 falling to 19.2 at r 6");
		z.setHealth(200f);
		ahead(z, p, 5.0);
		k.leapSlam(p, 0f, 0);
		near(helper, 200f - z.getHealth(), 0, "5 blocks out is beyond a bare slam's 3");
		// a plain leap lands with the plain shock -- no slam
		ahead(z, p, 2.0);
		helper.assertTrue(k.leap(p), "Leap");
		pump(helper, p);
		helper.runAfterDelay(6, () -> {
			helper.assertFalse(k.flag(p, WildmuttKit.F_LEAPING), "landed");
			near(helper, 200f - z.getHealth(), 0, "a plain leap does not slam");
			// a coiled leap does
			k.holdStart(p, com.projecthero.mod.hero.AbilitySlot.SLOT_3);
			helper.runAfterDelay(WildmuttKit.COIL_MAX, () -> {
				ahead(z, p, 2.0);
				k.holdEnd(p, com.projecthero.mod.hero.AbilitySlot.SLOT_3, WildmuttKit.COIL_MAX);
				helper.assertTrue(k.flag(p, WildmuttKit.F_LEAPING), "springing");
				helper.runAfterDelay(6, () -> {
					helper.assertFalse(k.flag(p, WildmuttKit.F_LEAPING), "landed");
					float lost = 200f - z.getHealth();
					helper.assertTrue(lost > 15.5f && lost < 27f, "the landing slammed it (" + lost + ")");
					helper.succeed();
				});
			});
		});
	}

	/** Z: Ricochet Hunt -- one pounce after another through the pack, each once; a lone creature is hit twice. */
	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 100)
	public void ricochetHuntBouncesThroughThePack(GameTestHelper helper) {
		floor(helper);
		ServerPlayer p = wildmutt(helper);
		WildmuttKit k = kit();
		p.moveTo(p.getX(), p.getY(), p.getZ(), 0f, 0f);
		Zombie a = zombie(helper, 3.5, 2, 4.5, 200f);
		Zombie b = zombie(helper, 3.5, 2, 7.5, 200f);
		Zombie c = zombie(helper, 7.0, 2, 7.0, 200f);
		helper.assertTrue(k.hunt(p) && k.flag(p, WildmuttKit.F_HUNT), "the hunt is on");
		helper.assertFalse(k.hunt(p), "and cools down");
		pump(helper, p);
		helper.runAfterDelay(WildmuttKit.HUNT_HOP_TICKS * 4 + 4, () -> {
			for (Zombie m : new Zombie[] { a, b, c }) {
				near(helper, 200f - m.getHealth(), WildmuttKit.HUNT_DAMAGE, "each of the pack is hit once for 8");
			}
			helper.assertFalse(k.flag(p, WildmuttKit.F_HUNT), "the hunt is over");
			a.discard();
			b.discard();
			c.discard();
			helper.succeed();
		});
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 80)
	public void ricochetHuntHitsALoneCreatureTwice(GameTestHelper helper) {
		floor(helper);
		ServerPlayer p = wildmutt(helper);
		WildmuttKit k = kit();
		p.moveTo(p.getX(), p.getY(), p.getZ(), 0f, 0f);
		Zombie a = zombie(helper, 3.5, 2, 5.5, 200f);
		helper.assertTrue(k.hunt(p), "the hunt is on");
		pump(helper, p);
		helper.runAfterDelay(WildmuttKit.HUNT_HOP_TICKS * 3 + 4, () -> {
			near(helper, 200f - a.getHealth(), 2 * WildmuttKit.HUNT_DAMAGE, "a lone zombie is hit twice (16)");
			helper.assertFalse(k.flag(p, WildmuttKit.F_HUNT), "the hunt is over");
			a.discard();
			helper.succeed();
		});
	}

	/** Shift+G's ping, Jump on a wall, the Bite, Predator's Appetite, the motion-based sense and the Choked Nose. */
	@GameTest(template = EMPTY_STRUCTURE)
	public void theRoarPingsTheWallKicksTheBiteAndTheAppetite(GameTestHelper helper) {
		floor(helper);
		ServerPlayer p = wildmutt(helper);
		WildmuttKit k = kit();
		p.moveTo(p.getX(), p.getY(), p.getZ(), 0f, 0f);
		// the ping reaches 24 blocks, round him, not just the roar's cone
		Zombie front = zombie(helper, 3.5, 2, 3.0, 200f);
		Zombie behind = zombie(helper, 3.5, 2, 1.5, 200f);
		behind.moveTo(p.getX(), p.getY(), p.getZ() - 20, 0f, 0f);
		List<net.minecraft.world.entity.LivingEntity> pinged = k.ping(p);
		helper.assertTrue(pinged.contains(front) && pinged.contains(behind), "the ping finds both, 20 blocks behind too (" + pinged.size() + ")");
		helper.assertTrue(WildmuttKit.pingListeners(p).contains(p), "he hears his own ping");
		behind.discard();
		// the bite: every 3rd claw hit
		ahead(front, p, 1.5);
		helper.assertFalse(kit().onAttack(p, front), "1st swipe: a claw");
		helper.assertFalse(kit().onAttack(p, front), "2nd swipe: a claw");
		float hp = front.getHealth();
		helper.assertTrue(kit().onAttack(p, front), "3rd swipe: the Bite (the kit makes the hit)");
		helper.assertTrue(hp - front.getHealth() >= WildmuttKit.BITE_BONUS + (1f + WildmuttKit.ATTACK) * 0.2f - 0.01f, "the bite adds 3 (" + (hp - front.getHealth()) + ")");
		helper.assertTrue(front.hasEffect(MobEffects.MOVEMENT_SLOWDOWN), "and slows");
		helper.assertFalse(kit().onAttack(p, front), "then it counts again from 1");
		// Predator's Appetite: a kill feeds him, 5 s apart
		p.getFoodData().setFoodLevel(10);
		p.setHealth(20f);
		front.setHealth(1f);
		front.invulnerableTime = 0;
		com.projecthero.mod.omnitrix.alien.AlienKit.hurt(p, front, 5f, false);
		helper.assertTrue(front.isDeadOrDying(), "the kill");
		helper.assertTrue(p.getFoodData().getFoodLevel() == 10 + WildmuttKit.APPETITE_FOOD, "+2 hunger (" + p.getFoodData().getFoodLevel() + ")");
		near(helper, p.getHealth(), 20f + WildmuttKit.APPETITE_HEAL, "+1 heart");
		helper.assertFalse(k.appetite(p), "5 s before it feeds him again");
		// the wall-kick: off with Wall Climb off, a bound away from the wall with it on (no cooldown)
		helper.getLevel().setBlock(helper.absolutePos(new BlockPos(3, 2, 2)), Blocks.STONE.defaultBlockState(), 3);
		helper.getLevel().setBlock(helper.absolutePos(new BlockPos(3, 3, 2)), Blocks.STONE.defaultBlockState(), 3);
		p.setOnGround(false);
		helper.assertFalse(k.wallKick(p), "no wall-kick without Wall Climb");
		k.toggleClimb(p);
		helper.assertTrue(k.wallKick(p), "a wall-kick");
		helper.assertTrue(p.getDeltaMovement().z < -0.7 && p.getDeltaMovement().y > 0.4, "off the wall he faced, backwards and up (" + p.getDeltaMovement() + ")");
		helper.assertTrue(k.flag(p, WildmuttKit.F_LEAPING), "a leap: no fall damage");
		helper.assertTrue(k.wallKick(p), "no cooldown");
		helper.getLevel().setBlock(helper.absolutePos(new BlockPos(3, 2, 2)), Blocks.AIR.defaultBlockState(), 3);
		helper.getLevel().setBlock(helper.absolutePos(new BlockPos(3, 3, 2)), Blocks.AIR.defaultBlockState(), 3);
		helper.assertFalse(k.wallKick(p), "no wall, no kick");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void heSensesMovementAndSmokeChokesHisNose(GameTestHelper helper) {
		floor(helper);
		// motion: bright while it moves, faint while it stands still or sneaks -- and those only within 8
		helper.assertTrue(WildmuttKit.senseStrength(12, 24, true, false) == 2, "a mover at 12: bright");
		helper.assertTrue(WildmuttKit.senseStrength(5, 24, false, false) == 1, "standing still at 5: faint");
		helper.assertTrue(WildmuttKit.senseStrength(5, 24, true, true) == 1, "sneaking at 5: faint");
		helper.assertTrue(WildmuttKit.senseStrength(12, 24, false, false) == 0, "standing still at 12: gone");
		helper.assertTrue(WildmuttKit.senseStrength(12, 24, true, true) == 0, "sneaking at 12: gone");
		helper.assertTrue(WildmuttKit.senseStrength(30, 24, true, false) == 0, "out of range");
		ServerPlayer p = wildmutt(helper);
		WildmuttKit k = kit();
		helper.assertFalse(WildmuttKit.smellsSmoke(p), "clean air");
		// a lit campfire beside him: its smoke chokes his nose -- senses down to 4
		helper.getLevel().setBlock(helper.absolutePos(new BlockPos(3, 2, 2)), Blocks.CAMPFIRE.defaultBlockState(), 3);
		helper.assertTrue(WildmuttKit.smellsSmoke(p), "campfire smoke");
		long now = helper.getLevel().getGameTime();
		p.tickCount++;
		k.setTimer(p, "choked_until", 0);
		k.choke(p);
		helper.assertTrue(k.choked(p), "choked");
		near(helper, k.senseRange(p), WildmuttKit.CHOKE_RANGE, "senses 4");
		// out of the smoke, once it wears off he sneezes it out
		helper.getLevel().setBlock(helper.absolutePos(new BlockPos(3, 2, 2)), Blocks.AIR.defaultBlockState(), 3);
		k.setTimer(p, "choked_until", now - 1);
		Omnitrix.tick(p);
		helper.assertFalse(k.choked(p), "the nose cleared");
		helper.assertTrue(Omnitrix.state(p).animId == WildmuttKit.ANIM_SNEEZE, "with a sneeze");
		near(helper, k.senseRange(p), WildmuttKit.SENSE_RANGE, "senses 24 again");
		// freezing in powder snow chokes it too
		p.setTicksFrozen(20);
		helper.assertTrue(WildmuttKit.smellsSmoke(p), "frozen nose");
		p.setTicksFrozen(0);
		helper.succeed();
	}

	// ================================================================ the senses

	@GameTest(template = EMPTY_STRUCTURE)
	public void theScentSenseWidensShrinksAndGoesDeaf(GameTestHelper helper) {
		floor(helper);
		ServerPlayer p = wildmutt(helper);
		WildmuttKit k = kit();
		near(helper, k.senseRange(p), WildmuttKit.SENSE_RANGE, "24 blocks");
		k.sniff(p);
		near(helper, k.senseRange(p), WildmuttKit.SNIFF_RANGE, "48 while sniffing");
		k.setFlag(p, WildmuttKit.F_SNIFF, false);
		// no eyes: blindness and darkness do nothing
		p.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 100));
		p.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 100));
		p.tickCount++;
		Omnitrix.tick(p);
		helper.assertTrue(!p.hasEffect(MobEffects.BLINDNESS) && !p.hasEffect(MobEffects.DARKNESS), "blindness and darkness are shrugged off");
		// an explosion deafens him
		p.invulnerableTime = 0;
		p.hurt(helper.getLevel().damageSources().explosion(null, null), 2f);
		near(helper, k.senseRange(p), 0, "deafened");
		k.setTimer(p, "deaf_until", 0);
		near(helper, k.senseRange(p), WildmuttKit.SENSE_RANGE, "and back after it");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 60)
	public void wallClimbTurnsOnAndOff(GameTestHelper helper) {
		floor(helper);
		ServerPlayer p = wildmutt(helper);
		WildmuttKit k = kit();
		p.horizontalCollision = true;
		helper.assertFalse(k.climbs(p), "no climbing with Wall Climb off");
		helper.assertTrue(k.toggleClimb(p) && k.flag(p, WildmuttKit.F_CLIMB), "Wall Climb on");
		helper.assertTrue(k.climbs(p) && p.onClimbable(), "pressed into a wall he climbs it");
		p.setShiftKeyDown(true);
		helper.assertFalse(k.climbs(p), "Sneak lets go");
		p.setShiftKeyDown(false);
		helper.assertFalse(p.hurt(helper.getLevel().damageSources().fall(), 5f), "no fall damage with Wall Climb on");
		helper.assertFalse(k.toggleClimb(p), "the toggle has a short cooldown");
		helper.runAfterDelay(12, () -> {
			helper.assertTrue(k.toggleClimb(p) && !k.flag(p, WildmuttKit.F_CLIMB), "Wall Climb off");
			helper.assertFalse(k.climbs(p), "no more climbing");
			p.horizontalCollision = false;
			helper.succeed();
		});
	}

	// ================================================================ saves made as Wildvine

	@GameTest(template = EMPTY_STRUCTURE)
	public void aWildvineSaveLoadsAsWildmutt(GameTestHelper helper) {
		OmnitrixState old = new OmnitrixState();
		old.hasPower = true;
		old.selected = "wildvine";
		old.active = "wildvine";
		old.kitFlags = 5;
		old.animId = 7;
		old.timers.put("wildvine.regrow_end", 1234L);
		old.timers.put("heatblast.fire_blast", 99L);
		var json = OmnitrixState.CODEC.encodeStart(JsonOps.INSTANCE, old).getOrThrow();
		OmnitrixState back = OmnitrixState.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow();
		helper.assertTrue("wildmutt".equals(back.selected), "selected wildvine -> wildmutt (" + back.selected + ")");
		helper.assertTrue("wildmutt".equals(back.active), "active wildvine -> wildmutt (" + back.active + ")");
		helper.assertTrue(back.kitFlags == 0 && back.animId == 0, "Wildvine's bits and pose dropped");
		helper.assertTrue(!back.timers.containsKey("wildvine.regrow_end") && back.timers.get("heatblast.fire_blast") == 99L,
				"Wildvine's timers dropped, the rest kept");
		helper.assertTrue(Aliens.playable(back.selected), "and Wildmutt is playable");
		helper.assertTrue(Aliens.byId("wildvine") == null, "Wildvine is off the dial");
		// a player loaded that way transforms into Wildmutt without trouble
		ServerPlayer p = mock(helper, 3.5, 2, 1.5);
		helper.assertTrue(Omnitrix.grant(p), "the Omnitrix goes on");
		OmnitrixState n = Omnitrix.state(p).copy();
		n.selected = back.selected;
		Omnitrix.save(p, n);
		helper.assertTrue(Omnitrix.transform(p) && WildmuttKit.ID.equals(Omnitrix.state(p).active), "transforms into Wildmutt");
		helper.succeed();
	}
}
