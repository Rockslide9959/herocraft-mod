package com.projecthero.mod.gametest;

import com.projecthero.mod.hero.AbilityRouter;
import com.projecthero.mod.hero.AbilitySlot;
import com.projecthero.mod.hero.ExperimentalPowers;
import com.projecthero.mod.hero.HeroConfig;
import com.projecthero.mod.hero.Power;
import com.projecthero.mod.hero.Powers;
import com.projecthero.mod.hero.power.p04.SuperSpeedHandlers;
import com.projecthero.mod.hero.power.p04.SuperSpeedMoves;
import com.projecthero.mod.hero.power.p04.SuperSpeedTimeSlow;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/**
 * v0.14.7 Super Speed batch: the eight-key kit (G Blitz, Shift+R Mach Punch, Shift+G Speed Vortex, Shift+X Speed
 * Sweep, H Afterimage Decoy, N Speed Carry), Rapid Assault through i-frames, Regeneration II, the wall run's server
 * half and Time Slow on projectiles / falling blocks. Mock players are not reliably ticked, so the per-tick moves are
 * driven by hand. Everything stays inside the 8x8x8 barrier cage.
 */
public class SuperSpeedV0147GameTests implements FabricGameTest {

	/** A survival speedster at (2.5, 2, 1.5) facing +Z, in a cleared 7 x 4 x 7 room with a stone floor. */
	private static ServerPlayer hero(GameTestHelper helper) {
		ServerPlayer p = helper.makeMockServerPlayerInLevel();
		p.setGameMode(GameType.SURVIVAL);
		for (BlockPos pos : BlockPos.betweenClosed(helper.absolutePos(new BlockPos(0, 2, 0)), helper.absolutePos(new BlockPos(6, 5, 6)))) {
			helper.getLevel().setBlock(pos, Blocks.AIR.defaultBlockState(), 2);
		}
		for (BlockPos pos : BlockPos.betweenClosed(helper.absolutePos(new BlockPos(0, 1, 0)), helper.absolutePos(new BlockPos(6, 1, 6)))) {
			helper.getLevel().setBlock(pos, Blocks.STONE.defaultBlockState(), 2);
		}
		Vec3 at = helper.absoluteVec(new Vec3(2.5, 2.0, 1.5));
		p.moveTo(at.x, at.y, at.z, 0.0f, 0.0f);
		p.setYRot(0);
		p.setXRot(0);
		p.setYHeadRot(0);
		Power power = power();
		ExperimentalPowers.grant(p, power);
		ExperimentalPowers.setActive(p, power);
		p.setOnGround(true);
		return p;
	}

	private static Power power() {
		return Powers.byKey(SuperSpeedHandlers.KEY);
	}

	/** A no-AI, armourless 100 HP zombie at relative {@code (x, 2, z)} facing -Z. */
	private static Zombie zombie(GameTestHelper helper, double x, double z) {
		Zombie m = EntityType.ZOMBIE.create(helper.getLevel());
		Vec3 at = helper.absoluteVec(new Vec3(x, 2.0, z));
		m.moveTo(at.x, at.y, at.z, 180f, 0f);
		m.setNoAi(true);
		m.getAttribute(Attributes.ARMOR).setBaseValue(0);
		m.getAttribute(Attributes.MAX_HEALTH).setBaseValue(100);
		m.setHealth(100);
		helper.getLevel().addFreshEntity(m);
		return m;
	}

	private static float res(ServerPlayer p, String name) {
		return ExperimentalPowers.getResource(p, power(), name);
	}

	private static void shiftPress(ServerPlayer p, int slot) {
		p.setShiftKeyDown(true);
		AbilityRouter.handleInput(p, slot, true);
		p.setShiftKeyDown(false);
	}

	// ---- G: Blitz ------------------------------------------------------------------------------

