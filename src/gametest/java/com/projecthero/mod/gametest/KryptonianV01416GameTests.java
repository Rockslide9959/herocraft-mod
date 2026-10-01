package com.projecthero.mod.gametest;

import com.projecthero.mod.hero.AbilitySlot;
import com.projecthero.mod.kryptonian.Kryptonian;
import com.projecthero.mod.kryptonian.KryptonianAbilities;
import com.projecthero.mod.kryptonian.KryptonianAbilityManager;
import com.projecthero.mod.kryptonian.KryptonianConfig;
import com.projecthero.mod.kryptonian.KryptonianFlight;
import com.projecthero.mod.kryptonian.data.KryptonianState;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/**
 * v0.14.16 Kryptonian rework: the new (much lower) move costs, the 5 s refill delay after any drain, flight's 0.1 a
 * second, the conditional Solar-paid Regeneration III, the full-bar Solar Flare and its 30 s powerless spell with 6 s of
 * debuffs, the free X-Ray toggle (key-repeat safe), the free Pick Up with no time limit and its gentle Shift+V set-down,
 * and the new C / Shift+C attacks. Mock players are not reliably ticked by the server, so each test drives
 * {@link Kryptonian#tick} itself (and advances {@code tickCount}, which the once-a-second checks read).
 */
public class KryptonianV01416GameTests implements FabricGameTest {
	private static final float EPS = 0.01f;

	private static ServerPlayer hero(GameTestHelper helper) {
		ServerPlayer p = helper.makeMockServerPlayerInLevel();
		p.setGameMode(GameType.SURVIVAL);
		Vec3 at = helper.absoluteVec(new Vec3(2.5, 2.0, 2.5));
		p.moveTo(at.x, at.y, at.z, 0.0f, 0.0f);
		try {
			java.lang.reflect.Field f = ServerPlayer.class.getDeclaredField("spawnInvulnerableTime");
			f.setAccessible(true);
			f.setInt(p, 0);
		} catch (ReflectiveOperationException e) {
			throw new IllegalStateException(e);
		}
		Kryptonian.grant(p);
		return p;
	}

	private static void pump(GameTestHelper helper, ServerPlayer p) {
		helper.onEachTick(() -> {
			p.tickCount++;
			Kryptonian.tick(p);
		});
	}

	private static <T extends Mob> T ahead(GameTestHelper helper, ServerPlayer p, EntityType<T> type, double distance) {
		T m = type.create(helper.getLevel());
		m.moveTo(p.getX(), p.getY(), p.getZ() + distance, 180.0f, 0.0f);
		m.setNoAi(true);
		helper.getLevel().addFreshEntity(m);
		return m;
	}

	private static void floor(GameTestHelper helper) {
		for (int x = 0; x <= 6; x++) {
			for (int z = 0; z <= 7; z++) {
				helper.getLevel().setBlock(helper.absolutePos(new BlockPos(x, 1, z)), Blocks.STONE.defaultBlockState(), 3);
			}
		}
	}

	private static void spends(GameTestHelper helper, ServerPlayer p, Runnable move, float expected, String what) {
		Kryptonian.setSolar(p, KryptonianConfig.SOLAR_MAX);
		move.run();
		float spent = KryptonianConfig.SOLAR_MAX - Kryptonian.solar(p);
		helper.assertTrue(Math.abs(spent - expected) < EPS, what + " costs " + expected + ", spent " + spent);
	}

	// ---------------------------------------------------------------- costs

