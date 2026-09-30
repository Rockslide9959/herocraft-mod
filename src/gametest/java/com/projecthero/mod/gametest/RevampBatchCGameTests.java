package com.projecthero.mod.gametest;

import com.projecthero.mod.hero.Ability;
import com.projecthero.mod.hero.AbilityHandlers;
import com.projecthero.mod.hero.AbilityRouter;
import com.projecthero.mod.hero.AbilitySlot;
import com.projecthero.mod.hero.ExperimentalPowers;
import com.projecthero.mod.hero.Power;
import com.projecthero.mod.hero.Powers;
import com.projecthero.mod.hero.power.p07.ElectrokinesisHandlers;
import com.projecthero.mod.hero.power.p14.SonicScreamHandlers;
import com.projecthero.mod.hero.power.p20.EnergyAbsorptionHandlers;
import com.projecthero.mod.hero.power.p21.ShockwaveHandlers;
import com.projecthero.mod.hero.power.p24.WindHandlers;
import com.projecthero.mod.hero.power.p24.WindTornadoEntity;
import com.projecthero.mod.hero.revamp.RevampBatchC;
import com.projecthero.mod.hero.visual.MutationMeters;
import com.projecthero.mod.hero.visual.MutationVisuals;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;

/** v0.13.22 mutation revamp, batch C: regression coverage for the reworked kits. */
public class RevampBatchCGameTests implements FabricGameTest {
	private static final String[] KEYS = { RevampBatchC.ELECTRO, RevampBatchC.SONIC, RevampBatchC.ENERGY, RevampBatchC.SHOCK,
			RevampBatchC.WIND };

	// ---------------- helpers ----------------

