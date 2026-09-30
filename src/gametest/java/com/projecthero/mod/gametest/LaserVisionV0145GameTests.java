package com.projecthero.mod.gametest;

import com.projecthero.mod.hero.Ability;
import com.projecthero.mod.hero.AbilityHandlers;
import com.projecthero.mod.hero.AbilityRouter;
import com.projecthero.mod.hero.AbilitySlot;
import com.projecthero.mod.hero.ExperimentalPowers;
import com.projecthero.mod.hero.Power;
import com.projecthero.mod.hero.Powers;
import com.projecthero.mod.hero.power.p02.LaserVisionHandlers;
import com.projecthero.mod.hero.visual.MutationMeters;
import com.projecthero.mod.hero.visual.MutationVisuals;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/**
 * v0.14.5 Laser Vision rework: six keys, a 0-100 heat gauge with per-move costs, Maximum Output's heat rules,
 * Ignite, the slow-fall beam and no eye effects. Mock players are not reliably ticked by the server, so the tests
 * drive {@link ExperimentalPowers#serverTick} themselves.
 */
public class LaserVisionV0145GameTests implements FabricGameTest {
	private static final String K = LaserVisionHandlers.KEY;

	// ---------------- helpers ----------------

	/** A survival mock player holding Laser Vision in the middle of the cage, on a stone floor, facing +Z. */
	private static ServerPlayer hero(GameTestHelper helper) {
		ServerPlayer p = helper.makeMockServerPlayerInLevel();
		p.setGameMode(GameType.SURVIVAL);
		Vec3 at = helper.absoluteVec(new Vec3(2.5, 2.0, 2.5));
		BlockPos base = BlockPos.containing(at);
		for (BlockPos pos : BlockPos.betweenClosed(base.offset(-1, 0, -1), base.offset(3, 3, 3))) {
			helper.getLevel().setBlock(pos, Blocks.AIR.defaultBlockState(), 2);
		}
		for (BlockPos pos : BlockPos.betweenClosed(base.offset(-1, -1, -1), base.offset(3, -1, 3))) {
			helper.getLevel().setBlock(pos, Blocks.STONE.defaultBlockState(), 2);
		}
		p.moveTo(at.x, at.y, at.z, 0.0f, 0.0f);
		p.setYRot(0);
		p.setXRot(0);
		p.setYHeadRot(0);
		Power power = Powers.byKey(K);
		ExperimentalPowers.grant(p, power);
		ExperimentalPowers.setActive(p, power);
		p.setOnGround(true);
		return p;
	}

	private static float res(ServerPlayer p, String name) {
		return ExperimentalPowers.getResource(p, Powers.byKey(K), name);
	}

	private static void setRes(ServerPlayer p, String name, float v) {
		ExperimentalPowers.setResource(p, Powers.byKey(K), name, v, 1e9f);
	}

	private static boolean onCooldown(ServerPlayer p, AbilitySlot slot) {
		Power power = Powers.byKey(K);
		return !ExperimentalPowers.cooldownReady(p, power, power.ability(slot));
	}

	private static void tick(ServerPlayer p, int n) {
		for (int i = 0; i < n; i++) {
			ExperimentalPowers.serverTick(p);
		}
	}

	private static void assertHeat(GameTestHelper helper, ServerPlayer p, float expected, String what) {
		float h = res(p, "heat");
		helper.assertTrue(Math.abs(h - expected) < 0.01f, what + ": expected heat " + expected + ", got " + h);
	}

	// ---------------- tests ----------------

