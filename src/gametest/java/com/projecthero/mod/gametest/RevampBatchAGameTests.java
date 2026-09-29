package com.projecthero.mod.gametest;

import com.projecthero.mod.hero.Ability;
import com.projecthero.mod.hero.AbilityHandlers;
import com.projecthero.mod.hero.AbilityRouter;
import com.projecthero.mod.hero.AbilitySlot;
import com.projecthero.mod.hero.ExperimentalPowers;
import com.projecthero.mod.hero.Power;
import com.projecthero.mod.hero.Powers;
import com.projecthero.mod.hero.power.p01.SuperStrengthHandlers;
import com.projecthero.mod.hero.power.p02.LaserVisionHandlers;
import com.projecthero.mod.hero.power.p03.FlightHandlers;
import com.projecthero.mod.hero.power.p04.SuperSpeedHandlers;
import com.projecthero.mod.hero.power.p12.SuperRegenerationHandlers;
import com.projecthero.mod.hero.power.p13.SuperDurabilityHandlers;
import com.projecthero.mod.hero.revamp.batcha.ThrownChunkEntity;
import com.projecthero.mod.hero.visual.MutationVisuals;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.Wolf;
import net.minecraft.world.entity.monster.Skeleton;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/**
 * v0.13.22 mutation revamp, batch A: regression coverage for the reworked kits (Super Strength, Laser Vision, Flight,
 * Super Speed, Super Regeneration, Super Durability). Mock players are not reliably ticked by the server, so tests
 * drive {@link ExperimentalPowers#serverTick} themselves; each AoE-heavy test runs in its own batch so its radius
 * never reaches another test's mobs.
 */
public class RevampBatchAGameTests implements FabricGameTest {
	private static final String[] BATCH_A = { SuperStrengthHandlers.KEY, LaserVisionHandlers.KEY, FlightHandlers.KEY,
			SuperSpeedHandlers.KEY, SuperRegenerationHandlers.KEY, SuperDurabilityHandlers.KEY };

	// ---------------- helpers ----------------

	/** A survival mock player holding {@code powerKey}, standing in a cleared room with a stone floor, facing +Z. */
	private static ServerPlayer hero(GameTestHelper helper, String powerKey) {
		ServerPlayer p = helper.makeMockServerPlayerInLevel();
		p.setGameMode(GameType.SURVIVAL);
		Vec3 at = helper.absoluteVec(new Vec3(4.5, 2.0, 2.5));
		p.moveTo(at.x, at.y, at.z, 0.0f, 0.0f);
		p.setYRot(0);
		p.setXRot(0);
		p.setYHeadRot(0);
		BlockPos base = BlockPos.containing(at);
		for (BlockPos pos : BlockPos.betweenClosed(base.offset(-4, 0, -2), base.offset(4, 5, 10))) {
			helper.getLevel().setBlock(pos, Blocks.AIR.defaultBlockState(), 2);
		}
		for (BlockPos pos : BlockPos.betweenClosed(base.offset(-4, -1, -2), base.offset(4, -1, 10))) {
			helper.getLevel().setBlock(pos, Blocks.STONE.defaultBlockState(), 2);
		}
		Power power = Powers.byKey(powerKey);
		ExperimentalPowers.grant(p, power);
		ExperimentalPowers.setActive(p, power);
		p.setOnGround(true);
		return p;
	}

	private static Zombie zombieAhead(GameTestHelper helper, ServerPlayer p, double dist) {
		Zombie z = EntityType.ZOMBIE.create(helper.getLevel());
		z.moveTo(p.getX(), p.getY(), p.getZ() + dist, 180f, 0f);
		z.setNoAi(true);
		helper.getLevel().addFreshEntity(z);
		return z;
	}

	private static Wolf petWolf(GameTestHelper helper, ServerPlayer p, double dx, double dz) {
		Wolf w = EntityType.WOLF.create(helper.getLevel());
		w.moveTo(p.getX() + dx, p.getY(), p.getZ() + dz, 0f, 0f);
		w.tame(p);
		helper.getLevel().addFreshEntity(w);
		return w;
	}

	private static float res(ServerPlayer p, String key, String name) {
		return ExperimentalPowers.getResource(p, Powers.byKey(key), name);
	}

	private static void setRes(ServerPlayer p, String key, String name, float v) {
		ExperimentalPowers.setResource(p, Powers.byKey(key), name, v, 1e9f);
	}