	private static ServerPlayer hero(GameTestHelper helper, String key) {
		ServerPlayer p = helper.makeMockServerPlayerInLevel();
		p.setGameMode(GameType.SURVIVAL);
		Power power = Powers.byKey(key);
		ExperimentalPowers.grant(p, power);
		ExperimentalPowers.setActive(p, power);
		Vec3 at = Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(2, 2, 2)));
		p.setPos(at.x, at.y, at.z);
		p.setYRot(0.0f); // facing +Z
		p.setXRot(0.0f);
		p.setYHeadRot(0.0f);
		return p;
	}

	private static Power power(String key) {
		return Powers.byKey(key);
	}

	/** A zombie straight ahead, looked at (same-tick tests only -- it is a Monster). */
	private static Zombie zombieAhead(GameTestHelper helper, ServerPlayer p, int dist) {
		Zombie z = helper.spawn(EntityType.ZOMBIE, new BlockPos(2, 2, 2 + dist));
		z.setNoAi(true);
		p.lookAt(net.minecraft.commands.arguments.EntityAnchorArgument.Anchor.EYES, z.getEyePosition());
		return z;
	}

	/** A no-AI iron golem straight ahead (safe across ticks on any difficulty). */
	private static IronGolem golemAhead(GameTestHelper helper, ServerPlayer p, double dist) {
		IronGolem g = EntityType.IRON_GOLEM.create(helper.getLevel());
		g.moveTo(p.getX(), p.getY(), p.getZ() + dist, 0, 0);
		g.setNoAi(true);
		helper.getLevel().addFreshEntity(g);
		return g;
	}

	private static void press(ServerPlayer p, int slot) {
		AbilityRouter.handleInput(p, slot, true);
	}

	private static void release(ServerPlayer p, int slot) {
		AbilityRouter.handleInput(p, slot, false);
	}

	// ---------------- registry ----------------

	@GameTest(template = EMPTY_STRUCTURE)
	public void batchPowersRegistered(GameTestHelper helper) {
		helper.assertTrue(Powers.count() == 26, "expected 26 powers (Super Durability removed in v0.14.5)");
		for (String key : KEYS) {
			Power p = power(key);
			helper.assertTrue(p != null && p.abilities().size() == 8, key + " must define 8 abilities");
			for (AbilitySlot slot : AbilitySlot.values()) {
				Ability a = p.ability(slot);
				helper.assertTrue(a != null && a.slot() == slot, key + " slot " + slot + " mismapped");
				helper.assertTrue(AbilityHandlers.has(p, a), key + "/" + a.id() + " has no handler");
			}
		}
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void batchVisualsAndMetersRegistered(GameTestHelper helper) {
		for (String flag : new String[] { RevampBatchC.FLAG_CHARGED, RevampBatchC.FLAG_ECHO, RevampBatchC.FLAG_ABSORB,
				RevampBatchC.FLAG_HANDS, RevampBatchC.FLAG_KINETIC, RevampBatchC.FLAG_AIR }) {
			helper.assertTrue(MutationVisuals.registeredFlags().contains(flag), "visual flag " + flag + " registered");
		}
		helper.assertTrue(MutationMeters.get(RevampBatchC.ELECTRO, "ecell") != null, "Electrokinesis charge meter");
		helper.assertTrue(MutationMeters.get(RevampBatchC.SONIC, "voice") != null, "Voice meter");
		helper.assertTrue(MutationMeters.get(RevampBatchC.ENERGY, "energy") != null, "Energy meter");
		helper.assertTrue(MutationMeters.get(RevampBatchC.SHOCK, "charge") != null, "Kinetic gauge");
		helper.assertTrue(MutationMeters.get(RevampBatchC.WIND, "tailwind") != null, "Wind meter");
		helper.succeed();
	}

	// ---------------- 07 Electrokinesis ----------------

	@GameTest(template = EMPTY_STRUCTURE)
	public void electroBoltAddsAStaticStackAndAnimates(GameTestHelper helper) {
		ServerPlayer p = hero(helper, RevampBatchC.ELECTRO);
		Zombie z = zombieAhead(helper, p, 4);
		press(p, 1);
		helper.assertTrue(z.getHealth() < z.getMaxHealth(), "Electric Bolt damages");
		helper.assertTrue(ElectrokinesisHandlers.stacks(z) == 1, "and leaves one static stack, got " + ElectrokinesisHandlers.stacks(z));
		helper.assertTrue("point_right".equals(MutationVisuals.anim(p)), "and plays its pose");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void electroThreeStacksStun(GameTestHelper helper) {
		ServerPlayer p = hero(helper, RevampBatchC.ELECTRO);
		Zombie z = zombieAhead(helper, p, 4);
		ElectrokinesisHandlers.addStack(p, z, 1);
		ElectrokinesisHandlers.addStack(p, z, 1);
		helper.assertTrue(z.getEffect(MobEffects.MOVEMENT_SLOWDOWN).getAmplifier() < 9, "two stacks only slow");
		ElectrokinesisHandlers.addStack(p, z, 1);
		helper.assertTrue(ElectrokinesisHandlers.stacks(z) == ElectrokinesisHandlers.MAX_STACKS, "capped at three");
		helper.assertTrue(z.getEffect(MobEffects.MOVEMENT_SLOWDOWN).getAmplifier() >= 9, "three stacks stun");
		ElectrokinesisHandlers.addStack(p, z, 1);
		helper.assertTrue(ElectrokinesisHandlers.stacks(z) == 3, "still three");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE, batch = "revamp_c_chain")
	public void electroChainJumpsFurtherToAStackedTarget(GameTestHelper helper) {
		ServerPlayer p = hero(helper, RevampBatchC.ELECTRO);
		Zombie first = zombieAhead(helper, p, 3);
		Zombie far = helper.spawn(EntityType.ZOMBIE, new BlockPos(2, 2, 2 + 3 + 12)); // 12 blocks past the first
		far.setNoAi(true);
		var chainUnstacked = ElectrokinesisHandlers.chainLightning(p, first, 6);
		helper.assertFalse(chainUnstacked.contains(far), "an unstacked target 12 blocks on is out of hop range");
		ElectrokinesisHandlers.consumeStacks(first);
		ElectrokinesisHandlers.consumeStacks(far);
		ElectrokinesisHandlers.addStack(p, far, 1);
		var chain = ElectrokinesisHandlers.chainLightning(p, first, 6);
		helper.assertTrue(chain.contains(far), "a stacked target 12 blocks on is reached");
		helper.assertTrue(ElectrokinesisHandlers.stacks(far) == 2, "and gains a stack");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE, batch = "revamp_c_overcharge")
	public void electroOverchargeDetonatesStacks(GameTestHelper helper) {
		ServerPlayer p = hero(helper, RevampBatchC.ELECTRO);
		Zombie z = zombieAhead(helper, p, 4);
		press(p, 7); // H with nothing stacked: refused, no cooldown
		helper.assertTrue(ExperimentalPowers.cooldownReady(p, power(RevampBatchC.ELECTRO),
				power(RevampBatchC.ELECTRO).ability(AbilitySlot.SLOT_7)), "Overcharge with no stacks does nothing");
		ElectrokinesisHandlers.addStack(p, z, 3);
		z.invulnerableTime = 0;
		float before = z.getHealth();
		press(p, 7);
		helper.assertTrue(z.getHealth() < before, "Overcharge hurts the stacked target");
		helper.assertTrue(ElectrokinesisHandlers.stacks(z) == 0, "and consumes its stacks");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE, batch = "revamp_c_ion")
	public void electroIonPullYanksStackedEnemies(GameTestHelper helper) {
		ServerPlayer p = hero(helper, RevampBatchC.ELECTRO);
		IronGolem g = golemAhead(helper, p, 9.0);
		ElectrokinesisHandlers.addStack(p, g, 1);
		press(p, 8);
		helper.assertTrue(g.getDeltaMovement().z < -0.3, "Ion Pull yanks the stacked golem toward you, v=" + g.getDeltaMovement());
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE, batch = "revamp_c_boltform")
	public void electroBoltFormMovesThePlayer(GameTestHelper helper) {
		ServerPlayer p = hero(helper, RevampBatchC.ELECTRO);
		p.setPos(p.getX(), p.getY() + 2.0, p.getZ()); // clear of the floor
		Vec3 start = p.position();
		press(p, 3);
		helper.assertTrue(p.position().distanceTo(start) >= 1.5, "Bolt Form carries you along your aim, moved "
				+ p.position().distanceTo(start));
		helper.assertTrue(ExperimentalPowers.getResource(p, power(RevampBatchC.ELECTRO), "ecell") < ElectrokinesisHandlers.MAX_CHARGE,
				"and spends charge");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void electroChargedModeRaisesItsOverlayFlag(GameTestHelper helper) {
		ServerPlayer p = hero(helper, RevampBatchC.ELECTRO);
		press(p, 6);
		helper.succeedWhen(() -> helper.assertTrue(MutationVisuals.hasFlag(p, RevampBatchC.FLAG_CHARGED),
				"Charged Mode shows its crackling shell"));
	}

	// ---------------- 14 Sonic Scream ----------------

	@GameTest(template = EMPTY_STRUCTURE)
	public void sonicScreamsSpendVoice(GameTestHelper helper) {
		ServerPlayer p = hero(helper, RevampBatchC.SONIC);
		Power sonic = power(RevampBatchC.SONIC);
		Zombie z = zombieAhead(helper, p, 3);
		press(p, 1);
		helper.assertTrue(z.getHealth() < z.getMaxHealth(), "Sonic Blast hits");
		float voice = ExperimentalPowers.getResource(p, sonic, "voice");
		helper.assertTrue(Math.abs(voice - (SonicScreamHandlers.VOICE_MAX - SonicScreamHandlers.COST_BLAST)) < 0.01f,
				"and spends Voice, left " + voice);
		ExperimentalPowers.setResource(p, sonic, "voice", 5.0f, SonicScreamHandlers.VOICE_MAX);
		press(p, 2);
		helper.assertTrue(ExperimentalPowers.cooldownReady(p, sonic, sonic.ability(AbilitySlot.SLOT_2)),
				"Focused Scream without enough Voice does not fire");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE, batch = "revamp_c_barrier")
	public void sonicSoundBarrierShredsProjectiles(GameTestHelper helper) {
		ServerPlayer p = hero(helper, RevampBatchC.SONIC);
		press(p, 7);
		SonicScreamHandlers.Barrier b = SonicScreamHandlers.barrier(p);
		helper.assertTrue(b != null, "Sound Barrier is up");
		Arrow arrow = new Arrow(EntityType.ARROW, helper.getLevel());
		arrow.moveTo(b.centre.x, b.centre.y, b.centre.z + 0.4, 0, 0);
		arrow.setDeltaMovement(0, 0, -0.2);
		arrow.setNoGravity(true);
		helper.getLevel().addFreshEntity(arrow);
		helper.succeedWhen(() -> helper.assertTrue(arrow.isRemoved(), "the arrow is shredded by the wall"));
	}

	@GameTest(template = EMPTY_STRUCTURE, batch = "revamp_c_disorient")
	public void sonicDisorientMakesMobsLoseTheirTarget(GameTestHelper helper) {
		ServerPlayer p = hero(helper, RevampBatchC.SONIC);
		IronGolem g = golemAhead(helper, p, 4.0);
		net.minecraft.world.entity.animal.Pig bait = EntityType.PIG.create(helper.getLevel());
		bait.moveTo(p.getX() + 2, p.getY(), p.getZ(), 0, 0);
		helper.getLevel().addFreshEntity(bait);
		g.setTarget(bait);
		press(p, 8);
		helper.assertTrue(g.getTarget() == null, "Disorient clears the mob's target");
		helper.assertTrue(SonicScreamHandlers.isDisoriented(g), "and keeps it disoriented");
		helper.assertTrue(g.hasEffect(MobEffects.CONFUSION), "with nausea");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void sonicEcholocationPings(GameTestHelper helper) {
		ServerPlayer p = hero(helper, RevampBatchC.SONIC);
		Power sonic = power(RevampBatchC.SONIC);
		press(p, 6);
		helper.assertTrue(SonicScreamHandlers.echoActive(p), "Echolocation is on");
		helper.assertTrue(ExperimentalPowers.getResource(p, sonic, "echo_until") > p.level().getGameTime(),
				"and pings (the client glow window is open)");
		helper.succeed();
	}

	// ---------------- 20 Energy Absorption ----------------

	@GameTest(template = EMPTY_STRUCTURE)
	public void energyElementalDamageSetsTheElement(GameTestHelper helper) {
		ServerPlayer p = hero(helper, RevampBatchC.ENERGY);
		Power energy = power(RevampBatchC.ENERGY);
		ExperimentalPowers.setResource(p, energy, "energy", 0, EnergyAbsorptionHandlers.MAX);
		float left = EnergyAbsorptionHandlers.absorb(p, p.damageSources().inFire(), 10.0f);
		helper.assertTrue(Math.abs(left - 0.4f) < 0.01f, "fire soaks 60%, " + left + " left");
		helper.assertTrue(EnergyAbsorptionHandlers.element(p) == EnergyAbsorptionHandlers.FIRE, "and sets the element to fire");
		helper.assertTrue(Math.abs(ExperimentalPowers.getResource(p, energy, "energy") - 6.0f) < 0.01f, "6 energy stored");
		EnergyAbsorptionHandlers.absorb(p, p.damageSources().explosion(null, null), 10.0f);
		helper.assertTrue(EnergyAbsorptionHandlers.element(p) == EnergyAbsorptionHandlers.EXPLOSION, "an explosion re-elements it");
		float phys = EnergyAbsorptionHandlers.absorb(p, p.damageSources().generic(), 10.0f);
		helper.assertTrue(Math.abs(phys - 0.75f) < 0.01f, "physical soaks 25%");
		helper.assertTrue(EnergyAbsorptionHandlers.element(p) == EnergyAbsorptionHandlers.EXPLOSION, "and leaves the element alone");
		EnergyAbsorptionHandlers.absorb(p, p.damageSources().magic(), 4.0f);
		helper.assertTrue(EnergyAbsorptionHandlers.element(p) == EnergyAbsorptionHandlers.MAGIC, "magic");
		EnergyAbsorptionHandlers.absorb(p, p.damageSources().lightningBolt(), 4.0f);
		helper.assertTrue(EnergyAbsorptionHandlers.element(p) == EnergyAbsorptionHandlers.LIGHTNING, "lightning");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void energyFireBlastIgnites(GameTestHelper helper) {
		ServerPlayer p = hero(helper, RevampBatchC.ENERGY);
		Power energy = power(RevampBatchC.ENERGY);
		ExperimentalPowers.setResource(p, energy, "energy", 200, EnergyAbsorptionHandlers.MAX);
		EnergyAbsorptionHandlers.setElement(p, EnergyAbsorptionHandlers.FIRE);
		Zombie z = zombieAhead(helper, p, 4);
		press(p, 1);
		release(p, 1);
		helper.assertTrue(z.getHealth() < z.getMaxHealth(), "the blast hits");
		helper.assertTrue(z.getRemainingFireTicks() > 0, "a fire-element blast ignites");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void energyRedirectFiresAMeleeHitBack(GameTestHelper helper) {
		ServerPlayer p = hero(helper, RevampBatchC.ENERGY);
		Power energy = power(RevampBatchC.ENERGY);
		ExperimentalPowers.setResource(p, energy, "energy", 100, EnergyAbsorptionHandlers.MAX);
		Zombie z = zombieAhead(helper, p, 2);
		helper.assertFalse(EnergyAbsorptionHandlers.tryRedirect(p, p.damageSources().mobAttack(z), 5.0f), "no window, no catch");
		press(p, 7);
		helper.assertTrue(EnergyAbsorptionHandlers.tryRedirect(p, p.damageSources().mobAttack(z), 5.0f), "the melee hit is caught");
		helper.assertTrue(z.getHealth() < z.getMaxHealth(), "and fired back at the attacker");
		helper.assertFalse(EnergyAbsorptionHandlers.tryRedirect(p, p.damageSources().mobAttack(z), 5.0f), "one catch per window");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void energyEmpowerDependsOnTheElement(GameTestHelper helper) {
		ServerPlayer p = hero(helper, RevampBatchC.ENERGY);
		Power energy = power(RevampBatchC.ENERGY);
		ExperimentalPowers.setResource(p, energy, "energy", 150, EnergyAbsorptionHandlers.MAX);
		EnergyAbsorptionHandlers.setElement(p, EnergyAbsorptionHandlers.LIGHTNING);
		press(p, 8);
		helper.assertTrue(ExperimentalPowers.getResource(p, energy, "energy") <= 50.01f, "Empower spends 100");
		helper.assertTrue(p.hasEffect(MobEffects.DIG_SPEED) && p.getEffect(MobEffects.MOVEMENT_SPEED).getAmplifier() >= 1,
				"a lightning Empower gives Speed II and Haste");
		helper.succeed();
	}

	// ---------------- 21 Shockwave ----------------

	@GameTest(template = EMPTY_STRUCTURE)
	public void shockwaveStoresKineticOnly(GameTestHelper helper) {
		ServerPlayer p = hero(helper, RevampBatchC.SHOCK);
		Power shock = power(RevampBatchC.SHOCK);
		Zombie z = zombieAhead(helper, p, 2);
		float f = ShockwaveHandlers.onIncoming(p, p.damageSources().mobAttack(z), 10.0f);
		helper.assertTrue(Math.abs(f - 0.85f) < 0.01f, "melee is dampened 15%");
		helper.assertTrue(Math.abs(ExperimentalPowers.getResource(p, shock, "charge") - 10.0f) < 0.01f, "and banked");
		ShockwaveHandlers.onIncoming(p, p.damageSources().fall(), 5.0f);
		helper.assertTrue(Math.abs(ExperimentalPowers.getResource(p, shock, "charge") - 16.0f) < 0.01f, "a fall banks x1.2");
		float fire = ShockwaveHandlers.onIncoming(p, p.damageSources().inFire(), 5.0f);
		helper.assertTrue(fire >= 0.999f && Math.abs(ExperimentalPowers.getResource(p, shock, "charge") - 16.0f) < 0.01f,
				"fire is not kinetic: nothing stored, nothing dampened");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void shockwaveParryNegatesAndStoresDouble(GameTestHelper helper) {
		ServerPlayer p = hero(helper, RevampBatchC.SHOCK);
		Power shock = power(RevampBatchC.SHOCK);
		Zombie z = zombieAhead(helper, p, 2);
		press(p, 8);
		helper.assertTrue(ShockwaveHandlers.parryOpen(p), "Kinetic Parry opens its window");
		float f = ShockwaveHandlers.onIncoming(p, p.damageSources().mobAttack(z), 6.0f);
		helper.assertTrue(f < 0.0f, "the melee hit is negated");
		helper.assertTrue(ExperimentalPowers.getResource(p, shock, "charge") >= 11.99f, "and stored at double");
		helper.assertTrue(z.getHealth() < z.getMaxHealth(), "the attacker is hit back");
		helper.assertFalse(ShockwaveHandlers.parryOpen(p), "the window closes");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE, batch = "revamp_c_wave", timeoutTicks = 120)
	public void shockwaveGroundWaveThenAftershock(GameTestHelper helper) {
		ServerPlayer p = hero(helper, RevampBatchC.SHOCK);
		IronGolem g = golemAhead(helper, p, 4.0); // v0.14.4: 6 put it in the 8x8x8 test cage's barrier wall (z 8.5)
		float max = g.getMaxHealth();
		float[] afterWave = new float[1];
		press(p, 2);
		// v0.14.4: wait for each hit rather than checking at fixed ticks (the fixed-tick form flaked on a busy server)
		helper.startSequence()
				// wait for the wave to LAND first: the golem can also lose health to other things (e.g. standing in the
				// test's barrier wall), so "it was hurt" alone does not prove the wave got there
				.thenWaitUntil(() -> helper.assertTrue(ShockwaveHandlers.lastImpact(p) != null, "the ground wave lands"))
				.thenExecute(() -> {
					helper.assertTrue(g.getHealth() < max, "the ground wave reaches the golem");
					afterWave[0] = g.getHealth();
					press(p, 7);
				})
				.thenWaitUntil(() -> helper.assertTrue(g.getHealth() < afterWave[0], "Aftershock erupts where the wave landed"))
				.thenSucceed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void shockwaveDetonationNeedsAFullGauge(GameTestHelper helper) {
		ServerPlayer p = hero(helper, RevampBatchC.SHOCK);
		Power shock = power(RevampBatchC.SHOCK);
		ExperimentalPowers.setResource(p, shock, "charge", 50, ShockwaveHandlers.MAX_CHARGE);
		press(p, 4);
		helper.assertTrue(ExperimentalPowers.getResource(p, shock, "kd_charging") < 0.5f, "not with half a gauge");
		release(p, 4);
		ExperimentalPowers.setResource(p, shock, "charge", ShockwaveHandlers.MAX_CHARGE, ShockwaveHandlers.MAX_CHARGE);
		press(p, 4);
		helper.assertTrue(ExperimentalPowers.getResource(p, shock, "kd_charging") > 0.5f, "a full gauge starts the charge-up");
		release(p, 4);
		helper.succeed();
	}

	// ---------------- 24 Wind ----------------

	@GameTest(template = EMPTY_STRUCTURE, batch = "revamp_c_updraft")
	public void windUpdraftOnTheGround(GameTestHelper helper) {
		ServerPlayer p = hero(helper, RevampBatchC.WIND);
		Power wind = power(RevampBatchC.WIND);
		p.setOnGround(true);
		press(p, 3);
		helper.assertTrue(WindHandlers.updrafts().stream().anyMatch(u -> u.owner().equals(p.getUUID())), "an updraft is raised");
		helper.assertTrue(ExperimentalPowers.getResource(p, wind, "tailwind") < WindHandlers.MAX_WIND, "for Wind");
		helper.assertFalse(ExperimentalPowers.cooldownReady(p, wind, wind.ability(AbilitySlot.SLOT_3)), "on cooldown");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void windGlideTogglesInTheAir(GameTestHelper helper) {
		ServerPlayer p = hero(helper, RevampBatchC.WIND);
		p.setOnGround(false);
		press(p, 3);
		helper.assertTrue(WindHandlers.isGliding(p), "X in the air starts a glide");
		helper.assertTrue(WindHandlers.airShell(p), "with the air shell");
		press(p, 3);
		helper.assertFalse(WindHandlers.isGliding(p), "and again stops it");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE, batch = "revamp_c_tornado")
	public void windTornadoCanBeRiddenAndDismissed(GameTestHelper helper) {
		ServerPlayer p = hero(helper, RevampBatchC.WIND);
		press(p, 7);
		helper.assertTrue(p.getVehicle() instanceof WindTornadoEntity, "H summons a tornado and mounts you on it");
		WindTornadoEntity t = (WindTornadoEntity) p.getVehicle();
		helper.assertTrue(p.getUUID().equals(t.owner()), "it is yours");
		helper.assertTrue(t.life() <= WindTornadoEntity.MAX_LIFE, "with a bounded lifetime");
		press(p, 7);
		helper.assertTrue(p.getVehicle() == null && t.isRemoved(), "H again blows it out");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE, batch = "revamp_c_vacuum", timeoutTicks = 80)
	public void windVacuumSuffocates(GameTestHelper helper) {
		ServerPlayer p = hero(helper, RevampBatchC.WIND);
		IronGolem g = golemAhead(helper, p, 5.0);
		press(p, 8);
		helper.assertTrue(WindHandlers.vacuumActive(p), "Vacuum runs");
		helper.succeedWhen(() -> helper.assertTrue(g.getHealth() < g.getMaxHealth(), "and suffocates what it drags in"));
	}
}