	@GameTest(template = EMPTY_STRUCTURE)
	public void everyMoveCostsTheNewAmount(GameTestHelper helper) {
		ServerPlayer p = hero(helper);
		helper.assertTrue(KryptonianConfig.SOLAR_MAX == 100f, "the bar tops out at 100");
		spends(helper, p, () -> KryptonianAbilities.punch(p), 5f, "R Punch");
		spends(helper, p, () -> KryptonianAbilities.thunderclap(p), 5f, "Shift+R Thunderclap");
		spends(helper, p, () -> KryptonianAbilities.groundSlam(p), 10f, "Shift+G Ground Pound");
		spends(helper, p, () -> KryptonianAbilities.superDash(p), KryptonianConfig.DASH_COST, "X Super Dash");
		spends(helper, p, () -> KryptonianAbilities.skyLaunch(p), KryptonianConfig.LAUNCH_COST, "Shift+X Sky Launch");
		spends(helper, p, () -> KryptonianAbilities.barrage(p), 5f, "C Barrage");
		spends(helper, p, () -> KryptonianAbilities.meteorStrike(p), 10f, "Shift+C Meteor Strike");
		spends(helper, p, () -> KryptonianAbilities.solarFlare(p), 100f, "Shift+Z Solar Flare (all of it)");
		helper.assertTrue(KryptonianConfig.HEAT_COST_PER_SECOND == 1f && KryptonianConfig.BREATH_COST_PER_SECOND == 1f,
				"Heat Vision and Freeze Breath cost 1 a second");
		helper.assertTrue(KryptonianConfig.DASH_DISTANCE >= 1.6 * 16.0, "the dash goes further (was 16 blocks)");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 200)
	public void heldBeamsDrainOneASecond(GameTestHelper helper) {
		ServerPlayer p = hero(helper);
		pump(helper, p);
		Kryptonian.setSolar(p, 50.5f);
		Kryptonian.spendSolar(p, 0.5f); // a drain now, so the sun does not refill the bar under the test
		KryptonianAbilityManager.handle(p, AbilitySlot.SLOT_2, true); // G: heat vision
		helper.assertTrue(Kryptonian.heatVisionActive(p), "G opens Heat Vision");
		helper.runAfterDelay(41, () -> {
			KryptonianAbilityManager.handle(p, AbilitySlot.SLOT_2, false);
			float spent = 50f - Kryptonian.solar(p);
			helper.assertTrue(spent >= 1.5f && spent <= 3.0f, "about 2 in 2 seconds, spent " + spent);
			helper.assertFalse(Kryptonian.heatVisionActive(p), "letting go of G ends it");
			Kryptonian.setSolar(p, 50f);
			KryptonianAbilityManager.handle(p, AbilitySlot.SLOT_4, true); // Z: freeze breath
			helper.assertTrue(KryptonianAbilities.running(p, KryptonianAbilities.FREEZE_BREATH), "Z opens Freeze Breath");
			helper.runAfterDelay(41, () -> {
				KryptonianAbilityManager.handle(p, AbilitySlot.SLOT_4, false);
				float spent2 = 50f - Kryptonian.solar(p);
				helper.assertTrue(spent2 >= 1.5f && spent2 <= 3.0f, "about 2 in 2 seconds, spent " + spent2);
				helper.assertFalse(KryptonianAbilities.running(p, KryptonianAbilities.FREEZE_BREATH), "letting go of Z ends it");
				helper.succeed();
			});
		});
	}

