package com.projecthero.mod.gametest;

import com.projecthero.mod.beamclash.BeamClash;
import com.projecthero.mod.saiyan.SaiyanForm;
import com.projecthero.mod.saiyan.moves.SaiyanMoveConfig;
import com.projecthero.mod.saiyan.moves.SaiyanMoves;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;

/**
 * v0.17.3: the Saiyan moves' server side -- damage (times the form), Ki costs, gating (the orb in base form only, Dragon Rush
 * in Super Saiyan), cooldowns, and the Ki Beam joining a beam clash. The form and Ki come from
 * {@link SaiyanMoves#overrideForTest} so these don't depend on the core. Mock players are not ticked by the server, so tests
 * over time drive {@link SaiyanMoves#tick}.
 */
public class SaiyanMovesGameTests implements FabricGameTest {
	private static ServerPlayer saiyan(GameTestHelper helper, SaiyanForm form, float ki) {
		ServerPlayer p = helper.makeMockServerPlayerInLevel();
		p.setGameMode(GameType.SURVIVAL);
		Vec3 at = helper.absoluteVec(new Vec3(2.5, 2.0, 2.5));
		p.moveTo(at.x, at.y, at.z, 0f, 0f); // facing +z
		p.setYHeadRot(0f);
		p.yBodyRot = 0f;
		SaiyanMoves.resetCooldowns(p);
		SaiyanMoves.overrideForTest(p, form, ki);
		return p;
	}

	/** A still, armourless zombie with 200 health, {@code dz} blocks in front of the Saiyan. */
	private static Zombie target(GameTestHelper helper, double dz) {
		Zombie z = helper.spawn(EntityType.ZOMBIE, new BlockPos(2, 2, 2));
		z.setNoAi(true);
		z.getAttribute(Attributes.MAX_HEALTH).setBaseValue(200);
		z.getAttribute(Attributes.ARMOR).setBaseValue(0);
		z.setHealth(200);
		z.addEffect(new net.minecraft.world.effect.MobEffectInstance(MobEffects.FIRE_RESISTANCE, 100000, 0, false, false)); // daylight must not burn it
		Vec3 at = helper.absoluteVec(new Vec3(2.5, 2.0, 2.5 + dz));
		z.moveTo(at.x, at.y, at.z, 0f, 0f);
		return z;
	}

	private static float taken(Zombie z) {
		return 200f - z.getHealth();
	}

