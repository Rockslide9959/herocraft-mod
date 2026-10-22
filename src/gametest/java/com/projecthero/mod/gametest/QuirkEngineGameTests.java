package com.projecthero.mod.gametest;

import com.projecthero.mod.hero.AbilitySlot;
import com.projecthero.mod.quirk.Quirk;
import com.projecthero.mod.quirk.QuirkAbilityManager;
import com.projecthero.mod.quirk.QuirkKits;
import com.projecthero.mod.quirk.Quirks;
import com.projecthero.mod.quirk.data.QuirkState;
import com.projecthero.mod.quirk.engine.Engine;
import com.projecthero.mod.quirk.engine.EngineConfig;
import com.projecthero.mod.quirk.engine.EngineMoves;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/**
 * v0.16.7 Engine (Tenya Iida / Ingenium), revamped: the gears (speed, momentum, heat), Recipro inside the mode, the one dash,
 * every kick and the stall. Mock players are not ticked by the server, so tests that run over time
 * drive the Quirk tick themselves ({@link #pump}).
 */
public class QuirkEngineGameTests implements FabricGameTest {
	private static final float EPS = 0.6f;

	// ---------------------------------------------------------------- helpers

	@SuppressWarnings("removal")
	private static ServerPlayer engine(GameTestHelper helper, double x, double y, double z) {
		ServerPlayer p = helper.makeMockServerPlayerInLevel();
		p.setGameMode(GameType.SURVIVAL);
		Vec3 at = helper.absoluteVec(new Vec3(x, y, z));
		p.moveTo(at.x, at.y, at.z, 0.0f, 0.0f);
		p.setYHeadRot(0f);
		p.yBodyRot = 0f;
		try {
			java.lang.reflect.Field f = ServerPlayer.class.getDeclaredField("spawnInvulnerableTime");
			f.setAccessible(true);
			f.setInt(p, 0);
		} catch (ReflectiveOperationException e) {
			throw new IllegalStateException(e);
		}
		helper.assertTrue(Quirks.grant(p, Quirk.ENGINE), "Engine is granted");
		Quirks.edit(p, n -> {
			n.abilityReadyAt.clear();
			n.animId = QuirkState.ANIM_NONE;
		});
		return p;
	}

	private static void pump(GameTestHelper helper, ServerPlayer p) {
		helper.onEachTick(() -> {
			p.tickCount++;
			Quirks.tick(p);
		});
	}

	private static void floor(GameTestHelper helper) {
		for (int x = 0; x <= 7; x++) {
			for (int z = 0; z <= 7; z++) {
				helper.getLevel().setBlock(helper.absolutePos(new BlockPos(x, 1, z)), Blocks.STONE.defaultBlockState(), 3);
			}
		}
	}

