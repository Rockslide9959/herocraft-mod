package com.projecthero.mod.gametest;

import com.projecthero.mod.beamclash.BeamClash;
import com.projecthero.mod.saiyan.SaiyanForm;
import com.projecthero.mod.saiyan.kit.SaiyanKit;
import com.projecthero.mod.saiyan.kit.SaiyanKitConfig;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;

/**
 * v0.17.3 Saiyan revamp: the move kit's numbers -- damage (and the damage multiplier), Ki, cooldowns, gating and the shape of
 * each move. Form and Ki come from {@link SaiyanKit#overrideForTest} so these don't depend on the core. Mock players aren't
 * ticked by the server, so tests over time drive {@link SaiyanKit#tick}. Everything stays inside the 8x8 barrier box.
 */
public class SaiyanKitGameTests implements FabricGameTest {
	private static ServerPlayer saiyan(GameTestHelper helper, SaiyanForm form, float ki) {
		ServerPlayer p = helper.makeMockServerPlayerInLevel();
		p.setGameMode(GameType.SURVIVAL);
		Vec3 at = helper.absoluteVec(new Vec3(3.5, 2.0, 1.5));
		p.moveTo(at.x, at.y, at.z, 0f, 0f); // facing +z
		p.setYHeadRot(0f);
		p.yBodyRot = 0f;
		SaiyanKit.resetForTest(p);
		SaiyanKit.overrideForTest(p, form, ki);
		return p;
	}