	private static boolean near(float a, float b) {
		return Math.abs(a - b) < 0.05f;
	}

	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void comboIsThreeHitsThenALaunchingKick(GameTestHelper helper) {
		ServerPlayer p = saiyan(helper, SaiyanForm.BASE, 100f);
		Zombie z = target(helper, 2.0);
		for (int i = 0; i < 4; i++) {
			int k = i;
			helper.runAfterDelay(1 + i * (SaiyanMoveConfig.COMBO_STEP_TICKS + 1), () -> {
				SaiyanMoves.press(p, SaiyanMoves.COMBO, true);
				if (k < 3) {
					helper.assertTrue(near(taken(z), SaiyanMoveConfig.COMBO_HIT_DAMAGE * (k + 1)), "hit " + (k + 1) + ": " + taken(z));
				}
			});
		}
		helper.runAfterDelay(1 + 3 * (SaiyanMoveConfig.COMBO_STEP_TICKS + 1) + 1, () -> {
			float want = SaiyanMoveConfig.COMBO_HIT_DAMAGE * 3 + SaiyanMoveConfig.COMBO_KICK_DAMAGE;
			helper.assertTrue(near(taken(z), want), "three hits and the kick: " + taken(z) + ", not " + want);
			helper.assertTrue(z.getDeltaMovement().y > 0.2 || z.getY() > helper.absoluteVec(new Vec3(0, 2.05, 0)).y, "the kick launches them");
			helper.assertTrue(SaiyanMoves.comboStep(p) == 0, "and the combo starts over");
			helper.assertTrue(SaiyanMoves.cooldownRemaining(p, SaiyanMoves.COMBO) > 0, "a short recovery after the kick");
			helper.succeed();
		});
	}

	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void damageScalesWithTheForm(GameTestHelper helper) {
		ServerPlayer p = saiyan(helper, SaiyanForm.SSJ, 100f);
		Zombie z = target(helper, 2.0);
		SaiyanMoves.press(p, SaiyanMoves.COMBO, true);
		float want = SaiyanMoveConfig.COMBO_HIT_DAMAGE * SaiyanForm.SSJ.damageMult();
		helper.assertTrue(near(taken(z), want), "Super Saiyan's first hit: " + taken(z) + ", not " + want);
		helper.succeed();
	}

	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void feintStunsThenKicks(GameTestHelper helper) {
		ServerPlayer p = saiyan(helper, SaiyanForm.BASE, 100f);
		Zombie z = target(helper, 2.0);
		helper.onEachTick(() -> SaiyanMoves.tick(p));
		SaiyanMoves.press(p, SaiyanMoves.FEINT, true);
		helper.assertTrue(near(taken(z), SaiyanMoveConfig.FEINT_JAB_DAMAGE), "the jab: " + taken(z));
		helper.assertTrue(z.hasEffect(MobEffects.MOVEMENT_SLOWDOWN), "the jab stuns");
		helper.assertTrue(near(SaiyanMoves.kiOf(p), 100f - SaiyanMoveConfig.FEINT_KI), "costs Ki: " + SaiyanMoves.kiOf(p));
		helper.assertTrue(SaiyanMoves.cooldownRemaining(p, SaiyanMoves.FEINT) > 0, "on cooldown");
		helper.runAfterDelay(SaiyanMoveConfig.FEINT_KICK_AT + 3, () -> {
			float want = SaiyanMoveConfig.FEINT_JAB_DAMAGE + SaiyanMoveConfig.FEINT_KICK_DAMAGE;
			helper.assertTrue(near(taken(z), want), "then the kick: " + taken(z) + ", not " + want);
			helper.succeed();
		});
	}

	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void noKiNoMove(GameTestHelper helper) {
		ServerPlayer p = saiyan(helper, SaiyanForm.BASE, 3f);
		Zombie z = target(helper, 2.0);
		SaiyanMoves.press(p, SaiyanMoves.FEINT, true);
		helper.assertTrue(taken(z) == 0f && SaiyanMoves.running(p) == null, "3 Ki is not enough for a Feint");
		helper.assertTrue(SaiyanMoves.cooldownRemaining(p, SaiyanMoves.FEINT) == 0, "and nothing goes on cooldown");
		helper.succeed();
	}

	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = 200)
	public void kiBeamChargesFiresAndHits(GameTestHelper helper) {
		ServerPlayer p = saiyan(helper, SaiyanForm.BASE, 100f);
		Zombie z = target(helper, 6.0);
		helper.onEachTick(() -> SaiyanMoves.tick(p));
		SaiyanMoves.press(p, SaiyanMoves.KI_BEAM, true);
		helper.assertTrue(SaiyanMoves.KI_BEAM.equals(SaiyanMoves.running(p)), "the beam starts charging");
		helper.runAfterDelay(SaiyanMoveConfig.BEAM_MAX_CHARGE + 2, () -> {
			helper.assertTrue(SaiyanMoves.charge(p) >= 0.99f, "a full charge fires on its own: " + SaiyanMoves.charge(p));
		});
		helper.runAfterDelay(SaiyanMoveConfig.BEAM_MAX_CHARGE + SaiyanMoveConfig.BEAM_FIRE_TICKS + 4, () -> {
			// ten hits of 1.5 x 2 = 30 at a full charge (give or take one for the timing)
			float full = SaiyanMoveConfig.BEAM_HIT_DAMAGE * 2f * SaiyanMoveConfig.BEAM_FIRE_TICKS / SaiyanMoveConfig.BEAM_HIT_EVERY;
			helper.assertTrue(taken(z) >= full - 3.5f && taken(z) <= full + 3.5f, "the beam's damage: " + taken(z) + ", about " + full);
			helper.assertTrue(SaiyanMoves.running(p) == null, "it ends");
			helper.assertTrue(SaiyanMoves.cooldownRemaining(p, SaiyanMoves.KI_BEAM) > 0, "on cooldown after");
			helper.assertTrue(SaiyanMoves.kiOf(p) < 100f - SaiyanMoveConfig.BEAM_KI - 10f, "Ki to start and Ki while firing: " + SaiyanMoves.kiOf(p));
			helper.succeed();
		});
	}

	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = 200)
	public void kiBeamJoinsABeamClash(GameTestHelper helper) {
		ServerPlayer p = saiyan(helper, SaiyanForm.BASE, 100f);
		Zombie z = target(helper, 5.0);
		boolean[] done = { false };
		helper.onEachTick(() -> {
			SaiyanMoves.tick(p);
			// the zombie fires a beam straight back at him
			BeamClash.report(z, z.getEyePosition(), p.getEyePosition().subtract(z.getEyePosition()), 32, 1f, 0x00FF00);
			if (!done[0] && BeamClash.inClash(p)) {
				done[0] = true;
				helper.succeed();
			}
		});
		SaiyanMoves.press(p, SaiyanMoves.KI_BEAM, true);
		helper.runAfterDelay(SaiyanMoveConfig.BEAM_MIN_CHARGE + 1, () -> SaiyanMoves.press(p, SaiyanMoves.KI_BEAM, false));
	}

	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = 240)
	public void spiritOrbIsBaseFormOnlyAndBlowsUp(GameTestHelper helper) {
		ServerPlayer gold = saiyan(helper, SaiyanForm.SSJ, 100f);
		SaiyanMoves.press(gold, SaiyanMoves.SPIRIT_ORB, true);
		helper.assertTrue(SaiyanMoves.running(gold) == null, "no Spirit Orb in Super Saiyan");
		SaiyanMoves.overrideForTest(gold, null, 0f);
		ServerPlayer p = saiyan(helper, SaiyanForm.BASE, 100f);
		p.setXRot(30f); // aim a little down, at the zombie
		Zombie z = target(helper, 7.0);
		helper.onEachTick(() -> SaiyanMoves.tick(p));
		SaiyanMoves.press(p, SaiyanMoves.SPIRIT_ORB, true);
		helper.assertTrue(SaiyanMoves.SPIRIT_ORB.equals(SaiyanMoves.running(p)), "it starts charging in base form");
		helper.runAfterDelay(SaiyanMoveConfig.ORB_MAX_CHARGE + 2, () -> {
			helper.assertTrue(SaiyanMoves.running(p) == null, "a full charge throws it");
			helper.assertTrue(near(SaiyanMoves.kiOf(p), 100f - SaiyanMoveConfig.ORB_KI), "50 Ki on the throw: " + SaiyanMoves.kiOf(p));
			helper.assertTrue(SaiyanMoves.cooldownRemaining(p, SaiyanMoves.SPIRIT_ORB) > 0, "the long cooldown");
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(SaiyanMoves.orbsInFlight() == 0 && taken(z) > SaiyanMoveConfig.ORB_MIN_DAMAGE * 0.5f,
					"the orb lands and blows up: " + taken(z));
		});
	}

	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void orbLetGoTooSoonFizzles(GameTestHelper helper) {
		ServerPlayer p = saiyan(helper, SaiyanForm.BASE, 100f);
		helper.onEachTick(() -> SaiyanMoves.tick(p));
		SaiyanMoves.press(p, SaiyanMoves.SPIRIT_ORB, true);
		helper.runAfterDelay(5, () -> SaiyanMoves.press(p, SaiyanMoves.SPIRIT_ORB, false));
		helper.runAfterDelay(8, () -> {
			helper.assertTrue(SaiyanMoves.running(p) == null && SaiyanMoves.orbsInFlight() == 0, "too short: nothing thrown");
			helper.assertTrue(near(SaiyanMoves.kiOf(p), 100f), "and nothing spent");
			helper.assertTrue(SaiyanMoves.cooldownRemaining(p, SaiyanMoves.SPIRIT_ORB) == 0, "nor a cooldown");
			helper.succeed();
		});
	}

	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = 120)
	public void barrageHitsOverAndOverThenFinishes(GameTestHelper helper) {
		ServerPlayer p = saiyan(helper, SaiyanForm.BASE, 100f);
		Zombie z = target(helper, 2.0);
		helper.onEachTick(() -> {
			SaiyanMoves.tick(p);
			z.setPos(z.getX(), z.getY(), helper.absoluteVec(new Vec3(0, 0, 4.5)).z); // held in front of him
		});
		SaiyanMoves.press(p, SaiyanMoves.BARRAGE, true);
		helper.assertTrue(near(SaiyanMoves.kiOf(p), 100f - SaiyanMoveConfig.BARRAGE_KI), "10 Ki");
		helper.runAfterDelay(SaiyanMoveConfig.BARRAGE_TICKS + 3, () -> {
			int hits = SaiyanMoveConfig.BARRAGE_TICKS / SaiyanMoveConfig.BARRAGE_HIT_EVERY;
			float want = hits * SaiyanMoveConfig.BARRAGE_HIT_DAMAGE + SaiyanMoveConfig.BARRAGE_FINISHER_DAMAGE;
			helper.assertTrue(taken(z) >= want - 2.6f && taken(z) <= want + 0.1f, "the barrage: " + taken(z) + ", about " + want);
			helper.assertTrue(SaiyanMoves.running(p) == null, "it ends");
			helper.succeed();
		});
	}

	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = 120)
	public void skyComboLaunchesKicksAndSmashes(GameTestHelper helper) {
		ServerPlayer p = saiyan(helper, SaiyanForm.BASE, 100f);
		Zombie z = target(helper, 2.5);
		helper.onEachTick(() -> SaiyanMoves.tick(p));
		SaiyanMoves.press(p, SaiyanMoves.SKY_COMBO, true);
		helper.assertTrue(near(taken(z), SaiyanMoveConfig.SKY_LAUNCH_DAMAGE), "the launching kick: " + taken(z));
		helper.assertTrue(z.getDeltaMovement().y > 1.0, "kicked up: " + z.getDeltaMovement());
		helper.runAfterDelay(SaiyanMoveConfig.SKY_SMASH + 1, () -> {
			float want = SaiyanMoveConfig.SKY_LAUNCH_DAMAGE + 2 * SaiyanMoveConfig.SKY_AIR_DAMAGE + SaiyanMoveConfig.SKY_SMASH_DAMAGE;
			helper.assertTrue(near(taken(z), want), "kick, two air kicks, the smash: " + taken(z) + ", not " + want);
			helper.succeed();
		});
	}

	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void skyComboWhiffHasAShortCooldown(GameTestHelper helper) {
		ServerPlayer p = saiyan(helper, SaiyanForm.BASE, 100f);
		SaiyanMoves.press(p, SaiyanMoves.SKY_COMBO, true);
		helper.assertTrue(SaiyanMoves.running(p) == null, "no one to kick");
		int cd = SaiyanMoves.cooldownRemaining(p, SaiyanMoves.SKY_COMBO);
		helper.assertTrue(cd > 0 && cd <= SaiyanMoveConfig.SKY_WHIFF_COOLDOWN, "a short whiff cooldown: " + cd);
		helper.assertTrue(near(SaiyanMoves.kiOf(p), 100f), "no Ki for a whiff");
		helper.succeed();
	}

	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = 100)
	public void dragonRushNeedsSuperSaiyan(GameTestHelper helper) {
		ServerPlayer weak = saiyan(helper, SaiyanForm.BASE, 100f);
		SaiyanMoves.press(weak, SaiyanMoves.DRAGON_RUSH, true);
		helper.assertTrue(SaiyanMoves.running(weak) == null, "not in Base");
		SaiyanMoves.overrideForTest(weak, null, 0f);
		ServerPlayer p = saiyan(helper, SaiyanForm.SSJ, 100f);
		Zombie z = target(helper, 2.0);
		helper.onEachTick(() -> SaiyanMoves.tick(p));
		SaiyanMoves.press(p, SaiyanMoves.DRAGON_RUSH, true);
		helper.assertTrue(SaiyanMoves.DRAGON_RUSH.equals(SaiyanMoves.running(p)), "Super Saiyan can");
		helper.assertTrue(near(SaiyanMoves.kiOf(p), 100f - SaiyanMoveConfig.RUSH_KI), "30 Ki");
		helper.runAfterDelay(SaiyanMoveConfig.RUSH_WINDUP + SaiyanMoveConfig.RUSH_TICKS + 2, () -> {
			float want = SaiyanMoveConfig.RUSH_DAMAGE * SaiyanForm.SSJ.damageMult();
			helper.assertTrue(taken(z) >= want - 0.05f && taken(z) <= want + 1.5f, "the rush lands once: " + taken(z) + ", not " + want);
			helper.assertTrue(SaiyanMoves.cooldownRemaining(p, SaiyanMoves.DRAGON_RUSH) > 0, "on cooldown");
			helper.succeed();
		});
	}

	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void instantStepGoesBehindWhoHeLooksAt(GameTestHelper helper) {
		ServerPlayer p = saiyan(helper, SaiyanForm.BASE, 100f);
		Zombie z = target(helper, 3.5);
		z.setYRot(180f); // facing him: behind it is further away
		z.setYBodyRot(180f);
		Vec3 start = p.position();
		SaiyanMoves.press(p, SaiyanMoves.INSTANT_STEP, true);
		helper.assertTrue(p.position().distanceTo(start) > 3.0, "he moved: " + p.position().distanceTo(start));
		helper.assertTrue(p.position().distanceTo(z.position()) < 2.3, "next to the zombie: " + p.position().distanceTo(z.position()));
		helper.assertTrue(p.getZ() > z.getZ(), "behind it");
		helper.assertTrue(near(SaiyanMoves.kiOf(p), 100f - SaiyanMoveConfig.STEP_KI), "10 Ki");
		helper.assertTrue(SaiyanMoves.cooldownRemaining(p, SaiyanMoves.INSTANT_STEP) > 0, "on cooldown");
		helper.succeed();
	}

	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void oneMoveAtATimeAndCooldownsHold(GameTestHelper helper) {
		ServerPlayer p = saiyan(helper, SaiyanForm.BASE, 100f);
		target(helper, 2.0);
		SaiyanMoves.press(p, SaiyanMoves.BARRAGE, true);
		SaiyanMoves.press(p, SaiyanMoves.FEINT, true);
		helper.assertTrue(SaiyanMoves.BARRAGE.equals(SaiyanMoves.running(p)), "the barrage keeps going");
		helper.assertTrue(SaiyanMoves.cooldownRemaining(p, SaiyanMoves.FEINT) == 0, "the feint never started");
		for (String id : SaiyanMoves.IDS) {
			helper.assertTrue(SaiyanMoves.maxCooldown(id) > 0, "a max cooldown for " + id);
		}
		helper.succeed();
	}
}