	@GameTest(template = EMPTY_STRUCTURE)
	public void powerIsRegistered(GameTestHelper helper) {
		helper.assertTrue(Powers.byKey(LaserVisionHandlers.KEY) != null, "Laser Vision is registered");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void sixAbilitiesNoUtilityKeys(GameTestHelper helper) {
		Power power = Powers.byKey(K);
		helper.assertTrue(power.abilities().size() == 6, "Laser Vision has exactly six abilities (no H / N)");
		String[] ids = { "heat_vision", "sweeping_arc", "recoil_blast", "maximum_output", "ignite", "thermal_vision" };
		for (int i = 0; i < ids.length; i++) {
			Ability a = power.ability(AbilitySlot.byNumber(i + 1));
			helper.assertTrue(a != null && ids[i].equals(a.id()), "slot " + (i + 1) + " should be " + ids[i]);
			helper.assertTrue(AbilityHandlers.has(power, a), ids[i] + " has a handler");
		}
		helper.assertTrue(power.passiveKeys().size() == 1, "only the heat passive is left");
		helper.assertTrue(LaserVisionHandlers.MAX_HEAT == 100f, "heat is out of 100");
		helper.assertFalse(MutationVisuals.registeredFlags().contains("p02.eyes"), "no glowing eyes any more");
		MutationMeters.Spec heat = MutationMeters.get(K, "heat");
		helper.assertTrue(heat != null && heat.style() == MutationMeters.Style.HAIRLINE && heat.always() && heat.showValue()
				&& heat.textColor() != 0, "heat is an always-on red Hairline with its percentage");
		MutationMeters.Spec charge = MutationMeters.get(K, "mo_charge");
		helper.assertTrue(charge != null && charge.above(), "Maximum Output's charge-up sits above the keys");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE, batch = "lv0145_costs")
	public void everyMoveCostsItsHeat(GameTestHelper helper) {
		ServerPlayer p = hero(helper);
		AbilityRouter.handleInput(p, 2, true); // G Sweeping Arc
		assertHeat(helper, p, 10f, "Sweeping Arc");
		helper.assertTrue(onCooldown(p, AbilitySlot.SLOT_2), "Sweeping Arc cools down");
		AbilityRouter.handleInput(p, 3, true); // X Recoil Blast
		assertHeat(helper, p, 15f, "Recoil Blast");
		p.setXRot(90f);
		AbilityRouter.handleInput(p, 5, true); // V Ignite
		assertHeat(helper, p, 17f, "Ignite");
		p.setXRot(0f);
		p.setShiftKeyDown(true);
		AbilityRouter.handleInput(p, 1, true); // Shift+R Piercing Blast
		AbilityRouter.handleInput(p, 1, false);
		p.setShiftKeyDown(false);
		assertHeat(helper, p, 27f, "Piercing Blast");
		helper.assertTrue(onCooldown(p, AbilitySlot.SLOT_1), "Piercing Blast's cooldown shows on R");
		helper.assertTrue(res(p, "beaming") == 0f, "Shift+R is a one-shot, not the held beam");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE, batch = "lv0145_beam")
	public void heatVisionBuildsOneHeatPerSecond(GameTestHelper helper) {
		ServerPlayer p = hero(helper);
		AbilityRouter.handleInput(p, 1, true);
		helper.assertTrue(LaserVisionHandlers.firing(p), "holding R fires the beam");
		helper.assertTrue(LaserVisionHandlers.ANIM_BEAM.equals(MutationVisuals.anim(p)), "the beam animation (drives the model) plays");
		tick(p, 20);
		assertHeat(helper, p, 1f, "a second of Heat Vision");
		AbilityRouter.handleInput(p, 1, false);
		helper.assertFalse(LaserVisionHandlers.firing(p), "release stops the beam");
		helper.assertTrue(MutationVisuals.anim(p).isEmpty(), "and its model");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE, batch = "lv0145_fall")
	public void beamingDownInMidAirSlowsTheFall(GameTestHelper helper) {
		ServerPlayer p = hero(helper);
		p.setOnGround(false);
		p.setXRot(70f);
		p.setDeltaMovement(0, -1.0, 0);
		AbilityRouter.handleInput(p, 1, true);
		tick(p, 1);
		helper.assertTrue(p.hasEffect(MobEffects.SLOW_FALLING), "a downward beam in mid-air holds you up");
		helper.assertTrue(p.getDeltaMovement().y >= -0.21, "the fall is clamped to a drift");
		AbilityRouter.handleInput(p, 1, false);
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE, batch = "lv0145_max")
	public void maximumOutputHeatRules(GameTestHelper helper) {
		ServerPlayer p = hero(helper);
		setRes(p, "heat", 60f);
		AbilityRouter.handleInput(p, 4, true);
		helper.assertFalse(onCooldown(p, AbilitySlot.SLOT_4), "refused above 50% heat");
		helper.assertTrue(res(p, "mo_charging") == 0f, "nothing charges");

		setRes(p, "heat", 40f);
		AbilityRouter.handleInput(p, 4, true);
		AbilityRouter.handleInput(p, 4, false);
		helper.assertTrue(onCooldown(p, AbilitySlot.SLOT_4), "at 40% it starts (and its cooldown runs)");
		tick(p, 10);
		helper.assertTrue(res(p, "mo_charge") > 0f, "the charge-up bar fills");
		tick(p, LaserVisionHandlers.MAX_OUTPUT_CHARGE_TICKS);
		helper.assertTrue(LaserVisionHandlers.maxOutputRunning(p), "the powered-up beam fires after the charge-up");
		assertHeat(helper, p, 100f, "Maximum Output pins heat at max");
		helper.assertTrue(LaserVisionHandlers.ANIM_MAX.equals(MutationVisuals.anim(p)), "the Maximum Output model plays");
		helper.assertFalse(LaserVisionHandlers.overheated(p), "no overheat while it runs");
		AbilityRouter.handleInput(p, 3, true); // X during the beam: heat stays pinned, no overheat
		helper.assertTrue(LaserVisionHandlers.maxOutputRunning(p) && !LaserVisionHandlers.overheated(p), "other moves do not cut it");
		tick(p, LaserVisionHandlers.MAX_OUTPUT_TICKS);
		helper.assertFalse(LaserVisionHandlers.maxOutputRunning(p), "it lasts 10 s");
		helper.assertTrue(LaserVisionHandlers.overheated(p), "and leaves you overheated");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE, batch = "lv0145_overheat")
	public void aFullGaugeLocksTheBeamsOut(GameTestHelper helper) {
		ServerPlayer p = hero(helper);
		setRes(p, "heat", 96f);
		AbilityRouter.handleInput(p, 3, true); // X +5 -> 100
		helper.assertTrue(LaserVisionHandlers.overheated(p), "a full gauge overheats");
		AbilityRouter.handleInput(p, 2, true);
		helper.assertFalse(onCooldown(p, AbilitySlot.SLOT_2), "nothing fires while overheated");
		tick(p, LaserVisionHandlers.OVERHEAT_TICKS + 1);
		helper.assertFalse(LaserVisionHandlers.overheated(p), "the lockout ends");
		helper.assertTrue(res(p, "heat") < 100f, "and it vents");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE, batch = "lv0145_ignite")
	public void igniteSetsTheFloorOnFire(GameTestHelper helper) {
		ServerPlayer p = hero(helper);
		p.setXRot(90f); // straight down at the stone floor
		BlockPos above = p.blockPosition();
		helper.getLevel().setBlock(above, Blocks.AIR.defaultBlockState(), 2);
		AbilityRouter.handleInput(p, 5, true);
		helper.assertTrue(helper.getLevel().getBlockState(above).is(Blocks.FIRE), "Ignite lights the block it hits, like flint and steel");
		helper.assertTrue(onCooldown(p, AbilitySlot.SLOT_5), "then a short cooldown");
		helper.getLevel().setBlock(above, Blocks.AIR.defaultBlockState(), 2);
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE, batch = "lv0145_eyes")
	public void noVisionEffectsAnyMore(GameTestHelper helper) {
		ServerPlayer p = hero(helper);
		tick(p, 2);
		helper.assertFalse(p.hasEffect(MobEffects.NIGHT_VISION), "no permanent night vision");
		p.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 100, 0));
		tick(p, 2);
		helper.assertTrue(p.hasEffect(MobEffects.BLINDNESS), "no blindness immunity");
		// the pre-v0.14.5 hidden infinite Night Vision is taken off; a real potion is not
		p.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, MobEffectInstance.INFINITE_DURATION, 0, false, false, false));
		LaserVisionHandlers.dropLegacyNightVision(p);
		helper.assertFalse(p.hasEffect(MobEffects.NIGHT_VISION), "the legacy night vision is removed");
		p.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, 600, 0));
		LaserVisionHandlers.dropLegacyNightVision(p);
		helper.assertTrue(p.hasEffect(MobEffects.NIGHT_VISION), "a potion's night vision stays");
		helper.succeed();
	}
}