	private static <T extends Mob> T mob(GameTestHelper helper, EntityType<T> type, double x, double y, double z, double health) {
		helper.getLevel().getServer().setDifficulty(Difficulty.NORMAL, true);
		T m = type.create(helper.getLevel());
		Vec3 at = helper.absoluteVec(new Vec3(x, y, z));
		m.moveTo(at.x, at.y, at.z, 180.0f, 0.0f);
		m.setNoAi(true);
		m.setPersistenceRequired();
		m.getAttribute(Attributes.MAX_HEALTH).setBaseValue(health);
		m.setHealth((float) health);
		if (m.getAttribute(Attributes.ARMOR) != null) {
			m.getAttribute(Attributes.ARMOR).setBaseValue(0.0);
		}
		m.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.CARVED_PUMPKIN));
		helper.getLevel().addFreshEntity(m);
		return m;
	}

	private static double mod(ServerPlayer p, net.minecraft.core.Holder<net.minecraft.world.entity.ai.attributes.Attribute> a, ResourceLocation id) {
		AttributeInstance inst = p.getAttribute(a);
		AttributeModifier m = inst == null ? null : inst.getModifier(id);
		return m == null ? 0.0 : m.amount();
	}

	private static float taken(LivingEntity e, double max) {
		return (float) max - e.getHealth();
	}

	// ---------------------------------------------------------------- passive / stall

	/** Drives the Quirk tick, moving him {@code step} blocks along +Z each tick (sprinting), so the server sees a run. */
	private static void run(GameTestHelper helper, ServerPlayer p, double step) {
		p.setSprinting(true);
		helper.onEachTick(() -> {
			p.setPos(p.getX(), p.getY(), p.getZ() + step);
			p.setOnGround(true);
			p.tickCount++;
			Quirks.tick(p);
		});
	}

	// ---------------------------------------------------------------- the kit / passive

	@GameTest(template = EMPTY_STRUCTURE)
	public void engineKitIsTheModeOneDashAndKicks(GameTestHelper helper) {
		var kit = QuirkKits.of(Quirk.ENGINE);
		helper.assertTrue(kit.idsOf(AbilitySlot.SLOT_5)[0].equals(EngineConfig.SHIFT_UP) && kit.idsOf(AbilitySlot.SLOT_5)[1].equals(EngineConfig.SHIFT_DOWN),
				"V / Shift+V are the gears");
		helper.assertTrue(kit.idsOf(AbilitySlot.SLOT_3)[0].equals(EngineConfig.DASH), "X is the one dash");
		java.util.Set<String> ids = new java.util.HashSet<>();
		for (AbilitySlot slot : new AbilitySlot[] { AbilitySlot.SLOT_1, AbilitySlot.SLOT_2, AbilitySlot.SLOT_3, AbilitySlot.SLOT_4, AbilitySlot.SLOT_5,
				AbilitySlot.SLOT_6 }) {
			String[] two = kit.idsOf(slot);
			helper.assertTrue(two.length == 2, slot + " has a tap and a Shift move");
			for (String id : two) {
				helper.assertTrue(ids.add(id), id + " appears once");
				helper.assertTrue(kit.maxCooldown(id) > 0, id + " has a cooldown");
			}
		}
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 40)
	public void stepAndGearSpeedComeOffOnRevoke(GameTestHelper helper) {
		ServerPlayer p = engine(helper, 4.5, 2.0, 4.5);
		Quirks.tick(p);
		helper.assertTrue(Math.abs(mod(p, Attributes.STEP_HEIGHT, EngineMoves.STEP_ID) - EngineConfig.STEP_BONUS) < 1e-6, "one-block step");
		helper.assertTrue(mod(p, Attributes.MOVEMENT_SPEED, EngineMoves.GEAR_ID) == 0.0, "no speed in neutral");
		EngineMoves.shiftUp(p);
		QuirkKits.of(Quirk.ENGINE).tick(p);
		helper.assertTrue(mod(p, Attributes.MOVEMENT_SPEED, EngineMoves.GEAR_ID) > 0.0, "gear 1 is faster");
		Quirks.revoke(p);
		helper.assertTrue(mod(p, Attributes.MOVEMENT_SPEED, EngineMoves.GEAR_ID) == 0.0, "gear speed removed");
		helper.assertTrue(mod(p, Attributes.STEP_HEIGHT, EngineMoves.STEP_ID) == 0.0, "step assist removed");
		helper.succeed();
	}

	// ---------------------------------------------------------------- the mode: gears, momentum, heat

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 40)
	public void gearsShiftUpAndDownThroughThree(GameTestHelper helper) {
		ServerPlayer p = engine(helper, 4.5, 2.0, 4.5);
		for (int g = 1; g <= 3; g++) {
			helper.assertTrue(EngineMoves.shiftUp(p) == g, "shifted into gear " + g);
		}
		helper.assertTrue(Engine.gear(p) == 3, "the synced gear is 3");
		helper.assertTrue(EngineMoves.shiftDown(p) == 2, "Shift+V drops to gear 2");
		EngineMoves.shiftDown(p);
		EngineMoves.shiftDown(p);
		helper.assertTrue(EngineMoves.shiftDown(p) == -1 && EngineMoves.gear(p) == 0, "neutral is the bottom");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 40)
	public void eachGearIsFasterAndMomentumBuildsTheRest(GameTestHelper helper) {
		helper.assertTrue(EngineMoves.speedBonus(1, 1f, 1.0) < EngineMoves.speedBonus(2, 1f, 1.0)
				&& EngineMoves.speedBonus(2, 1f, 1.0) < EngineMoves.speedBonus(3, 1f, 1.0), "each gear is faster");
		helper.assertTrue(Math.abs(EngineMoves.speedBonus(3, 1f, 1.0) - 1.4) < 1e-6, "Gear 3 flat out is Speed VII (+140%)");
		helper.assertTrue(Math.abs(EngineMoves.speedBonus(1, 1f, 1.0) - 0.6) < 1e-6, "Gear 1 flat out is Speed III");
		helper.assertTrue(EngineMoves.speedBonus(3, 0f, 1.0) < EngineMoves.speedBonus(3, 1f, 1.0) * 0.5, "a fresh gear starts well below its top");
		ServerPlayer p = engine(helper, 4.5, 2.0, 0.5);
		EngineMoves.setGear(p, 2);
		run(helper, p, 0.45);
		helper.runAfterDelay(30, () -> {
			helper.assertTrue(EngineMoves.momentum(p) > 0.6f, "a sprint builds momentum, got " + EngineMoves.momentum(p));
			double m = mod(p, Attributes.MOVEMENT_SPEED, EngineMoves.GEAR_ID);
			helper.assertTrue(m > EngineMoves.speedBonus(2, 0f, 1.0) + 0.1, "the run got faster, modifier " + m);
			helper.assertTrue(Engine.momentum(p) > 0.5f, "momentum is synced");
			helper.succeed();
		});
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 60)
	public void higherGearsHeatFasterAndNeutralCools(GameTestHelper helper) {
		ServerPlayer low = engine(helper, 1.5, 2.0, 0.5);
		ServerPlayer high = engine(helper, 6.5, 2.0, 0.5);
		EngineMoves.setGear(low, 1);
		EngineMoves.setGear(high, 3);
		run(helper, low, 0.4);
		run(helper, high, 0.4);
		helper.runAfterDelay(40, () -> {
			float a = Quirks.meter(low), b = Quirks.meter(high);
			helper.assertTrue(b > a * 3f && b > 6f, "Gear 3 heats much faster: g1 " + a + ", g3 " + b);
			helper.succeed();
		});
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 40)
	public void heatCoolsOnItsOwnFastestInNeutral(GameTestHelper helper) {
		ServerPlayer n = engine(helper, 1.5, 2.0, 4.5);
		ServerPlayer g = engine(helper, 6.5, 2.0, 4.5);
		EngineMoves.setGear(g, 2);
		Quirks.setMeter(n, 50f);
		Quirks.setMeter(g, 50f);
		pump(helper, n);
		pump(helper, g);
		helper.runAfterDelay(21, () -> {
			float cn = 50f - Quirks.meter(n), cg = 50f - Quirks.meter(g);
			helper.assertTrue(cn > 5f && cg > 1.5f && cn > cg * 1.8f, "neutral cools fastest: neutral " + cn + ", in gear " + cg);
			helper.succeed();
		});
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 40)
	public void fullHeatStallsTheEngines(GameTestHelper helper) {
		ServerPlayer p = engine(helper, 4.5, 2.0, 4.5);
		EngineMoves.setGear(p, 3);
		Quirks.setMeter(p, 98f);
		EngineMoves.heat(p, 5f);
		helper.assertTrue(Quirks.overloaded(p), "100 heat stalls");
		helper.assertTrue(p.hasEffect(MobEffects.MOVEMENT_SLOWDOWN) && p.getEffect(MobEffects.MOVEMENT_SLOWDOWN).getAmplifier() == 2, "Slowness III");
		helper.assertTrue(EngineMoves.gear(p) == 0, "back to neutral");
		p.tickCount++;
		helper.runAfterDelay(1, () -> {
			Quirks.tick(p);
			helper.assertTrue(mod(p, Attributes.MOVEMENT_SPEED, EngineMoves.GEAR_ID) == 0.0, "no gear while stalled");
			QuirkAbilityManager.handle(p, AbilitySlot.SLOT_5, true);
			helper.assertTrue(EngineMoves.gear(p) == 0, "can't shift while stalled");
			helper.succeed();
		});
	}

	// ---------------------------------------------------------------- Recipro (inside the mode)

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 40)
	public void gearUpAtTopGearIsReciproBurstThenExtend(GameTestHelper helper) {
		ServerPlayer p = engine(helper, 4.5, 2.0, 4.5);
		EngineMoves.setGear(p, 3);
		EngineMoves.setMomentum(p, 1f);
		EngineMoves.shiftUp(p);
		helper.assertTrue(Engine.running(p, Engine.V_BURST), "V at Gear 3 engages Recipro Burst");
		QuirkKits.of(Quirk.ENGINE).tick(p);
		double burst = mod(p, Attributes.MOVEMENT_SPEED, EngineMoves.GEAR_ID);
		helper.assertTrue(Math.abs(burst - EngineMoves.speedBonus(3, EngineMoves.momentum(p), EngineConfig.BURST_MULT)) < 0.03,
				"double the gear's speed, got " + burst);
		helper.assertTrue(burst > 3.5, "Recipro Burst at Gear 3 is about 4.8x walking speed");
		Quirks.edit(p, n -> n.abilityReadyAt.clear());
		EngineMoves.shiftUp(p);
		helper.assertTrue(Engine.running(p, Engine.V_EXTEND) && !Engine.running(p, Engine.V_BURST), "V again in Burst is Recipro Extend");
		QuirkKits.of(Quirk.ENGINE).tick(p);
		helper.assertTrue(mod(p, Attributes.MOVEMENT_SPEED, EngineMoves.GEAR_ID) > burst, "Extend is faster still");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 40)
	public void reciproBurstEndsCleanlyButExtendStalls(GameTestHelper helper) {
		ServerPlayer a = engine(helper, 1.5, 2.0, 4.5);
		ServerPlayer b = engine(helper, 6.5, 2.0, 4.5);
		EngineMoves.setGear(a, 3);
		EngineMoves.setGear(b, 3);
		EngineMoves.burst(a);
		EngineMoves.extend(b);
		pump(helper, a);
		pump(helper, b);
		helper.runAfterDelay(2, () -> {
			EngineMoves.shiftDown(a); // ends the Burst
			EngineMoves.shiftDown(b); // ends the Extend -- into its stall
		});
		helper.runAfterDelay(6, () -> {
			helper.assertTrue(!Engine.recipro(a) && !Quirks.overloaded(a), "Burst ends with no stall");
			helper.assertTrue(Quirks.overloaded(b), "Extend ends in the stall");
			helper.assertTrue(b.hasEffect(MobEffects.MOVEMENT_SLOWDOWN) && b.getEffect(MobEffects.MOVEMENT_SLOWDOWN).getAmplifier() == 2, "Slowness III");
			helper.assertTrue(Quirks.overloadLeft(b) > EngineConfig.EXTEND_STALL_TICKS - 10, "a 6 s stall");
			helper.succeed();
		});
	}

	// ---------------------------------------------------------------- X: the one dash / Shift+X

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 40)
	public void reciproDashGoesFartherInHigherGears(GameTestHelper helper) {
		floor(helper);
		ServerPlayer p = engine(helper, 4.5, 2.0, 0.5);
		EngineMoves.dash(p);
		double neutral = EngineMoves.dashLength(p);
		Quirks.edit(p, n -> n.abilityReadyAt.clear());
		EngineMoves.setGear(p, 3);
		EngineMoves.dash(p);
		double top = EngineMoves.dashLength(p);
		helper.assertTrue(Math.abs(neutral - EngineConfig.DASH_DISTANCE) < 0.6, "7 blocks in neutral, got " + neutral);
		helper.assertTrue(Math.abs(EngineMoves.dashDistance(3) - 13.0) < 1e-6 && top >= neutral, "13 blocks in Gear 3 (stopped by the test area edge here): " + top);
		helper.assertTrue(p.getDeltaMovement().z > 1.0, "thrown forward");
		helper.assertTrue(!Quirks.ready(p, EngineConfig.DASH), "cooldown");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void risingKickLaunchesTheTargetUp(GameTestHelper helper) {
		floor(helper);
		ServerPlayer p = engine(helper, 4.5, 2.0, 2.5);
		Zombie z = mob(helper, EntityType.ZOMBIE, 4.5, 2.0, 4.5, 100);
		helper.assertTrue(EngineMoves.risingKick(p) == z, "it found the zombie");
		helper.assertTrue(Math.abs(taken(z, 100) - EngineConfig.RISING_DAMAGE) < EPS, "10 damage, took " + taken(z, 100));
		helper.assertTrue(z.getDeltaMovement().y > 1.0, "launched straight up");
		helper.succeed();
	}

	// ---------------------------------------------------------------- R / G / Z / C: kicks

	@GameTest(template = EMPTY_STRUCTURE)
	public void reciproKickScalesWithSpeed(GameTestHelper helper) {
		floor(helper);
		ServerPlayer p = engine(helper, 4.5, 2.0, 2.5);
		Zombie slow = mob(helper, EntityType.ZOMBIE, 4.5, 2.0, 4.5, 100);
		EngineMoves.setSpeed(p, 0.0);
		EngineMoves.kick(p);
		float standing = taken(slow, 100);
		helper.assertTrue(Math.abs(standing - EngineConfig.KICK_DAMAGE) < EPS, "5 standing, took " + standing);
		slow.discard();
		Zombie fast = mob(helper, EntityType.ZOMBIE, 4.5, 2.0, 4.5, 100);
		Quirks.edit(p, n -> n.abilityReadyAt.clear());
		EngineMoves.setSpeed(p, 20.0);
		EngineMoves.kick(p);
		float running = taken(fast, 100);
		helper.assertTrue(Math.abs(running - EngineMoves.kickDamage(20.0)) < EPS && running > 15f, "about 16 at 20 b/s, took " + running);
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void spinKickHitsAllAround(GameTestHelper helper) {
		floor(helper);
		ServerPlayer p = engine(helper, 4.5, 2.0, 4.5);
		Zombie a = mob(helper, EntityType.ZOMBIE, 4.5, 2.0, 6.5, 100);
		Zombie b = mob(helper, EntityType.ZOMBIE, 4.5, 2.0, 2.5, 100);
		helper.assertTrue(EngineMoves.spin(p) == 2, "both sides hit");
		helper.assertTrue(Math.abs(taken(a, 100) - EngineConfig.SPIN_DAMAGE) < EPS, "7 each");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 60)
	public void kickBarrageLandsSixKicks(GameTestHelper helper) {
		floor(helper);
		ServerPlayer p = engine(helper, 4.5, 2.0, 2.5);
		Zombie z = mob(helper, EntityType.ZOMBIE, 4.5, 2.0, 4.0, 200);
		z.getAttribute(Attributes.KNOCKBACK_RESISTANCE).setBaseValue(1.0);
		pump(helper, p);
		EngineMoves.barrage(p);
		helper.runAfterDelay(EngineConfig.BARRAGE_HITS * EngineConfig.BARRAGE_INTERVAL + 4, () -> {
			// i-frames can swallow a kick or two at 3-tick spacing; at least most land
			float t = taken(z, 200);
			helper.assertTrue(t >= EngineConfig.BARRAGE_DAMAGE * 4 - EPS, "most of six kicks of 3.5 land, took " + t);
			helper.succeed();
		});
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 60)
	public void reciproExtendKickChargesThenLandsHeavy(GameTestHelper helper) {
		floor(helper);
		ServerPlayer p = engine(helper, 4.5, 2.0, 2.5);
		Zombie z = mob(helper, EntityType.ZOMBIE, 4.5, 2.0, 4.5, 100);
		pump(helper, p);
		EngineMoves.extendKick(p);
		helper.runAfterDelay(EngineConfig.EXTEND_KICK_CHARGE - 3, () -> helper.assertTrue(z.getHealth() == 100f, "nothing lands while it charges"));
		helper.runAfterDelay(EngineConfig.EXTEND_KICK_CHARGE + 3, () -> {
			float t = taken(z, 100);
			helper.assertTrue(t >= EngineConfig.EXTEND_KICK_DAMAGE - EPS, "a heavy kick of 14+, took " + t);
			helper.succeed();
		});
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void axeKickSlamsDownAndSlows(GameTestHelper helper) {
		floor(helper);
		ServerPlayer p = engine(helper, 4.5, 2.0, 2.5);
		Zombie z = mob(helper, EntityType.ZOMBIE, 4.5, 2.0, 4.5, 100);
		EngineMoves.axeKick(p);
		helper.assertTrue(z.getHealth() == 100f, "the heel is still up");
		helper.assertTrue(EngineMoves.axeLandNow(p) == 1, "the heel lands on it");
		helper.assertTrue(Math.abs(taken(z, 100) - EngineConfig.AXE_DAMAGE) < EPS, "12, took " + taken(z, 100));
		helper.assertTrue(z.hasEffect(MobEffects.MOVEMENT_SLOWDOWN), "slowed");
		helper.assertTrue(z.getDeltaMovement().y < -0.3, "slammed down");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void sweepKickTripsTheFrontNotBehind(GameTestHelper helper) {
		floor(helper);
		ServerPlayer p = engine(helper, 4.5, 2.0, 4.5);
		Zombie front = mob(helper, EntityType.ZOMBIE, 5.5, 2.0, 6.5, 100);
		Zombie behind = mob(helper, EntityType.ZOMBIE, 4.5, 2.0, 1.5, 100);
		helper.assertTrue(EngineMoves.sweepKick(p) == 1, "only the front is swept");
		helper.assertTrue(Math.abs(taken(front, 100) - EngineConfig.SWEEP_DAMAGE) < EPS, "7");
		helper.assertTrue(front.getDeltaMovement().y > 0.3 && front.hasEffect(MobEffects.MOVEMENT_SLOWDOWN), "tripped up");
		helper.assertTrue(behind.getHealth() == 100f, "behind him is safe");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 40)
	public void reciproTurboStrikesThePathThenStalls(GameTestHelper helper) {
		floor(helper);
		ServerPlayer p = engine(helper, 4.5, 2.0, 0.5);
		Zombie z = mob(helper, EntityType.ZOMBIE, 4.5, 2.0, 4.0, 100);
		Pig pig = mob(helper, EntityType.PIG, 1.5, 2.0, 4.0, 10);
		pump(helper, p);
		EngineMoves.turbo(p);
		helper.runAfterDelay(EngineConfig.TURBO_TICKS + 3, () -> {
			helper.assertTrue(EngineMoves.turboHit(p).contains(z.getId()), "the zombie in the path was hit");
			helper.assertTrue(Math.abs(taken(z, 100) - EngineConfig.TURBO_DAMAGE) < EPS, "18 damage");
			helper.assertTrue(pig.getHealth() == 10f, "a pig 3 blocks off the path is left alone");
			helper.assertTrue(Quirks.overloaded(p), "the engines red-line and stall");
			helper.succeed();
		});
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 60)
	public void finisherKicksSpinsThenLaunches(GameTestHelper helper) {
		floor(helper);
		ServerPlayer p = engine(helper, 4.5, 2.0, 2.5);
		Zombie z = mob(helper, EntityType.ZOMBIE, 4.5, 2.0, 4.5, 200);
		z.getAttribute(Attributes.KNOCKBACK_RESISTANCE).setBaseValue(1.0);
		pump(helper, p);
		EngineMoves.finisher(p);
		helper.runAfterDelay(EngineConfig.FINISHER_LAUNCH_AT + 3, () -> {
			float t = taken(z, 200);
			float all = EngineConfig.FINISHER_KICK_DAMAGE + EngineConfig.FINISHER_SPIN_DAMAGE + EngineConfig.FINISHER_LAUNCH_DAMAGE;
			helper.assertTrue(Math.abs(t - all) < EPS * 3, "kick + spin + launch = " + all + ", took " + t);
			helper.succeed();
		});
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 40)
	public void runningIntoMobsAtGearTwoBowlsThemOver(GameTestHelper helper) {
		floor(helper);
		ServerPlayer p = engine(helper, 4.5, 2.0, 0.5);
		Zombie z = mob(helper, EntityType.ZOMBIE, 4.5, 2.0, 4.0, 100);
		EngineMoves.setGear(p, 2);
		EngineMoves.setMomentum(p, 1f);
		run(helper, p, 0.5);
		helper.runAfterDelay(10, () -> {
			helper.assertTrue(z.getHealth() < 100f, "the zombie was bowled over");
			helper.succeed();
		});
	}
}