	private static void clearCooldown(ServerPlayer p, String key, AbilitySlot slot) {
		Power power = Powers.byKey(key);
		ExperimentalPowers.state(p).abilityReadyAt.remove(key + "/" + power.ability(slot).id());
	}

	private static boolean onCooldown(ServerPlayer p, String key, AbilitySlot slot) {
		Power power = Powers.byKey(key);
		return !ExperimentalPowers.cooldownReady(p, power, power.ability(slot));
	}

	/** Re-evaluates the overlay flags now (the real tick only does it every 4th tick). */
	private static void refreshVisuals(ServerPlayer p) {
		for (int i = 0; i < 4; i++) {
			p.tickCount = i;
			MutationVisuals.tick(p);
		}
	}

	private static void tick(ServerPlayer p, int n) {
		for (int i = 0; i < n; i++) {
			ExperimentalPowers.serverTick(p);
		}
	}

	// ---------------- registry ----------------

	@GameTest(template = EMPTY_STRUCTURE)
	public void batchPowersRegistered(GameTestHelper helper) {
		helper.assertTrue(Powers.count() == 27, "expected 27 powers");
		for (String key : BATCH_A) {
			Power p = Powers.byKey(key);
			helper.assertTrue(p != null && p.abilities().size() == 8, key + " must have 8 abilities");
			for (AbilitySlot slot : AbilitySlot.values()) {
				Ability a = p.ability(slot);
				helper.assertTrue(a != null && a.slot() == slot, key + " slot " + slot + " mismapped");
				helper.assertTrue(AbilityHandlers.has(p, a), key + "/" + a.id() + " has no handler");
			}
		}
		// the ids the client code / older saves rely on stay put
		helper.assertTrue("power_leap".equals(Powers.byKey(SuperStrengthHandlers.KEY).ability(AbilitySlot.SLOT_3).id()),
				"Power Leap stays on X (client-timed flow)");
		helper.assertTrue("thermal_vision".equals(Powers.byKey(LaserVisionHandlers.KEY).ability(AbilitySlot.SLOT_6).id()),
				"Thermal Vision stays on C (client glow mixin)");
		helper.assertTrue("flight_toggle".equals(Powers.byKey(FlightHandlers.KEY).ability(AbilitySlot.SLOT_3).id()),
				"Flight stays on X");
		helper.assertTrue("speed_mode".equals(Powers.byKey(SuperSpeedHandlers.KEY).ability(AbilitySlot.SLOT_6).id()),
				"Speed Mode stays on C");
		helper.assertTrue("resurrection".equals(Powers.byKey(SuperRegenerationHandlers.KEY).ability(AbilitySlot.SLOT_4).id()),
				"Resurrection stays on Z");
		helper.assertTrue(MutationVisuals.registeredFlags().containsAll(java.util.List.of("p01.effort", "p02.eyes", "p03.wind",
				"p04.crackle", "p12.regen", "p12.rage", "p13.steel", "p13.gold")), "batch A overlay flags registered");
		helper.succeed();
	}

	// ---------------- 01 Super Strength ----------------