	/** A still, armourless 300-health zombie {@code dx}/{@code dz} blocks from the Saiyan, facing {@code yaw}. */
	private static Zombie target(GameTestHelper helper, double dx, double dz, float yaw) {
		Zombie z = helper.spawn(EntityType.ZOMBIE, new BlockPos(3, 2, 3));
		z.setNoAi(true);
		z.getAttribute(Attributes.MAX_HEALTH).setBaseValue(299);
		z.getAttribute(Attributes.ARMOR).setBaseValue(0);
		z.setHealth(299);
		z.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, 100000, 0, false, false)); // daylight must not burn it
		Vec3 at = helper.absoluteVec(new Vec3(3.5 + dx, 2.0, 1.5 + dz));
		z.moveTo(at.x, at.y, at.z, yaw, 0f);
		z.setYHeadRot(yaw);
		z.yHeadRotO = yaw;
		z.yBodyRot = yaw;
		return z;
	}

	private static Zombie target(GameTestHelper helper, double dz) {
		return target(helper, 0, dz, 180f);
	}

	private static float taken(Zombie z) {
		return 299f - z.getHealth();
	}

	private static boolean near(float a, float b) {
		return Math.abs(a - b) < 0.05f;
	}

	private static String f(float v) {
		return String.format(java.util.Locale.ROOT, "%.2f", v);
	}

	// ---------------------------------------------------------------- R: Punch Combo

	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void comboIsFourHitsThenAKnockbackFinisher(GameTestHelper helper) {
		ServerPlayer p = saiyan(helper, SaiyanForm.BASE, 100f);
		Zombie z = target(helper, 2.0);
		int gap = SaiyanKitConfig.COMBO_STEP_TICKS + 1;
		for (int i = 0; i < 5; i++) {
			int k = i;
			helper.runAfterDelay(1 + i * gap, () -> {
				SaiyanKit.press(p, SaiyanKit.COMBO, true);
				float want = k < 4 ? SaiyanKitConfig.COMBO_HIT_DAMAGE * (k + 1) : SaiyanKitConfig.COMBO_HIT_DAMAGE * 4 + SaiyanKitConfig.COMBO_FINISH_DAMAGE;
				helper.assertTrue(near(taken(z), want), "after hit " + (k + 1) + ": " + f(taken(z)) + ", not " + want);
				if (k == 4) {
					Vec3 v = z.getDeltaMovement();
					helper.assertTrue(v.z > 1.0 && v.y > 0.1, "the 5th hit knocks back and up: " + v);
					helper.assertTrue(SaiyanKit.comboStep(p) == 0, "the combo starts over");
					helper.assertTrue(SaiyanKit.cooldownRemaining(p, SaiyanKit.COMBO) > 0, "a short recovery after the finisher");
					helper.succeed();
				} else {
					helper.assertTrue(z.getDeltaMovement().z < 0.9, "only a melee nudge before the finisher: " + z.getDeltaMovement());
				}
			});
		}
	}

	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void comboStartsOverAfterAPause(GameTestHelper helper) {
		ServerPlayer p = saiyan(helper, SaiyanForm.BASE, 100f);
		target(helper, 2.0);
		SaiyanKit.press(p, SaiyanKit.COMBO, true);
		helper.runAfterDelay(SaiyanKitConfig.COMBO_STEP_TICKS + 1, () -> SaiyanKit.press(p, SaiyanKit.COMBO, true));
		helper.runAfterDelay(SaiyanKitConfig.COMBO_STEP_TICKS + 2, () -> helper.assertTrue(SaiyanKit.comboStep(p) == 2, "two hits in"));
		helper.runAfterDelay(SaiyanKitConfig.COMBO_STEP_TICKS + 3 + SaiyanKitConfig.COMBO_RESET_TICKS, () -> {
			SaiyanKit.press(p, SaiyanKit.COMBO, true);
			helper.assertTrue(SaiyanKit.comboStep(p) == 1, "after 1.5 s idle the next press is hit 1 again: " + SaiyanKit.comboStep(p));
			helper.succeed();
		});
	}

	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void leftClickFeedsTheComboAndTooFastIsIgnored(GameTestHelper helper) {
		ServerPlayer p = saiyan(helper, SaiyanForm.BASE, 100f);
		Zombie z = target(helper, 2.0);
		SaiyanKit.attack(p);
		SaiyanKit.press(p, SaiyanKit.COMBO, true); // same tick: too fast
		helper.assertTrue(near(taken(z), SaiyanKitConfig.COMBO_HIT_DAMAGE), "one hit of 8: " + f(taken(z)));
		helper.assertTrue(SaiyanKit.comboStep(p) == 1, "one step");
		helper.succeed();
	}

	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void kitDealsBaseDamageAndLeavesTheMultiplierToTheCore(GameTestHelper helper) {
		// the core's SaiyanDamage scales every Saiyan hit by Saiyan.damageMult; the kit must not do it a second time
		ServerPlayer p = saiyan(helper, SaiyanForm.SSJ, 100f);
		Zombie z = target(helper, 2.0);
		SaiyanKit.press(p, SaiyanKit.COMBO, true);
		helper.assertTrue(SaiyanKit.damageMult(p) == 1f, "no multiplier of its own");
		helper.assertTrue(near(taken(z), SaiyanKitConfig.COMBO_HIT_DAMAGE), "base 8 from the kit (no power on a mock player, so no core bonus): " + f(taken(z)));
		helper.succeed();
	}

	// ---------------------------------------------------------------- Shift+R: Rising Dragon Kick

	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void risingDragonKickLaunchesThenSlams(GameTestHelper helper) {
		ServerPlayer p = saiyan(helper, SaiyanForm.BASE, 100f);
		Zombie z = target(helper, 2.0);
		double[] minVy = { 0 };
		helper.onEachTick(() -> {
			SaiyanKit.tick(p);
			minVy[0] = Math.min(minVy[0], z.getDeltaMovement().y);
		});
		SaiyanKit.press(p, SaiyanKit.MARTIAL, true);
		helper.assertTrue(near(taken(z), SaiyanKitConfig.MARTIAL_LAUNCH_DAMAGE), "the flip kick: " + f(taken(z)));
		helper.assertTrue(z.getDeltaMovement().y > 0.8, "launched up: " + z.getDeltaMovement());
		helper.assertTrue(near(SaiyanKit.kiOf(p), 100f - SaiyanKitConfig.MARTIAL_KI), "15 Ki: " + SaiyanKit.kiOf(p));
		helper.assertTrue(SaiyanKit.cooldownRemaining(p, SaiyanKit.MARTIAL) > SaiyanKitConfig.MARTIAL_COOLDOWN - 3, "6 s cooldown");
		helper.assertTrue(SaiyanKit.busy(p), "busy while it plays");
		helper.runAfterDelay(SaiyanKitConfig.MARTIAL_AXE_AT + 3, () -> {
			float want = SaiyanKitConfig.MARTIAL_LAUNCH_DAMAGE + SaiyanKitConfig.MARTIAL_AXE_DAMAGE;
			helper.assertTrue(near(taken(z), want), "the axe kick: " + f(taken(z)) + ", not " + want);
			helper.assertTrue(minVy[0] < -1.0, "slammed down: " + minVy[0]);
			helper.succeed();
		});
	}

	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void risingDragonKickWhiffCostsNothing(GameTestHelper helper) {
		ServerPlayer p = saiyan(helper, SaiyanForm.BASE, 100f);
		SaiyanKit.press(p, SaiyanKit.MARTIAL, true);
		helper.assertTrue(SaiyanKit.running(p) == null, "no one to kick");
		helper.assertTrue(near(SaiyanKit.kiOf(p), 100f), "no Ki");
		int cd = SaiyanKit.cooldownRemaining(p, SaiyanKit.MARTIAL);
		helper.assertTrue(cd > 0 && cd <= SaiyanKitConfig.MARTIAL_WHIFF_COOLDOWN, "only a short cooldown: " + cd);
		helper.succeed();
	}

	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void notEnoughKiDoesNothing(GameTestHelper helper) {
		ServerPlayer p = saiyan(helper, SaiyanForm.BASE, 3f);
		Zombie z = target(helper, 2.0);
		SaiyanKit.press(p, SaiyanKit.MARTIAL, true);
		SaiyanKit.press(p, SaiyanKit.AOE, true);
		helper.assertTrue(taken(z) == 0f && SaiyanKit.running(p) == null, "3 Ki is not enough");
		helper.assertTrue(SaiyanKit.cooldownRemaining(p, SaiyanKit.MARTIAL) == 0 && SaiyanKit.cooldownRemaining(p, SaiyanKit.AOE) == 0,
				"and nothing goes on cooldown");
		helper.succeed();
	}

	// ---------------------------------------------------------------- G: Ki Blast Volley

	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = 120)
	public void kiBlastVolleyFiresWhileHeld(GameTestHelper helper) {
		ServerPlayer p = saiyan(helper, SaiyanForm.BASE, 100f);
		Zombie z = target(helper, 4.0);
		helper.onEachTick(() -> SaiyanKit.tick(p));
		SaiyanKit.press(p, SaiyanKit.KI_BLAST, true);
		helper.assertTrue(near(SaiyanKit.kiOf(p), 100f - SaiyanKitConfig.BLAST_KI), "the first sphere on the press, 2 Ki");
		helper.assertTrue(!SaiyanKit.busy(p), "he can move while firing");
		int hold = SaiyanKitConfig.BLAST_EVERY * 4 + 1;
		helper.runAfterDelay(hold, () -> SaiyanKit.press(p, SaiyanKit.KI_BLAST, false));
		helper.runAfterDelay(hold + 20, () -> {
			int shots = Math.round((100f - SaiyanKit.kiOf(p)) / SaiyanKitConfig.BLAST_KI);
			helper.assertTrue(shots >= 4 && shots <= 6, "about 4 a second: " + shots + " in " + hold + " ticks");
			helper.assertTrue(SaiyanKit.running(p) == null, "letting go stops it");
			helper.assertTrue(SaiyanKit.shotsInFlight() == 0, "every sphere has landed");
			helper.assertTrue(near(taken(z), SaiyanKitConfig.BLAST_DAMAGE * shots), "5 a sphere: " + f(taken(z)) + " from " + shots);
			helper.succeed();
		});
	}

	// ---------------------------------------------------------------- Shift+G: Kamehameha

	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = 200)
	public void kamehamehaFullChargeIs40DamageFor60Ki(GameTestHelper helper) {
		ServerPlayer p = saiyan(helper, SaiyanForm.BASE, 100f);
		Zombie z = target(helper, 4.5);
		helper.onEachTick(() -> SaiyanKit.tick(p));
		SaiyanKit.press(p, SaiyanKit.BEAM, true);
		helper.assertTrue(near(SaiyanKit.kiOf(p), 100f - SaiyanKitConfig.BEAM_START_KI), "25 Ki to start");
		helper.assertTrue(SaiyanKit.busy(p), "busy while charging");
		int release = SaiyanKitConfig.BEAM_FULL_CHARGE + 10;
		helper.runAfterDelay(release - 1, () -> {
			helper.assertTrue(taken(z) == 0f, "nothing while charging");
			helper.assertTrue(SaiyanKit.charge(p) >= 0.999f, "fully charged: " + SaiyanKit.charge(p));
			helper.assertTrue(Math.abs(SaiyanKit.beamKiPaid(p) - SaiyanKitConfig.BEAM_MAX_KI) < 0.1f, "60 Ki in all: " + SaiyanKit.beamKiPaid(p));
			SaiyanKit.press(p, SaiyanKit.BEAM, false);
		});
		helper.runAfterDelay(release + SaiyanKitConfig.BEAM_FIRE_TICKS + 6, () -> {
			helper.assertTrue(SaiyanKit.running(p) == null, "it ends");
			helper.assertTrue(Math.abs(taken(z) - SaiyanKitConfig.BEAM_MAX_DAMAGE) < 0.1f, "40 damage: " + f(taken(z)));
			helper.assertTrue(Math.abs(SaiyanKit.kiOf(p) - (100f - SaiyanKitConfig.BEAM_MAX_KI)) < 0.1f, "60 Ki spent: " + SaiyanKit.kiOf(p));
			helper.assertTrue(SaiyanKit.cooldownRemaining(p, SaiyanKit.BEAM) > SaiyanKitConfig.BEAM_COOLDOWN - 10, "12 s cooldown");
			helper.succeed();
		});
	}

	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = 160)
	public void kamehamehaTapFiresAWeakBeam(GameTestHelper helper) {
		ServerPlayer p = saiyan(helper, SaiyanForm.BASE, 100f);
		Zombie z = target(helper, 4.5);
		float[] charge = { 0f };
		helper.onEachTick(() -> {
			SaiyanKit.tick(p);
			charge[0] = Math.max(charge[0], SaiyanKit.charge(p));
		});
		SaiyanKit.press(p, SaiyanKit.BEAM, true);
		helper.runAfterDelay(1, () -> SaiyanKit.press(p, SaiyanKit.BEAM, false));
		helper.runAfterDelay(SaiyanKitConfig.BEAM_MIN_CHARGE + SaiyanKitConfig.BEAM_FIRE_TICKS + 8, () -> {
			float c = charge[0];
			helper.assertTrue(c > 0.15f && c < 0.3f, "fired at about the shortest charge: " + c);
			float want = SaiyanKit.beamDamage(c);
			helper.assertTrue(want >= SaiyanKitConfig.BEAM_MIN_DAMAGE && Math.abs(taken(z) - want) < 0.1f, "damage by charge: " + f(taken(z)) + ", not " + want);
			float ki = SaiyanKitConfig.BEAM_START_KI + c * (SaiyanKitConfig.BEAM_MAX_KI - SaiyanKitConfig.BEAM_START_KI);
			helper.assertTrue(Math.abs(SaiyanKit.kiOf(p) - (100f - ki)) < 0.1f, "Ki by charge: " + SaiyanKit.kiOf(p));
			helper.succeed();
		});
	}

	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = 200)
	public void kamehamehaJoinsABeamClash(GameTestHelper helper) {
		ServerPlayer p = saiyan(helper, SaiyanForm.BASE, 100f);
		Zombie z = target(helper, 4.5);
		boolean[] done = { false };
		helper.onEachTick(() -> {
			SaiyanKit.tick(p);
			BeamClash.report(z, z.getEyePosition(), p.getEyePosition().subtract(z.getEyePosition()), 32, 1f, 0x00FF00);
			if (!done[0] && BeamClash.inClash(p)) {
				done[0] = true;
				helper.succeed();
			}
		});
		SaiyanKit.press(p, SaiyanKit.BEAM, true);
		helper.runAfterDelay(SaiyanKitConfig.BEAM_MIN_CHARGE + 1, () -> SaiyanKit.press(p, SaiyanKit.BEAM, false));
	}

	// ---------------------------------------------------------------- Z: Meteor Smash

	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = 80)
	public void meteorSmashAppearsBehindAndHits22(GameTestHelper helper) {
		ServerPlayer p = saiyan(helper, SaiyanForm.BASE, 100f);
		Zombie z = target(helper, 4.5);
		double[] minVy = { 0 };
		helper.onEachTick(() -> {
			SaiyanKit.tick(p);
			minVy[0] = Math.min(minVy[0], z.getDeltaMovement().y);
		});
		SaiyanKit.press(p, SaiyanKit.STRONG, true);
		helper.assertTrue(near(SaiyanKit.kiOf(p), 100f - SaiyanKitConfig.SMASH_KI), "25 Ki");
		helper.assertTrue(SaiyanKit.cooldownRemaining(p, SaiyanKit.STRONG) > SaiyanKitConfig.SMASH_COOLDOWN - 3, "10 s cooldown");
		helper.assertTrue(p.distanceTo(z) < 3.0, "he reappears by the target: " + p.distanceTo(z));
		helper.assertTrue(p.getZ() > z.getZ() - 0.1 || p.getY() > z.getY() + 1.5, "behind or above it");
		helper.runAfterDelay(SaiyanKitConfig.SMASH_HIT_AT + 3, () -> {
			helper.assertTrue(near(taken(z), SaiyanKitConfig.SMASH_DAMAGE), "22 damage: " + f(taken(z)));
			helper.assertTrue(minVy[0] < -1.0, "driven down: " + minVy[0]);
			helper.succeed();
		});
	}

	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void meteorSmashWithNoTargetCostsNothing(GameTestHelper helper) {
		ServerPlayer p = saiyan(helper, SaiyanForm.BASE, 100f);
		Vec3 before = p.position();
		SaiyanKit.press(p, SaiyanKit.STRONG, true);
		helper.assertTrue(near(SaiyanKit.kiOf(p), 100f) && p.position().distanceTo(before) < 0.01, "no Ki, no vanish");
		helper.assertTrue(SaiyanKit.cooldownRemaining(p, SaiyanKit.STRONG) <= SaiyanKitConfig.SMASH_WHIFF_COOLDOWN, "a short cooldown");
		helper.succeed();
	}

	// ---------------------------------------------------------------- X: Ki Dash / Shift+X: Dragon Rush

	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void kiDashBurstsAlongTheLook(GameTestHelper helper) {
		ServerPlayer p = saiyan(helper, SaiyanForm.BASE, 100f);
		helper.onEachTick(() -> SaiyanKit.tick(p));
		SaiyanKit.press(p, SaiyanKit.DASH, true);
		helper.assertTrue(near(SaiyanKit.kiOf(p), 100f - SaiyanKitConfig.DASH_KI), "5 Ki");
		helper.assertTrue(SaiyanKit.cooldownRemaining(p, SaiyanKit.DASH) > SaiyanKitConfig.DASH_COOLDOWN - 3, "2 s cooldown");
		Vec3 v = p.getDeltaMovement();
		helper.assertTrue(v.z > SaiyanKitConfig.DASH_SPEED * 0.9 && Math.abs(v.x) < 0.1, "a fast burst forward: " + v);
		helper.assertTrue(SaiyanKit.fallSafe(p), "no fall damage");
		double blocks = SaiyanKitConfig.DASH_SPEED * SaiyanKitConfig.DASH_TICKS;
		helper.assertTrue(blocks >= 9.5 && blocks <= 10.5, "about 10 blocks: " + blocks);
		helper.runAfterDelay(SaiyanKitConfig.DASH_TICKS + 3, () -> {
			helper.assertTrue(SaiyanKit.running(p) == null, "it ends");
			helper.assertTrue(SaiyanKit.fallSafe(p), "still safe from the fall after");
			helper.succeed();
		});
	}

	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = 60)
	public void dragonRushHitsEveryoneOnThePathThenKnocksThemBack(GameTestHelper helper) {
		ServerPlayer p = saiyan(helper, SaiyanForm.BASE, 100f);
		Zombie a = target(helper, 2.0);
		Zombie b = target(helper, 0, 5.0, 180f);
		Zombie off = target(helper, 3.0, 1.0, 180f);
		double[] maxVz = { 0, 0 };
		helper.onEachTick(() -> {
			SaiyanKit.tick(p);
			maxVz[0] = Math.max(maxVz[0], a.getDeltaMovement().z);
			maxVz[1] = Math.max(maxVz[1], b.getDeltaMovement().z);
		});
		SaiyanKit.press(p, SaiyanKit.RUSH, true);
		helper.assertTrue(near(SaiyanKit.kiOf(p), 100f - SaiyanKitConfig.RUSH_KI), "15 Ki");
		helper.assertTrue(SaiyanKit.cooldownRemaining(p, SaiyanKit.RUSH) > SaiyanKitConfig.RUSH_COOLDOWN - 3, "6 s cooldown");
		helper.runAfterDelay(SaiyanKitConfig.RUSH_TICKS + 3, () -> {
			helper.assertTrue(near(taken(a), SaiyanKitConfig.RUSH_DAMAGE) && near(taken(b), SaiyanKitConfig.RUSH_DAMAGE),
					"6 to each one passed: " + f(taken(a)) + ", " + f(taken(b)));
			helper.assertTrue(taken(off) == 0f, "not the one off to the side: " + f(taken(off)));
			helper.assertTrue(maxVz[0] > 1.0 && maxVz[1] > 1.0, "the finisher knocks them on: " + maxVz[0] + ", " + maxVz[1]);
			helper.assertTrue(SaiyanKit.running(p) == null, "it ends");
			helper.succeed();
		});
	}

	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = 60)
	public void aRealSaiyansHitsCountAsLandedThroughTheCoresReissue(GameTestHelper helper) {
		// the core's SaiyanDamage cancels and re-issues every hit a Saiyan deals; the kit must still see it land
		ServerPlayer p = saiyan(helper, SaiyanForm.BASE, 100f);
		com.projecthero.mod.saiyan.Saiyan.grant(p);
		Zombie z = target(helper, 2.0);
		double[] maxVz = { 0 };
		helper.onEachTick(() -> {
			SaiyanKit.tick(p);
			maxVz[0] = Math.max(maxVz[0], z.getDeltaMovement().z);
		});
		SaiyanKit.press(p, SaiyanKit.RUSH, true);
		helper.runAfterDelay(SaiyanKitConfig.RUSH_TICKS + 3, () -> {
			helper.assertTrue(taken(z) >= SaiyanKitConfig.RUSH_DAMAGE - 0.05f, "the rush hurt it: " + f(taken(z)));
			helper.assertTrue(maxVz[0] > 1.0, "and it counted as struck, so the finisher knocked it on: " + maxVz[0]);
			helper.succeed();
		});
	}

	// ---------------------------------------------------------------- C: Explosive Wave / Shift+C: Ki Rain

	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void explosiveWaveHitsAllAroundWithinSeven(GameTestHelper helper) {
		ServerPlayer p = saiyan(helper, SaiyanForm.BASE, 100f);
		Zombie front = target(helper, 3.0);
		Zombie side = target(helper, 3.0, 0.5, 0f);
		Zombie far = target(helper, 0, 0, 180f);
		far.moveTo(far.getX(), far.getY() + 9.0, far.getZ()); // 9 blocks over his head (the box is too small to go sideways)
		SaiyanKit.press(p, SaiyanKit.AOE, true);
		helper.assertTrue(near(taken(front), SaiyanKitConfig.WAVE_DAMAGE) && near(taken(side), SaiyanKitConfig.WAVE_DAMAGE),
				"14 all round: " + f(taken(front)) + ", " + f(taken(side)));
		helper.assertTrue(taken(far) == 0f, "nothing past 7 blocks");
		helper.assertTrue(front.getDeltaMovement().z > 0.5 && side.getDeltaMovement().x > 0.5, "thrown away from him");
		helper.assertTrue(near(SaiyanKit.kiOf(p), 100f - SaiyanKitConfig.WAVE_KI), "20 Ki");
		helper.assertTrue(SaiyanKit.cooldownRemaining(p, SaiyanKit.AOE) > SaiyanKitConfig.WAVE_COOLDOWN - 3, "10 s cooldown");
		helper.succeed();
	}

	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = 120)
	public void kiRainDropsTenBlastsOnTheArea(GameTestHelper helper) {
		ServerPlayer p = saiyan(helper, SaiyanForm.BASE, 100f);
		p.setXRot(55f); // looking at the floor just ahead
		Zombie z = target(helper, 3.0);
		helper.onEachTick(() -> SaiyanKit.tick(p));
		SaiyanKit.press(p, SaiyanKit.AOE2, true);
		helper.assertTrue(near(SaiyanKit.kiOf(p), 100f - SaiyanKitConfig.RAIN_KI), "30 Ki");
		helper.assertTrue(SaiyanKit.cooldownRemaining(p, SaiyanKit.AOE2) > SaiyanKitConfig.RAIN_COOLDOWN - 3, "15 s cooldown");
		int done = SaiyanKitConfig.RAIN_FIRE_AT + SaiyanKitConfig.RAIN_EVERY * SaiyanKitConfig.RAIN_SHOTS + 12;
		helper.runAfterDelay(done, () -> {
			float want = SaiyanKitConfig.RAIN_DAMAGE * SaiyanKitConfig.RAIN_SHOTS;
			helper.assertTrue(near(taken(z), want), "ten blasts of 4: " + f(taken(z)) + ", not " + want);
			helper.assertTrue(SaiyanKit.running(p) == null && SaiyanKit.shotsInFlight() == 0, "it ends");
			helper.succeed();
		});
	}

	// ---------------------------------------------------------------- Shift+V: Solar Flare

	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void solarFlareBlindsOnlyThoseFacingHim(GameTestHelper helper) {
		ServerPlayer p = saiyan(helper, SaiyanForm.BASE, 100f);
		Zombie facing = target(helper, 3.0); // yaw 180: looking back at him
		Zombie away = target(helper, 3.0, 1.0, -90f); // looking off to +x
		facing.setTarget(p);
		SaiyanKit.press(p, SaiyanKit.UTILITY2, true);
		helper.assertTrue(facing.hasEffect(MobEffects.BLINDNESS) && facing.hasEffect(MobEffects.MOVEMENT_SLOWDOWN), "the one facing him is blinded and slowed");
		helper.assertTrue(facing.getEffect(MobEffects.BLINDNESS).getDuration() >= SaiyanKitConfig.FLARE_TICKS - 2, "for 4 s");
		helper.assertTrue(facing.getTarget() == null && SaiyanKit.flared(facing), "and loses its target");
		helper.assertTrue(!away.hasEffect(MobEffects.BLINDNESS), "the one looking away is not");
		helper.assertTrue(near(SaiyanKit.kiOf(p), 100f - SaiyanKitConfig.FLARE_KI), "15 Ki");
		helper.assertTrue(SaiyanKit.cooldownRemaining(p, SaiyanKit.UTILITY2) > SaiyanKitConfig.FLARE_COOLDOWN - 3, "20 s cooldown");
		helper.runAfterDelay(5, () -> {
			facing.setTarget(p);
			helper.runAfterDelay(2, () -> {
				helper.assertTrue(facing.getTarget() == null, "it can't take a target back while blinded");
				helper.succeed();
			});
		});
	}

	// ---------------------------------------------------------------- the contract

	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = 60)
	public void oneMoveAtATimeAndTheContract(GameTestHelper helper) {
		ServerPlayer p = saiyan(helper, SaiyanForm.BASE, 100f);
		helper.onEachTick(() -> SaiyanKit.tick(p));
		SaiyanKit.press(p, SaiyanKit.UTILITY, true);
		helper.assertTrue(SaiyanKit.running(p) == null && near(SaiyanKit.kiOf(p), 100f), "V (utility) is the core's Ki charge: a no-op here");
		SaiyanKit.press(p, SaiyanKit.BEAM, true);
		SaiyanKit.press(p, SaiyanKit.DASH, true);
		helper.assertTrue(SaiyanKit.BEAM.equals(SaiyanKit.running(p)) && SaiyanKit.busy(p), "the charge keeps going and holds him");
		helper.assertTrue(SaiyanKit.cooldownRemaining(p, SaiyanKit.DASH) == 0, "the dash never started");
		for (String id : SaiyanKit.IDS) {
			helper.assertTrue(SaiyanKit.maxCooldown(id) > 0, "a max cooldown for " + id);
		}
		SaiyanKit.resetForTest(p);
		helper.assertTrue(SaiyanKit.running(p) == null && !SaiyanKit.busy(p), "the reset drops his move");
		helper.succeed();
	}
}