	@GameTest(template = EMPTY_STRUCTURE, batch = "speed_v0147_blitz")
	public void blitzZipsToTheZombieAndHitsFor20(GameTestHelper helper) {
		ServerPlayer p = hero(helper);
		Zombie z = zombie(helper, 2.5, 5.5);
		p.lookAt(EntityAnchorArgument.Anchor.EYES, z.getEyePosition());
		AbilityRouter.handleInput(p, 2, true); // G
		float dealt = 100f - z.getHealth();
		helper.assertTrue(Math.abs(dealt - SuperSpeedMoves.BLITZ_DAMAGE) < 0.01f, "Blitz hits for 20, dealt " + dealt);
		helper.assertTrue(p.distanceTo(z) < 2.0f, "the speedster lands right next to the zombie (" + p.distanceTo(z) + ")");
		int cd = ExperimentalPowers.cooldownRemainingTicks(p, power(), power().ability(AbilitySlot.SLOT_2));
		int expected = HeroConfig.get().scaledCooldown(80);
		helper.assertTrue(Math.abs(cd - expected) <= 1, "4 s cooldown (" + cd + " / " + expected + ")");
		float before = z.getHealth();
		z.invulnerableTime = 0;
		AbilityRouter.handleInput(p, 2, true); // on cooldown
		helper.assertTrue(z.getHealth() == before, "no second Blitz during the cooldown");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void blitzWithNoTargetCostsNothing(GameTestHelper helper) {
		ServerPlayer p = hero(helper);
		p.setXRot(-60f); // at the sky
		Vec3 before = p.position();
		AbilityRouter.handleInput(p, 2, true);
		helper.assertTrue(ExperimentalPowers.cooldownReady(p, power(), power().ability(AbilitySlot.SLOT_2)), "no cooldown");
		helper.assertTrue(p.position().distanceTo(before) < 0.01, "and no zip");
		helper.succeed();
	}

	// ---- R: Rapid Assault through i-frames, Shift+R Mach Punch ------------------------------------

	@GameTest(template = EMPTY_STRUCTURE, batch = "speed_v0147_assault")
	public void rapidAssaultLandsAllFourPunchesThroughIFrames(GameTestHelper helper) {
		ServerPlayer p = hero(helper);
		IronGolem golem = EntityType.IRON_GOLEM.create(helper.getLevel());
		golem.moveTo(p.getX(), p.getY(), p.getZ() + 2.0, 180f, 0f);
		golem.setNoAi(true);
		helper.getLevel().addFreshEntity(golem);
		p.lookAt(EntityAnchorArgument.Anchor.EYES, golem.getEyePosition());
		golem.invulnerableTime = 20; // just hit by something else
		float before = golem.getHealth();
		AbilityRouter.handleInput(p, 1, true); // R
		float dealt = before - golem.getHealth();
		helper.assertTrue(Math.abs(dealt - 32f) < 0.01f, "4 x 8 = 32 even inside the i-frame window, dealt " + dealt);
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE, batch = "speed_v0147_assault_pvp")
	public void rapidAssaultLandsAllFourPunchesOnAPlayer(GameTestHelper helper) {
		ServerPlayer p = hero(helper);
		ServerPlayer victim = helper.makeMockServerPlayerInLevel();
		victim.setGameMode(GameType.SURVIVAL);
		victim.moveTo(p.getX(), p.getY(), p.getZ() + 2.0, 180f, 0f);
		victim.getAttribute(Attributes.MAX_HEALTH).setBaseValue(100);
		victim.setHealth(100);
		p.lookAt(EntityAnchorArgument.Anchor.EYES, victim.getEyePosition());
		boolean pvp = helper.getLevel().getServer().isPvpAllowed() && HeroConfig.get().abilityPvpDamage;
		victim.invulnerableTime = 20;
		AbilityRouter.handleInput(p, 1, true);
		float dealt = 100f - victim.getHealth();
		if (pvp) {
			helper.assertTrue(Math.abs(dealt - 32f) < 0.01f, "4 x 8 = 32 on a player too, dealt " + dealt);
		} else {
			helper.assertTrue(dealt == 0f, "PvP off: no damage to players");
		}
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE, batch = "speed_v0147_mach")
	public void machPunchStandingHitsFor12OnItsOwnCooldown(GameTestHelper helper) {
		ServerPlayer p = hero(helper);
		IronGolem golem = EntityType.IRON_GOLEM.create(helper.getLevel());
		golem.moveTo(p.getX(), p.getY(), p.getZ() + 2.0, 180f, 0f);
		golem.setNoAi(true);
		helper.getLevel().addFreshEntity(golem);
		p.lookAt(EntityAnchorArgument.Anchor.EYES, golem.getEyePosition());
		helper.assertTrue(SuperSpeedMoves.machDamage(0) == 12f && SuperSpeedMoves.machDamage(3.2) == 30f
				&& SuperSpeedMoves.machDamage(10) == 30f, "12 standing, 30 at full Overdrive speed");
		float before = golem.getHealth();
		shiftPress(p, 1); // Shift+R
		float dealt = before - golem.getHealth();
		helper.assertTrue(Math.abs(dealt - 12f) < 0.01f, "a standing Mach Punch hits for 12, dealt " + dealt);
		helper.assertTrue(res(p, SuperSpeedMoves.MACH_READY) > helper.getLevel().getGameTime() + 100,
				"Mach Punch goes on its own 10 s cooldown");
		helper.assertTrue(ExperimentalPowers.cooldownReady(p, power(), power().ability(AbilitySlot.SLOT_1)),
				"Rapid Assault stays ready");
		float mid = golem.getHealth();
		shiftPress(p, 1);
		helper.assertTrue(golem.getHealth() == mid, "no second Mach Punch during its cooldown");
		AbilityRouter.handleInput(p, 1, true); // plain R still fires
		helper.assertTrue(golem.getHealth() < mid, "plain R still works while Mach Punch recharges");
		helper.succeed();
	}

	// ---- Shift+G: Speed Vortex ---------------------------------------------------------------------

	@GameTest(template = EMPTY_STRUCTURE, batch = "speed_v0147_vortex")
	public void speedVortexGrindsThenBursts(GameTestHelper helper) {
		ServerPlayer p = hero(helper);
		Zombie z = zombie(helper, 2.5, 4.5);
		shiftPress(p, 2); // Shift+G
		helper.assertTrue(SuperSpeedMoves.vortexActive(p), "the vortex spins up");
		helper.assertTrue(ExperimentalPowers.cooldownReady(p, power(), power().ability(AbilitySlot.SLOT_2)),
				"Blitz stays ready");
		for (int i = 0; i < SuperSpeedMoves.VORTEX_TICKS; i++) {
			SuperSpeedMoves.vortexTick(p);
		}
		helper.assertFalse(SuperSpeedMoves.vortexActive(p), "and ends after 3 s");
		float dealt = 100f - z.getHealth();
		float expected = 5 * SuperSpeedMoves.VORTEX_TICK_DAMAGE + SuperSpeedMoves.VORTEX_FINAL_DAMAGE;
		helper.assertTrue(Math.abs(dealt - expected) < 0.01f, "5 x 3 + 8 = " + expected + ", dealt " + dealt);
		helper.assertTrue(res(p, SuperSpeedMoves.VORTEX_READY) > helper.getLevel().getGameTime() + 200, "14 s cooldown");
		shiftPress(p, 2);
		helper.assertFalse(SuperSpeedMoves.vortexActive(p), "not again during the cooldown");
		helper.succeed();
	}

	// ---- Shift+X: Speed Sweep ------------------------------------------------------------------------

	@GameTest(template = EMPTY_STRUCTURE, batch = "speed_v0147_sweep")
	public void speedSweepHitsEveryZombieAndComesHome(GameTestHelper helper) {
		ServerPlayer p = hero(helper);
		p.setYRot(37f);
		p.setXRot(12f);
		Vec3 home = p.position();
		Zombie a = zombie(helper, 0.8, 4.5);
		Zombie b = zombie(helper, 4.5, 4.5);
		Zombie c = zombie(helper, 5.2, 1.5);
		shiftPress(p, 3); // Shift+X
		helper.assertTrue(SuperSpeedMoves.sweeping(p), "the sweep starts");
		helper.assertFalse(ServerLivingEntityEvents.ALLOW_DAMAGE.invoker().allowDamage(p,
				helper.getLevel().damageSources().mobAttack(a), 5f), "untouchable mid-sweep");
		for (int i = 0; i < 40 && SuperSpeedMoves.sweeping(p); i++) {
			SuperSpeedMoves.sweepTick(p);
		}
		helper.assertFalse(SuperSpeedMoves.sweeping(p), "the sweep finishes");
		for (Zombie z : new Zombie[] { a, b, c }) {
			float dealt = 100f - z.getHealth();
			helper.assertTrue(Math.abs(dealt - SuperSpeedMoves.SWEEP_DAMAGE) < 0.01f, "each zombie takes 12, dealt " + dealt);
		}
		helper.assertTrue(p.position().distanceTo(home) < 0.01, "back exactly where it started (" + p.position() + " vs " + home + ")");
		helper.assertTrue(Math.abs(p.getYRot() - 37f) < 0.01f && Math.abs(p.getXRot() - 12f) < 0.01f, "facing the same way");
		helper.assertTrue(res(p, SuperSpeedMoves.SWEEP_READY) > helper.getLevel().getGameTime() + 300, "20 s cooldown");
		helper.assertTrue(ExperimentalPowers.cooldownReady(p, power(), power().ability(AbilitySlot.SLOT_3)),
				"Momentum Dash stays ready");
		helper.succeed();
	}

	// ---- H is the power wheel, N: Speed Carry ------------------------------------------------------------

	@GameTest(template = EMPTY_STRUCTURE, batch = "speed_v0147_h")
	public void hDoesNothingForSpeed(GameTestHelper helper) {
		ServerPlayer p = hero(helper);
		Zombie z = zombie(helper, 2.5, 3.5);
		p.lookAt(EntityAnchorArgument.Anchor.EYES, z.getEyePosition());
		Vec3 before = p.position();
		AbilityRouter.handleInput(p, 7, true); // H: no Super Speed move
		AbilityRouter.handleInput(p, 7, false);
		helper.assertTrue(power().ability(AbilitySlot.SLOT_7) == null, "Super Speed has no H ability");
		helper.assertTrue(z.getVehicle() == null && z.getHealth() == 100f && p.position().distanceTo(before) < 0.01,
				"H does nothing to the world");
		for (var ab : power().abilities()) {
			helper.assertTrue(ExperimentalPowers.cooldownReady(p, power(), ab), ab.id() + " untouched");
		}
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE, batch = "speed_v0147_carry")
	public void speedCarryIsOnN(GameTestHelper helper) {
		ServerPlayer p = hero(helper);
		Zombie z = zombie(helper, 2.5, 3.5);
		p.lookAt(EntityAnchorArgument.Anchor.EYES, z.getEyePosition());
		AbilityRouter.handleInput(p, 8, true); // N
		helper.assertTrue(z.getVehicle() == p, "N picks the zombie up");
		AbilityRouter.handleInput(p, 8, true);
		helper.assertTrue(z.getVehicle() == null, "and N again sets it down");
		helper.succeed();
	}

	// ---- passives --------------------------------------------------------------------------------------

	@GameTest(template = EMPTY_STRUCTURE)
	public void regenerationTwoWhileOwned(GameTestHelper helper) {
		ServerPlayer p = hero(helper);
		MobEffectInstance regen = p.getEffect(MobEffects.REGENERATION);
		helper.assertTrue(regen != null && regen.getAmplifier() == SuperSpeedHandlers.REGEN_AMPLIFIER
				&& regen.isInfiniteDuration(), "permanent Regeneration II");
		ExperimentalPowers.forget(p, power());
		helper.assertTrue(p.getEffect(MobEffects.REGENERATION) == null, "gone with the power");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE, batch = "speed_v0147_wallrun")
	public void wallRunKeepsYouSafeFromTheFall(GameTestHelper helper) {
		ServerPlayer p = hero(helper);
		BlockPos wall = BlockPos.containing(p.position()).offset(0, 0, 1);
		helper.getLevel().setBlock(wall, Blocks.STONE.defaultBlockState(), 2);
		helper.getLevel().setBlock(wall.above(), Blocks.STONE.defaultBlockState(), 2);
		p.setXRot(-80f);
		helper.assertFalse(SuperSpeedHandlers.wallRunReady(p), "no wall run without Speed Mode");
		AbilityRouter.handleInput(p, 6, true); // C: Speed Mode
		helper.assertTrue(SuperSpeedHandlers.wallRunReady(p), "Speed Mode + a wall ahead + looking up = wall run");
		p.fallDistance = 5f;
		SuperSpeedHandlers.wallRunTick(p);
		helper.assertTrue(p.fallDistance == 0f, "no fall distance builds up while running up the wall");
		p.setXRot(0f);
		helper.assertFalse(SuperSpeedHandlers.wallRunReady(p), "looking ahead is a normal run");
		helper.succeed();
	}

	// ---- Time Slow: game-wide, the caster at full speed --------------------------------------------------

	@GameTest(template = EMPTY_STRUCTURE, batch = "speed_v0147_timeslow")
	public void timeSlowSlowsTheWholeServerButNotTheCaster(GameTestHelper helper) {
		ServerPlayer p = hero(helper);
		MinecraftServer server = helper.getLevel().getServer();
		float rateBefore = server.tickRateManager().tickrate();
		Zombie z = zombie(helper, 2.5, 3.5);
		ServerPlayer other = helper.makeMockServerPlayerInLevel();
		ExperimentalPowers.grant(other, power());
		ExperimentalPowers.setActive(other, power());
		AbilityRouter.handleInput(p, 4, true); // Z
		try {
			helper.assertTrue(SuperSpeedTimeSlow.isCasting(p), "Time Slow starts");
			helper.assertTrue(server.tickRateManager().tickrate() == SuperSpeedTimeSlow.SLOW_RATE,
					"the whole server drops to 1 tick a second (" + server.tickRateManager().tickrate() + ")");
			helper.assertTrue(ExperimentalPowers.cooldownReady(p, power(), power().ability(AbilitySlot.SLOT_4)),
					"no cooldown while it runs");
			AbilityRouter.handleInput(other, 4, true);
			helper.assertFalse(SuperSpeedTimeSlow.isCasting(other), "only one Time Slow at a time");

			// the caster's extra ticks: they (and their cooldowns) keep going, the zombie does not
			var blitz = power().ability(AbilitySlot.SLOT_2);
			ExperimentalPowers.triggerCooldown(p, power(), blitz, 80);
			int cd0 = ExperimentalPowers.cooldownRemainingTicks(p, power(), blitz);
			int pt = p.tickCount;
			int zt = z.tickCount;
			p.resetAttackStrengthTicker();
			for (int i = 0; i < 10; i++) {
				SuperSpeedTimeSlow.extraCasterTick(p);
			}
			helper.assertTrue(p.getAttackStrengthScale(0f) > 0.99f,
					"the caster's attack cooldown recharges at full speed (" + p.getAttackStrengthScale(0f) + ")");
			helper.assertTrue(p.tickCount - pt == 10, "the caster lived 10 ticks (" + (p.tickCount - pt) + ")");
			helper.assertTrue(z.tickCount == zt, "the zombie none");
			int cd1 = ExperimentalPowers.cooldownRemainingTicks(p, power(), blitz);
			helper.assertTrue(cd0 - cd1 == 10, "the caster's cooldowns run at their pace (" + cd0 + " -> " + cd1 + ")");

			// slowed creatures take the caster's hits in full, at the caster's cadence
			var hit = helper.getLevel().damageSources().playerAttack(p);
			z.hurt(hit, 5f);
			helper.assertTrue(Math.abs(z.getHealth() - 95f) < 0.01f, "first hit lands (" + z.getHealth() + ")");
			for (int i = 0; i < SuperSpeedTimeSlow.CASTER_HIT_COOLDOWN; i++) {
				SuperSpeedTimeSlow.extraCasterTick(p);
			}
			z.hurt(hit, 5f);
			helper.assertTrue(Math.abs(z.getHealth() - 90f) < 0.01f,
					"10 caster ticks later the second lands in full too (" + z.getHealth() + ")");
			// ... and a kill pays out at once
			z.setHealth(3f);
			for (int i = 0; i < SuperSpeedTimeSlow.CASTER_HIT_COOLDOWN; i++) {
				SuperSpeedTimeSlow.extraCasterTick(p);
			}
			z.hurt(hit, 5f);
			helper.assertTrue(z.isDeadOrDying(), "the killing blow kills");
			boolean xp = !helper.getLevel().getEntitiesOfClass(net.minecraft.world.entity.ExperienceOrb.class,
					z.getBoundingBox().inflate(3.0)).isEmpty();
			helper.assertTrue(xp, "and the XP drops straight away");
		} finally {
			AbilityRouter.handleInput(p, 4, true); // Z again: end it
			SuperSpeedTimeSlow.end(p, false); // belt and braces: never leave the test server at 1 tick a second
		}
		helper.assertFalse(SuperSpeedTimeSlow.isCasting(p), "a second press ends it");
		helper.assertTrue(server.tickRateManager().tickrate() == rateBefore, "the tick rate is put back");
		int cd = ExperimentalPowers.cooldownRemainingTicks(p, power(), power().ability(AbilitySlot.SLOT_4));
		int expected = HeroConfig.get().scaledCooldown(SuperSpeedTimeSlow.COOLDOWN_TICKS);
		helper.assertTrue(Math.abs(cd - expected) <= 1, "150 s cooldown starts at the end (" + cd + " / " + expected + ")");
		helper.succeed();
	}
}