	// ---------------------------------------------------------------- refill delay

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 240)
	public void solarRefillsOnlyFiveSecondsAfterTheLastDrain(GameTestHelper helper) {
		ServerPlayer p = hero(helper);
		pump(helper, p);
		Kryptonian.setSolar(p, 50f);
		helper.assertTrue(Kryptonian.spendSolar(p, 5f), "spends 5");
		helper.assertTrue(Kryptonian.solarRegenPaused(p), "the refill is paused right after a drain");
		helper.runAfterDelay(80, () -> {
			helper.assertTrue(Math.abs(Kryptonian.solar(p) - 45f) < EPS, "4 s later nothing has come back, " + Kryptonian.solar(p));
			helper.runAfterDelay(80, () -> {
				helper.assertFalse(Kryptonian.solarRegenPaused(p), "the delay is over");
				helper.assertTrue(Kryptonian.solar(p) > 45f + EPS, "8 s after the drain it refills again, " + Kryptonian.solar(p));
				helper.succeed();
			});
		});
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 200)
	public void flightDrainsATenthASecondAndStopsTheRefill(GameTestHelper helper) {
		ServerPlayer p = hero(helper);
		pump(helper, p);
		Kryptonian.setSolar(p, 50f);
		KryptonianFlight.start(p);
		helper.runAfterDelay(100, () -> {
			helper.assertTrue(Kryptonian.isFlying(p), "still flying");
			float s = Kryptonian.solar(p);
			helper.assertTrue(s < 50f - 0.25f && s > 50f - 0.75f, "about 0.5 over 5 s of flight and no refill, " + s);
			helper.assertTrue(Kryptonian.solarRegenPaused(p), "flying counts as a drain");
			// an empty bar drops him out of the sky
			Kryptonian.setSolar(p, 0.05f);
			helper.runAfterDelay(25, () -> {
				helper.assertFalse(Kryptonian.isFlying(p), "no Solar Energy, no flight");
				helper.succeed();
			});
		});
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void savedSolarAboveTheCapIsClamped(GameTestHelper helper) {
		ServerPlayer p = hero(helper);
		KryptonianState n = Kryptonian.state(p).copy();
		n.solar = 250f; // an over-full value from an older save
		p.setAttached(com.projecthero.mod.attachment.ModAttachments.KRYPTONIAN_STATE, n);
		Kryptonian.onPlayerJoin(p);
		helper.assertTrue(Kryptonian.solar(p) == KryptonianConfig.SOLAR_MAX, "clamped to 100, got " + Kryptonian.solar(p));
		helper.succeed();
	}

	// ---------------------------------------------------------------- Regeneration III

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 200)
	public void regenerationThreeOnlyWhileHurtAndItDrains(GameTestHelper helper) {
		ServerPlayer p = hero(helper);
		pump(helper, p);
		Kryptonian.setSolar(p, 50.5f);
		Kryptonian.spendSolar(p, 0.5f); // a drain now, so the sun does not refill the bar under the test
		helper.assertTrue(p.getEffect(MobEffects.REGENERATION) == null, "no Regeneration at full health");
		p.setHealth(10.0f);
		helper.runAfterDelay(2, () -> {
			MobEffectInstance regen = p.getEffect(MobEffects.REGENERATION);
			helper.assertTrue(regen != null && regen.getAmplifier() == 2, "Regeneration III while hurt");
			helper.runAfterDelay(45, () -> {
				float s = Kryptonian.solar(p);
				helper.assertTrue(s <= 49f + EPS && s >= 46f, "it costs 1 Solar Energy a second, " + s);
				p.setHealth(p.getMaxHealth());
				helper.runAfterDelay(2, () -> {
					helper.assertTrue(p.getEffect(MobEffects.REGENERATION) == null, "gone the moment he is whole");
					// hurt again with an empty bar: nothing to pay with, no Regeneration
					Kryptonian.setSolar(p, 0f);
					p.setHealth(10.0f);
					helper.runAfterDelay(2, () -> {
						helper.assertTrue(p.getEffect(MobEffects.REGENERATION) == null, "no Regeneration on an empty bar");
						helper.succeed();
					});
				});
			});
		});
	}

	// ---------------------------------------------------------------- Solar Flare

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 200)
	public void solarFlareNeedsAFullBarThenLeavesHimPowerless(GameTestHelper helper) {
		ServerPlayer p = hero(helper);
		pump(helper, p);
		Kryptonian.setSolar(p, 99f);
		KryptonianAbilities.solarFlare(p);
		helper.assertFalse(KryptonianAbilities.running(p, KryptonianAbilities.SOLAR_FLARE), "99 is not enough");
		helper.assertTrue(Math.abs(Kryptonian.solar(p) - 99f) < EPS, "and costs nothing");
		Kryptonian.setSolar(p, KryptonianConfig.SOLAR_MAX);
		KryptonianAbilities.solarFlare(p);
		helper.assertTrue(KryptonianAbilities.running(p, KryptonianAbilities.SOLAR_FLARE), "a full bar charges it");
		helper.assertTrue(Kryptonian.solar(p) == 0f, "all 100 spent");
		helper.startSequence()
				.thenWaitUntil(() -> helper.assertTrue(Kryptonian.depowered(p), "burnt out after the blast"))
				.thenExecute(() -> {
					long left = Kryptonian.state(p).depoweredUntil - helper.getLevel().getGameTime();
					helper.assertTrue(left > KryptonianConfig.FLARE_DEPOWER_TICKS - 10 && left <= 30 * 20, "powerless for 30 s, " + left);
					MobEffectInstance slow = p.getEffect(MobEffects.MOVEMENT_SLOWDOWN);
					MobEffectInstance weak = p.getEffect(MobEffects.WEAKNESS);
					MobEffectInstance blind = p.getEffect(MobEffects.BLINDNESS);
					helper.assertTrue(slow != null && slow.getAmplifier() == 3 && slow.getDuration() <= 6 * 20, "Slowness IV for 6 s");
					helper.assertTrue(weak != null && weak.getAmplifier() == 3 && weak.getDuration() <= 6 * 20, "Weakness IV for 6 s");
					helper.assertTrue(blind != null && blind.getDuration() <= 6 * 20, "Blindness for 6 s");
					helper.assertTrue(Kryptonian.damageTakenFactor(p) == 1.0f, "no damage reduction");
					KryptonianAbilities.punch(p);
					helper.assertTrue(Kryptonian.cooldownRemaining(p, KryptonianAbilities.PUNCH) == 0, "no moves");
					KryptonianFlight.toggle(p);
					helper.assertFalse(Kryptonian.isFlying(p), "no flight");
					p.setHealth(10.0f);
				})
				.thenIdle(3)
				.thenExecute(() -> helper.assertTrue(p.getEffect(MobEffects.REGENERATION) == null, "no Regeneration while powerless"))
				.thenSucceed();
	}

	// ---------------------------------------------------------------- V / Shift+V

	@GameTest(template = EMPTY_STRUCTURE)
	public void xrayIsAFreeToggleThatIgnoresKeyRepeat(GameTestHelper helper) {
		ServerPlayer p = hero(helper);
		Kryptonian.setSolar(p, 40f);
		KryptonianAbilityManager.handle(p, AbilitySlot.SLOT_5, true);
		helper.assertTrue(Kryptonian.xrayActive(p), "V: on");
		helper.assertTrue(p.getEffect(MobEffects.NIGHT_VISION) != null, "with night vision");
		KryptonianAbilityManager.handle(p, AbilitySlot.SLOT_5, true); // the keyboard's auto-repeat while V is held
		helper.assertTrue(Kryptonian.xrayActive(p), "a held key does not flick it back off");
		KryptonianAbilityManager.handle(p, AbilitySlot.SLOT_5, false);
		KryptonianAbilityManager.handle(p, AbilitySlot.SLOT_5, true);
		helper.assertFalse(Kryptonian.xrayActive(p), "V again: off");
		helper.assertTrue(p.getEffect(MobEffects.NIGHT_VISION) == null, "its night vision goes too");
		helper.assertTrue(Math.abs(Kryptonian.solar(p) - 40f) < EPS, "free");
		helper.assertTrue(Kryptonian.cooldownRemaining(p, KryptonianAbilities.XRAY) == 0, "no cooldown");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 400)
	public void shiftVSetsDownGentlyAndThereIsNoTimeLimit(GameTestHelper helper) {
		floor(helper);
		ServerPlayer p = hero(helper);
		pump(helper, p);
		Kryptonian.setSolar(p, 40f);
		LivingEntity sheep = ahead(helper, p, EntityType.SHEEP, 2.0);
		p.lookAt(net.minecraft.commands.arguments.EntityAnchorArgument.Anchor.EYES, sheep.position().add(0, sheep.getBbHeight() * 0.5, 0));
		float health = sheep.getHealth();
		KryptonianAbilities.superGrab(p);
		helper.assertTrue(KryptonianAbilities.holding(p), "picked up");
		helper.assertTrue(Math.abs(Kryptonian.solar(p) - 40f) < EPS, "picking up is free");
		helper.runAfterDelay(KryptonianConfig.GRAB_COOLDOWN + 220, () -> {
			helper.assertTrue(KryptonianAbilities.holding(p), "still carried after 11+ s: no time limit");
			p.setXRot(0.0f);
			Kryptonian.setSolar(p, 40f); // (the sun may have topped it up while he carried it)
			KryptonianAbilities.superGrab(p); // Shift+V again
			helper.assertFalse(KryptonianAbilities.holding(p), "set down");
			helper.assertTrue(sheep.getHealth() >= health, "no damage, " + health + " -> " + sheep.getHealth());
			helper.assertTrue(sheep.getDeltaMovement().lengthSqr() < 1.0e-6, "no throw: it is not moving");
			helper.assertFalse(sheep.isNoGravity(), "gravity back");
			double ground = helper.absoluteVec(new Vec3(0, 2.0, 0)).y;
			helper.assertTrue(Math.abs(sheep.getY() - ground) < 0.05, "standing on the ground, y " + sheep.getY() + " vs " + ground);
			sheep.invulnerableTime = 0;
			sheep.hurt(helper.getLevel().damageSources().fall(), 6.0f);
			helper.assertTrue(sheep.getHealth() >= health, "and no fall damage from the set-down");
			helper.assertTrue(Math.abs(Kryptonian.solar(p) - 40f) < EPS, "setting down is free");
			helper.succeed();
		});
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 100)
	public void aCarriedCreatureCannotHurtAnyone(GameTestHelper helper) {
		floor(helper);
		ServerPlayer p = hero(helper);
		pump(helper, p);
		LivingEntity zombie = ahead(helper, p, EntityType.ZOMBIE, 2.0);
		p.lookAt(net.minecraft.commands.arguments.EntityAnchorArgument.Anchor.EYES, zombie.position().add(0, zombie.getBbHeight() * 0.5, 0));
		KryptonianAbilities.pickUp(p);
		helper.assertTrue(KryptonianAbilities.isCarriedBy(p, zombie), "carrying the zombie");
		LivingEntity pig = ahead(helper, p, EntityType.PIG, -2.0);
		float before = pig.getHealth();
		pig.hurt(helper.getLevel().damageSources().mobAttack(zombie), 4.0f);
		helper.assertTrue(pig.getHealth() >= before, "the carried zombie's hit does nothing");
		zombie.invulnerableTime = 0;
		float zBefore = zombie.getHealth();
		zombie.hurt(helper.getLevel().damageSources().inWall(), 2.0f);
		helper.assertTrue(zombie.getHealth() >= zBefore, "and being carried through a wall does not hurt it");
		KryptonianAbilities.throwHeld(p);
		helper.assertFalse(KryptonianAbilities.holding(p), "V throws it");
		helper.succeed();
	}

	// ---------------------------------------------------------------- C / Shift+C

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 200)
	public void barrageAndMeteorStrikeHitHard(GameTestHelper helper) {
		ServerPlayer p = hero(helper);
		pump(helper, p);
		LivingEntity golem = ahead(helper, p, EntityType.IRON_GOLEM, 2.5);
		float before = golem.getHealth();
		KryptonianAbilityManager.handle(p, AbilitySlot.SLOT_6, true); // C
		helper.assertTrue(KryptonianAbilities.running(p, KryptonianAbilities.BARRAGE), "C starts the barrage");
		helper.startSequence()
				.thenWaitUntil(() -> helper.assertFalse(KryptonianAbilities.running(p, KryptonianAbilities.BARRAGE), "the flurry ends"))
				.thenExecute(() -> {
					helper.assertTrue(golem.getHealth() < before - 20f || !golem.isAlive(),
							"eight blows and a haymaker land, " + before + " -> " + golem.getHealth());
					helper.assertTrue(Kryptonian.cooldownRemaining(p, KryptonianAbilities.BARRAGE) > 0, "cooldown");
				})
				.thenExecute(() -> {
					KryptonianAbilityManager.handle(p, AbilitySlot.SLOT_6, false);
					p.setShiftKeyDown(true);
					KryptonianAbilityManager.handle(p, AbilitySlot.SLOT_6, true); // Shift+C
					p.setShiftKeyDown(false);
					helper.assertTrue(KryptonianAbilities.running(p, KryptonianAbilities.METEOR_STRIKE), "Shift+C starts the Meteor Strike");
				})
				.thenWaitUntil(() -> helper.assertFalse(KryptonianAbilities.running(p, KryptonianAbilities.METEOR_STRIKE), "it lands"))
				.thenExecute(() -> helper.assertTrue(Kryptonian.cooldownRemaining(p, KryptonianAbilities.METEOR_STRIKE) > 0, "cooldown"))
				.thenSucceed();
	}
}