	@GameTest(template = EMPTY_STRUCTURE, batch = "revamp_a_haymaker")
	public void strengthHaymakerComboLaunchesOnTheThirdHit(GameTestHelper helper) {
		ServerPlayer p = hero(helper, SuperStrengthHandlers.KEY);
		Zombie z = zombieAhead(helper, p, 2.0);
		p.lookAt(net.minecraft.commands.arguments.EntityAnchorArgument.Anchor.EYES, z.getEyePosition());
		float before = z.getHealth();
		AbilityRouter.handleInput(p, 1, true);
		helper.assertTrue(res(p, SuperStrengthHandlers.KEY, "combo") == 1f, "first press is the jab");
		helper.assertTrue(z.getHealth() < before, "the jab lands");
		clearCooldown(p, SuperStrengthHandlers.KEY, AbilitySlot.SLOT_1);
		AbilityRouter.handleInput(p, 1, true);
		helper.assertTrue(res(p, SuperStrengthHandlers.KEY, "combo") == 2f, "second press is the cross");
		clearCooldown(p, SuperStrengthHandlers.KEY, AbilitySlot.SLOT_1);
		AbilityRouter.handleInput(p, 1, true);
		helper.assertTrue(res(p, SuperStrengthHandlers.KEY, "combo") == 0f, "the haymaker finishes the combo");
		Power power = Powers.byKey(SuperStrengthHandlers.KEY);
		helper.assertTrue(ExperimentalPowers.cooldownRemainingTicks(p, power, power.ability(AbilitySlot.SLOT_1)) > 40,
				"the finisher puts the full cooldown on");
		helper.assertTrue("haymaker".equals(MutationVisuals.anim(p)), "the haymaker pose plays");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE, batch = "revamp_a_grab")
	public void strengthGrabRipsABlockAndThrowsIt(GameTestHelper helper) {
		ServerPlayer p = hero(helper, SuperStrengthHandlers.KEY);
		BlockPos target = BlockPos.containing(p.getX(), p.getY() + 1, p.getZ() + 2);
		helper.getLevel().setBlock(target, Blocks.COBBLESTONE.defaultBlockState(), 2);
		AbilityRouter.handleInput(p, 5, true); // V = Grab & Throw
		int id = (int) res(p, SuperStrengthHandlers.KEY, "chunk_id");
		helper.assertTrue(id != 0, "an empty-handed grab at a block rips it out");
		ThrownChunkEntity chunk = helper.getLevel().getEntity(id) instanceof ThrownChunkEntity c ? c : null;
		helper.assertTrue(chunk != null && chunk.blockState().is(Blocks.COBBLESTONE), "the chunk is made of that block");
		tick(p, 2);
		helper.assertTrue(chunk.getY() > p.getY() + 1.5, "the chunk is held over your head");
		AbilityRouter.handleInput(p, 5, true);
		helper.assertTrue(chunk.flying(), "the second press throws it");
		helper.assertTrue(res(p, SuperStrengthHandlers.KEY, "chunk_id") == 0f, "and your hands are empty again");
		helper.assertTrue(onCooldown(p, SuperStrengthHandlers.KEY, AbilitySlot.SLOT_5), "throwing starts the cooldown");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE, batch = "revamp_a_grabmob")
	public void strengthGrabHoistsAMobOverhead(GameTestHelper helper) {
		ServerPlayer p = hero(helper, SuperStrengthHandlers.KEY);
		Zombie z = zombieAhead(helper, p, 2.0);
		p.lookAt(net.minecraft.commands.arguments.EntityAnchorArgument.Anchor.EYES, z.getEyePosition());
		AbilityRouter.handleInput(p, 5, true);
		helper.assertTrue(res(p, SuperStrengthHandlers.KEY, "grabbed") == z.getId(), "the zombie is grabbed");
		tick(p, 1);
		helper.assertTrue(z.getY() > p.getY() + 1.5, "held above the head");
		AbilityRouter.handleInput(p, 5, true);
		helper.assertTrue(res(p, SuperStrengthHandlers.KEY, "thrown_id") == z.getId(), "then hurled");
		helper.assertTrue(z.getDeltaMovement().z > 1.0, "along the aim");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE, batch = "revamp_a_clap")
	public void strengthThunderclapStunsAndPutsOutFire(GameTestHelper helper) {
		ServerPlayer p = hero(helper, SuperStrengthHandlers.KEY);
		Zombie z = zombieAhead(helper, p, 3.0);
		z.setRemainingFireTicks(200);
		BlockPos fire = BlockPos.containing(p.getX() + 1, p.getY(), p.getZ() + 4);
		helper.getLevel().setBlock(fire, Blocks.FIRE.defaultBlockState(), 2);
		p.setRemainingFireTicks(100);
		AbilityRouter.handleInput(p, 7, true); // H = Thunderclap
		helper.assertTrue(z.hasEffect(MobEffects.MOVEMENT_SLOWDOWN), "the clap stuns what is in the cone");
		helper.assertFalse(z.isOnFire(), "and puts it out");
		helper.assertFalse(p.isOnFire(), "and you");
		helper.assertTrue(helper.getLevel().getBlockState(fire).isAir(), "and the fire on the ground");
		helper.assertTrue(onCooldown(p, SuperStrengthHandlers.KEY, AbilitySlot.SLOT_7), "Thunderclap goes on cooldown");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE, batch = "revamp_a_rip", timeoutTicks = 100)
	public void strengthRipAndHurlTearsUpABoulderAndThrowsIt(GameTestHelper helper) {
		ServerPlayer p = hero(helper, SuperStrengthHandlers.KEY);
		AbilityRouter.handleInput(p, 8, true); // N = Rip & Hurl
		int id = (int) res(p, SuperStrengthHandlers.KEY, "rip_id");
		helper.assertTrue(id != 0, "a boulder is torn up");
		ThrownChunkEntity chunk = helper.getLevel().getEntity(id) instanceof ThrownChunkEntity c ? c : null;
		helper.assertTrue(chunk != null && chunk.scale() > 1.5f, "a big one");
		tick(p, 14);
		helper.assertTrue(res(p, SuperStrengthHandlers.KEY, "rip_id") == 0f, "after rising it is hurled");
		helper.assertTrue(chunk.flying() || !chunk.isAlive(), "and flies (or has already shattered)");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE, batch = "revamp_a_effort")
	public void strengthMaximumEffortDoublesAndShowsVeins(GameTestHelper helper) {
		ServerPlayer p = hero(helper, SuperStrengthHandlers.KEY);
		double atk = p.getAttributeValue(Attributes.ATTACK_DAMAGE);
		AbilityRouter.handleInput(p, 4, true); // Z = Maximum Effort
		helper.assertTrue(SuperStrengthHandlers.maxEffortActive(p), "Maximum Effort runs");
		helper.assertTrue(p.getAttributeValue(Attributes.ATTACK_DAMAGE) > atk * 1.9, "melee doubles");
		helper.assertTrue(p.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE) >= 1.0, "knockback immune");
		refreshVisuals(p);
		helper.assertTrue(MutationVisuals.hasFlag(p, "p01.effort"), "the vein overlay is on");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE, batch = "revamp_a_landing")
	public void strengthPowerLeapEndsInAHeroLanding(GameTestHelper helper) {
		ServerPlayer p = hero(helper, SuperStrengthHandlers.KEY);
		SuperStrengthHandlers.performPowerLeap(p, 35);
		helper.assertTrue(res(p, SuperStrengthHandlers.KEY, "leap_air") > 0.5f, "the leap arms the landing");
		// land it: back on the floor, a zombie right next to the impact
		Zombie z = zombieAhead(helper, p, 1.0);
		float before = z.getHealth();
		setRes(p, SuperStrengthHandlers.KEY, "leap_air_ticks", 10);
		p.setDeltaMovement(Vec3.ZERO);
		p.setOnGround(true);
		tick(p, 1);
		helper.assertTrue(res(p, SuperStrengthHandlers.KEY, "leap_air") == 0f, "touching down triggers the landing");
		helper.assertTrue(z.getHealth() < before, "the crater hits what is around you");
		helper.succeed();
	}

	// ---------------- 02 Laser Vision ----------------

	@GameTest(template = EMPTY_STRUCTURE, batch = "revamp_a_heat")
	public void laserHeatVisionBuildsHeatAndBrightensTheEyes(GameTestHelper helper) {
		ServerPlayer p = hero(helper, LaserVisionHandlers.KEY);
		refreshVisuals(p);
		helper.assertTrue(MutationVisuals.hasFlag(p, "p02.eyes"), "the eyes always glow");
		AbilityRouter.handleInput(p, 1, true);
		tick(p, 20);
		helper.assertTrue(res(p, LaserVisionHandlers.KEY, "heat") > 20f, "beaming builds heat");
		helper.assertTrue(LaserVisionHandlers.firing(p), "while firing");
		helper.assertTrue("beam_eyes".equals(MutationVisuals.anim(p)), "the beam pose plays");
		refreshVisuals(p);
		helper.assertTrue(MutationVisuals.state(p).value("p02.eye_glow", 0f) > 0.99f, "the eyes blaze while firing");
		AbilityRouter.handleInput(p, 1, false);
		helper.assertFalse(LaserVisionHandlers.firing(p), "release stops the beam");
		helper.assertTrue(LaserVisionHandlers.heatMult(p) > 1.0f, "a hot gauge sharpens the next beam");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE, batch = "revamp_a_overheat")
	public void laserOverheatLocksTheBeamsOut(GameTestHelper helper) {
		ServerPlayer p = hero(helper, LaserVisionHandlers.KEY);
		setRes(p, LaserVisionHandlers.KEY, "heat", LaserVisionHandlers.MAX_HEAT - 10f);
		AbilityRouter.handleInput(p, 3, true); // X = Recoil Blast (+50 heat) -> overheat
		helper.assertTrue(res(p, LaserVisionHandlers.KEY, "overheat") > 0.5f, "a full gauge overheats");
		AbilityRouter.handleInput(p, 5, true); // V = Ricochet Shot: refused while overheated
		helper.assertFalse(onCooldown(p, LaserVisionHandlers.KEY, AbilitySlot.SLOT_5), "nothing fires while overheated");
		helper.assertTrue(Math.abs(LaserVisionHandlers.heatMult(p) - 1.6f) < 0.01f, "a full gauge is x1.6");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE, batch = "revamp_a_lance")
	public void laserPiercingLanceRunsThroughTwoTargets(GameTestHelper helper) {
		ServerPlayer p = hero(helper, LaserVisionHandlers.KEY);
		Zombie a = zombieAhead(helper, p, 3.0);
		Zombie b = zombieAhead(helper, p, 5.5);
		float ha = a.getHealth();
		float hb = b.getHealth();
		p.lookAt(net.minecraft.commands.arguments.EntityAnchorArgument.Anchor.EYES, a.position().add(0, 1.0, 0));
		AbilityRouter.handleInput(p, 2, true);
		setRes(p, LaserVisionHandlers.KEY, "lance_held", 40);
		AbilityRouter.handleInput(p, 2, false);
		helper.assertTrue(a.getHealth() < ha, "the lance hits the first zombie");
		helper.assertTrue(b.getHealth() < hb, "and pierces into the second");
		helper.assertTrue(onCooldown(p, LaserVisionHandlers.KEY, AbilitySlot.SLOT_2), "then cools down");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE, batch = "revamp_a_cauterize")
	public void laserCauterizeTurnsHeatIntoHealing(GameTestHelper helper) {
		ServerPlayer p = hero(helper, LaserVisionHandlers.KEY);
		AbilityRouter.handleInput(p, 8, true);
		helper.assertFalse(onCooldown(p, LaserVisionHandlers.KEY, AbilitySlot.SLOT_8), "cold eyes cannot cauterize");
		p.setHealth(6f);
		p.addEffect(new MobEffectInstance(MobEffects.WITHER, 200, 0));
		setRes(p, LaserVisionHandlers.KEY, "heat", 400f);
		AbilityRouter.handleInput(p, 8, true);
		helper.assertTrue(p.getHealth() > 12f, "heat becomes health");
		helper.assertFalse(p.hasEffect(MobEffects.WITHER), "wither is burned out");
		helper.assertTrue(res(p, LaserVisionHandlers.KEY, "heat") == 0f, "the gauge is emptied");
		helper.succeed();
	}

	// ---------------- 03 Flight ----------------

	@GameTest(template = EMPTY_STRUCTURE, batch = "revamp_a_tiers")
	public void flightSprintingClimbsSpeedTiers(GameTestHelper helper) {
		ServerPlayer p = hero(helper, FlightHandlers.KEY);
		AbilityRouter.handleInput(p, 3, true); // X = Flight
		helper.assertTrue(com.projecthero.mod.hero.power.HeroFlight.isFlying(p), "flying");
		p.setSprinting(true);
		setRes(p, FlightHandlers.KEY, "tier_ticks", 119);
		tick(p, 1);
		helper.assertTrue(res(p, FlightHandlers.KEY, "speed_tier") == 3f, "three tiers of sprinting reach the top tier");
		helper.assertTrue(p.getAbilities().getFlyingSpeed() > 0.1f, "which flies faster");
		refreshVisuals(p);
		helper.assertTrue(MutationVisuals.hasFlag(p, "p03.wind"), "with the wind sheath");
		p.setSprinting(false);
		tick(p, 40);
		helper.assertTrue(res(p, FlightHandlers.KEY, "speed_tier") == 0f, "letting go of sprint bleeds the tiers off");
		AbilityRouter.handleInput(p, 3, true);
		helper.assertTrue(p.getAbilities().getFlyingSpeed() < 0.055f, "landing restores the vanilla flying speed");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE, batch = "revamp_a_roll")
	public void flightBarrelRollGivesIFrames(GameTestHelper helper) {
		ServerPlayer p = hero(helper, FlightHandlers.KEY);
		AbilityRouter.handleInput(p, 8, true); // N = Barrel Roll
		helper.assertTrue(FlightHandlers.rolling(p), "the roll opens an i-frame window");
		boolean allowed = ServerLivingEntityEvents.ALLOW_DAMAGE.invoker().allowDamage(p,
				p.level().damageSources().generic(), 5f);
		helper.assertFalse(allowed, "damage is refused mid-roll");
		tick(p, 12);
		helper.assertFalse(FlightHandlers.rolling(p), "the window closes");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE, batch = "revamp_a_orbit")
	public void flightOrbitalDropSlamsDown(GameTestHelper helper) {
		ServerPlayer p = hero(helper, FlightHandlers.KEY);
		AbilityRouter.handleInput(p, 4, true); // Z = Orbital Drop
		helper.assertTrue(res(p, FlightHandlers.KEY, "orbit_phase") == 1f, "it starts by rocketing up");
		helper.assertTrue(onCooldown(p, FlightHandlers.KEY, AbilitySlot.SLOT_4), "on cooldown");
		// skip to the end of the drop: on the floor, 20 blocks below where it started falling
		Zombie z = zombieAhead(helper, p, 2.0);
		float before = z.getHealth();
		setRes(p, FlightHandlers.KEY, "orbit_phase", 3);
		setRes(p, FlightHandlers.KEY, "orbit_drop_y", (float) (p.getY() + 1020));
		p.setOnGround(true);
		tick(p, 1);
		helper.assertTrue(res(p, FlightHandlers.KEY, "orbit_phase") == 0f, "the landing ends it");
		helper.assertTrue(z.getHealth() < before, "and the impact hits the zombie");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE, batch = "revamp_a_slip")
	public void flightSlipstreamTowsAllies(GameTestHelper helper) {
		ServerPlayer p = hero(helper, FlightHandlers.KEY);
		Wolf w = petWolf(helper, p, 0, 7.0);
		AbilityRouter.handleInput(p, 7, true); // H = Slipstream
		helper.assertTrue(res(p, FlightHandlers.KEY, "slip_ticks") > 0f, "Slipstream runs");
		setRes(p, FlightHandlers.KEY, "slip_ticks", 200); // on a 20-tick boundary: Slow Falling is (re)applied
		tick(p, 1);
		helper.assertTrue(w.getDeltaMovement().z < 0, "your pet is pulled along behind you");
		setRes(p, FlightHandlers.KEY, "slip_ticks", 201);
		tick(p, 1);
		helper.assertTrue(w.hasEffect(MobEffects.SLOW_FALLING), "and kept on Slow Falling");
		helper.succeed();
	}

	// ---------------- 04 Super Speed ----------------

	@GameTest(template = EMPTY_STRUCTURE, batch = "revamp_a_momentum")
	public void speedMomentumBuildsAndLightningThrowSpendsIt(GameTestHelper helper) {
		ServerPlayer p = hero(helper, SuperSpeedHandlers.KEY);
		p.setSprinting(true);
		p.setOnGround(true);
		tick(p, 20);
		helper.assertTrue(SuperSpeedHandlers.momentum(p) > 5f, "sprinting builds Momentum");
		Zombie z = zombieAhead(helper, p, 4.0);
		p.lookAt(net.minecraft.commands.arguments.EntityAnchorArgument.Anchor.EYES, z.getEyePosition());
		setRes(p, SuperSpeedHandlers.KEY, SuperSpeedHandlers.MOMENTUM, 20f);
		AbilityRouter.handleInput(p, 8, true); // N = Lightning Throw
		helper.assertFalse(onCooldown(p, SuperSpeedHandlers.KEY, AbilitySlot.SLOT_8), "needs 30 Momentum");
		float before = z.getHealth();
		setRes(p, SuperSpeedHandlers.KEY, SuperSpeedHandlers.MOMENTUM, 100f);
		AbilityRouter.handleInput(p, 8, true);
		helper.assertTrue(z.getHealth() < before, "the bolt strikes");
		helper.assertTrue(SuperSpeedHandlers.momentum(p) == 0f, "and spends the Momentum");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE, batch = "revamp_a_vortex")
	public void speedVortexDragsMobsIn(GameTestHelper helper) {
		ServerPlayer p = hero(helper, SuperSpeedHandlers.KEY);
		Zombie z = zombieAhead(helper, p, 8.0);
		AbilityRouter.handleInput(p, 5, true); // V = Vortex (hold)
		helper.assertTrue(res(p, SuperSpeedHandlers.KEY, "vortex_ticks") > 0f, "the vortex spins up");
		tick(p, 1);
		helper.assertTrue(z.getDeltaMovement().z < -0.05, "the zombie is dragged toward the eye");
		helper.assertTrue("spin_arms".equals(MutationVisuals.anim(p)), "the spin pose plays");
		AbilityRouter.handleInput(p, 5, false);
		helper.assertTrue(res(p, SuperSpeedHandlers.KEY, "vortex_ticks") == 0f, "release ends it");
		helper.assertTrue(onCooldown(p, SuperSpeedHandlers.KEY, AbilitySlot.SLOT_5), "and starts the cooldown");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE, batch = "revamp_a_phase")
	public void speedPhaseVibratePassesThroughAThinWall(GameTestHelper helper) {
		ServerPlayer p = hero(helper, SuperSpeedHandlers.KEY);
		BlockPos feet = p.blockPosition();
		helper.getLevel().setBlock(feet.offset(0, 0, 1), Blocks.STONE.defaultBlockState(), 2);
		helper.getLevel().setBlock(feet.offset(0, 1, 1), Blocks.STONE.defaultBlockState(), 2);
		double z0 = p.getZ();
		AbilityRouter.handleInput(p, 7, true); // H = Phase Vibrate
		helper.assertTrue(p.getZ() > z0 + 1.2, "you come out the far side of the wall (z " + p.getZ() + " from " + z0 + ")");
		helper.assertTrue(p.level().noCollision(p, p.getBoundingBox()), "somewhere you fit");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE, batch = "revamp_a_overdrive")
	public void speedOverdriveSlowsTheWorldAndCrackles(GameTestHelper helper) {
		ServerPlayer p = hero(helper, SuperSpeedHandlers.KEY);
		Zombie z = zombieAhead(helper, p, 5.0);
		AbilityRouter.handleInput(p, 4, true); // Z = Overdrive
		helper.assertTrue(SuperSpeedHandlers.overdrive(p), "Overdrive runs");
		for (int i = 0; i < 5; i++) {
			p.tickCount = i;
			ExperimentalPowers.serverTick(p);
		}
		helper.assertTrue(z.hasEffect(MobEffects.MOVEMENT_SLOWDOWN), "time slows for the zombie");
		refreshVisuals(p);
		helper.assertTrue(MutationVisuals.hasFlag(p, "p04.crackle"), "lightning crackles over you");
		helper.succeed();
	}

	// ---------------- 12 Super Regeneration ----------------

	@GameTest(template = EMPTY_STRUCTURE, batch = "revamp_a_rage")
	public void regenAdrenalineFuelsBloodRage(GameTestHelper helper) {
		ServerPlayer p = hero(helper, SuperRegenerationHandlers.KEY);
		AbilityRouter.handleInput(p, 7, true); // H = Blood Rage, no Adrenaline yet
		helper.assertFalse(SuperRegenerationHandlers.bloodRaging(p), "Blood Rage needs Adrenaline");
		SuperRegenerationHandlers.gainAdrenaline(p, 12f);
		helper.assertTrue(SuperRegenerationHandlers.adrenaline(p) >= 47f, "damage taken fills Adrenaline (4 per point)");
		double atk = p.getAttributeValue(Attributes.ATTACK_DAMAGE);
		AbilityRouter.handleInput(p, 7, true);
		helper.assertTrue(SuperRegenerationHandlers.bloodRaging(p), "Blood Rage runs");
		helper.assertTrue(SuperRegenerationHandlers.adrenaline(p) == 0f, "and spends all of it");
		helper.assertTrue(p.getAttributeValue(Attributes.ATTACK_DAMAGE) > atk * 1.2, "melee is boosted");
		refreshVisuals(p);
		helper.assertTrue(MutationVisuals.hasFlag(p, "p12.rage"), "the red veins show");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE, batch = "revamp_a_purge")
	public void regenPurgeAndResurrectionKeepTheUnstableMutation(GameTestHelper helper) {
		ServerPlayer p = hero(helper, SuperRegenerationHandlers.KEY);
		p.addEffect(new MobEffectInstance(MobEffects.POISON, 400, 0));
		p.addEffect(new MobEffectInstance(com.projecthero.mod.hero.mutation.ModMobEffects.UNSTABLE_MUTATION, 1200, 0));
		AbilityRouter.handleInput(p, 2, true); // G = Purge
		helper.assertFalse(p.hasEffect(MobEffects.POISON), "Purge removes poison");
		helper.assertTrue(p.hasEffect(com.projecthero.mod.hero.mutation.ModMobEffects.UNSTABLE_MUTATION),
				"but never the unstable mutation");
		boolean dies = ServerLivingEntityEvents.ALLOW_DEATH.invoker().allowDeath(p, p.level().damageSources().generic(), 100f);
		helper.assertFalse(dies, "Resurrection cancels the death");
		helper.assertTrue(p.hasEffect(com.projecthero.mod.hero.mutation.ModMobEffects.UNSTABLE_MUTATION),
				"and the unstable mutation survives it");
		helper.assertTrue(SuperRegenerationHandlers.adrenaline(p) >= SuperRegenerationHandlers.MAX_ADRENALINE - 0.01f,
				"coming back tops Adrenaline off");
		helper.assertTrue(onCooldown(p, SuperRegenerationHandlers.KEY, AbilitySlot.SLOT_4), "Resurrection then recharges");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE, batch = "revamp_a_mend")
	public void regenMendHealsAllies(GameTestHelper helper) {
		ServerPlayer p = hero(helper, SuperRegenerationHandlers.KEY);
		AbilityRouter.handleInput(p, 8, true);
		helper.assertFalse(onCooldown(p, SuperRegenerationHandlers.KEY, AbilitySlot.SLOT_8), "nobody to mend: no cooldown");
		Wolf w = petWolf(helper, p, 2.0, 2.0);
		w.setHealth(3f);
		AbilityRouter.handleInput(p, 8, true); // N = Mend
		helper.assertTrue(w.getHealth() > 8f, "your pet is healed");
		helper.assertTrue(com.projecthero.mod.wolverine.Wolverine.hasSuperRegeneration(p),
				"the Wolverine ascension prerequisite still reads this power");
		helper.succeed();
	}

	// ---------------- 13 Super Durability ----------------

	@GameTest(template = EMPTY_STRUCTURE, batch = "revamp_a_impact")
	public void durabilityImpactReleaseSlamsWhatWasAbsorbed(GameTestHelper helper) {
		ServerPlayer p = hero(helper, SuperDurabilityHandlers.KEY);
		Zombie z = zombieAhead(helper, p, 2.0);
		AbilityRouter.handleInput(p, 7, true); // H = Impact Release, nothing banked
		helper.assertFalse(onCooldown(p, SuperDurabilityHandlers.KEY, AbilitySlot.SLOT_7), "needs Impact first");
		SuperDurabilityHandlers.gainImpact(p, 20f);
		helper.assertTrue(res(p, SuperDurabilityHandlers.KEY, SuperDurabilityHandlers.IMPACT) >= 59f, "blocked damage banks Impact");
		float before = z.getHealth();
		AbilityRouter.handleInput(p, 7, true);
		helper.assertTrue(z.getHealth() < before - 10f, "the release hits hard");
		helper.assertTrue(res(p, SuperDurabilityHandlers.KEY, SuperDurabilityHandlers.IMPACT) == 0f, "and empties the bank");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE, batch = "revamp_a_deflect")
	public void durabilityDeflectionReturnsArrowsToTheShooter(GameTestHelper helper) {
		ServerPlayer p = hero(helper, SuperDurabilityHandlers.KEY);
		Skeleton s = EntityType.SKELETON.create(helper.getLevel());
		s.moveTo(p.getX(), p.getY(), p.getZ() + 8, 180f, 0f);
		s.setNoAi(true);
		helper.getLevel().addFreshEntity(s);
		Arrow arrow = new Arrow(helper.getLevel(), s, new ItemStack(Items.ARROW), null);
		arrow.setPos(p.getX(), p.getY() + 1.2, p.getZ() + 2.0);
		arrow.setDeltaMovement(0, 0, -1.5);
		helper.getLevel().addFreshEntity(arrow);
		AbilityRouter.handleInput(p, 5, true); // V = Deflection (hold)
		tick(p, 1);
		helper.assertTrue(arrow.getOwner() == p, "the arrow becomes yours");
		helper.assertTrue(arrow.getDeltaMovement().z > 1.0, "and flies back toward the skeleton");
		helper.assertTrue("shield_brace".equals(MutationVisuals.anim(p)), "the brace pose holds");
		AbilityRouter.handleInput(p, 5, false);
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE, batch = "revamp_a_taunt")
	public void durabilityTauntAndUnbreakableGold(GameTestHelper helper) {
		ServerPlayer p = hero(helper, SuperDurabilityHandlers.KEY);
		Zombie z = zombieAhead(helper, p, 8.0);
		AbilityRouter.handleInput(p, 8, true); // N = Taunt
		helper.assertTrue(z.getTarget() == p, "the zombie turns on you");
		AbilityRouter.handleInput(p, 4, true); // Z = Unbreakable
		refreshVisuals(p);
		helper.assertTrue(MutationVisuals.hasFlag(p, "p13.steel") && MutationVisuals.hasFlag(p, "p13.gold"),
				"Unbreakable gilds the metal shell");
		helper.succeed();
	}
}
