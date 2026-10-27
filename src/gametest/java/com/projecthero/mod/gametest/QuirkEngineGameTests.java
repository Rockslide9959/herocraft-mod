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
 * v0.16.7 Engine (Tenya Iida / Ingenium), revamped: the gears (20 / 30 / 40 b/s, momentum, heat), Recipro Turbo (the mode:
 * 30 s at 2x, no heat, +50% damage, then a minute's stall; Burst / Extend gone), the one dash (the Turbo Strike in Turbo),
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

	/** Flat-ground sprint speed (blocks a second) a movement-speed attribute value runs at (see SuperSpeedHandlers.BLOCKS_PER_SECOND_PER_SPEED). */
	private static double sprintBps(double attribute) {
		return attribute * 1.3 * com.projecthero.mod.hero.power.p04.SuperSpeedHandlers.BLOCKS_PER_SECOND_PER_SPEED;
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 40)
	public void gearTopSpeedsAre20_30_40BlocksASecond(GameTestHelper helper) {
		double[] want = { 0.0, 20.0, 30.0, 40.0 };
		for (int g = 1; g <= 3; g++) {
			helper.assertTrue(Math.abs(EngineMoves.runBps(g, 1f, 1.0) - want[g]) < 1e-6, "Gear " + g + " top is " + want[g]);
			double bps = EngineConfig.VANILLA_SPRINT_BPS * (1.0 + EngineMoves.speedBonus(g, 1f, 1.0));
			helper.assertTrue(Math.abs(bps - want[g]) < 0.01, "Gear " + g + "'s modifier sprints at " + bps);
		}
		helper.assertTrue(Math.abs(EngineConfig.VANILLA_SPRINT_BPS - 5.612) < 0.01, "vanilla sprint is 5.6 b/s");
		// on a real player: Gear 3 with the run built, the attribute itself sprints at 40 b/s (the modifier is stepped 0.05)
		ServerPlayer p = engine(helper, 4.5, 2.0, 4.5);
		EngineMoves.setGear(p, 3);
		EngineMoves.setMomentum(p, 1f);
		QuirkKits.of(Quirk.ENGINE).tick(p);
		double attr = p.getAttributeValue(Attributes.MOVEMENT_SPEED);
		helper.assertTrue(Math.abs(sprintBps(attr) - 40.0) < 0.5, "Gear 3 sprints at " + sprintBps(attr) + " b/s");
		helper.assertTrue(p.getAttribute(Attributes.MOVEMENT_SPEED).getModifier(EngineMoves.GEAR_ID).operation()
				== AttributeModifier.Operation.ADD_MULTIPLIED_BASE, "an ADD_MULTIPLIED_BASE modifier, like Super Speed's");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 40)
	public void eachGearIsFasterAndMomentumBuildsTheRest(GameTestHelper helper) {
		helper.assertTrue(EngineMoves.speedBonus(1, 1f, 1.0) < EngineMoves.speedBonus(2, 1f, 1.0)
				&& EngineMoves.speedBonus(2, 1f, 1.0) < EngineMoves.speedBonus(3, 1f, 1.0), "each gear is faster");
		helper.assertTrue(EngineMoves.speedBonus(3, 0f, 1.0) < EngineMoves.speedBonus(3, 1f, 1.0) * 0.5, "a fresh gear starts well below its top");
		helper.assertTrue(1f / EngineConfig.MOMENTUM_BUILD[3] <= 30f, "Gear 3 builds to the top in 1.5 s or less");
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
			helper.assertTrue(b > a * 3f && b > 3.5f, "Gear 3 heats much faster: g1 " + a + ", g3 " + b);
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

	// ---------------------------------------------------------------- C: Recipro Turbo (the mode)

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 40)
	public void gearUpAtTopGearDoesNothingMore(GameTestHelper helper) {
		ServerPlayer p = engine(helper, 4.5, 2.0, 4.5);
		EngineMoves.setGear(p, 3);
		helper.assertTrue(EngineMoves.shiftUp(p) == -1 && EngineMoves.gear(p) == 3, "V at Gear 3: Max gear, nothing else");
		helper.assertFalse(Engine.running(p, Engine.V_TURBO), "and no Recipro (Burst / Extend are gone)");
		var kit = QuirkKits.of(Quirk.ENGINE);
		for (AbilitySlot slot : AbilitySlot.values()) {
			for (String id : kit.idsOf(slot)) {
				helper.assertFalse(id.equals("quirk_engine_recipro_burst") || id.equals("quirk_engine_recipro_extend"), "no Recipro Burst / Extend key: " + id);
			}
		}
		helper.assertTrue(kit.maxCooldown("quirk_engine_recipro_burst") == 0 && kit.maxCooldown("quirk_engine_recipro_extend") == 0,
				"Burst / Extend are not moves any more");
		helper.assertTrue(kit.idsOf(AbilitySlot.SLOT_6)[0].equals(EngineConfig.TURBO), "Recipro Turbo is on C");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 40)
	public void reciproTurboIsThirtySecondsAtDoubleSpeed(GameTestHelper helper) {
		ServerPlayer p = engine(helper, 4.5, 2.0, 4.5);
		EngineMoves.setGear(p, 3);
		EngineMoves.setMomentum(p, 1f);
		helper.assertTrue(EngineMoves.turbo(p), "C engages Recipro Turbo");
		helper.assertTrue(Engine.val(p, Engine.V_TURBO) - p.level().getGameTime() == EngineConfig.TURBO_TICKS && EngineConfig.TURBO_TICKS == 600,
				"for 30 s");
		helper.assertTrue(Engine.turbo(p), "Turbo reads as running");
		QuirkKits.of(Quirk.ENGINE).tick(p);
		double m = mod(p, Attributes.MOVEMENT_SPEED, EngineMoves.GEAR_ID);
		helper.assertTrue(Math.abs(m - EngineMoves.speedBonus(3, 1f, EngineConfig.TURBO_MULT)) < 0.03, "double the gear's speed, modifier " + m);
		double bps = sprintBps(p.getAttributeValue(Attributes.MOVEMENT_SPEED));
		helper.assertTrue(Math.abs(bps - 80.0) < 0.6, "Gear 3 in Turbo sprints at 80 b/s, got " + bps);
		// a lower gear in Turbo is double THAT gear
		EngineMoves.setGear(p, 1);
		EngineMoves.setMomentum(p, 1f);
		QuirkKits.of(Quirk.ENGINE).tick(p);
		bps = sprintBps(p.getAttributeValue(Attributes.MOVEMENT_SPEED));
		helper.assertTrue(Math.abs(bps - 40.0) < 0.6, "Gear 1 in Turbo sprints at 40 b/s, got " + bps);
		// neutral: Turbo puts him in Gear 1
		ServerPlayer q = engine(helper, 1.5, 2.0, 4.5);
		EngineMoves.turbo(q);
		helper.assertTrue(EngineMoves.gear(q) == 1, "Turbo from neutral is Gear 1");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 80)
	public void reciproTurboHasNoHeatAndNoStall(GameTestHelper helper) {
		ServerPlayer p = engine(helper, 4.5, 2.0, 0.5);
		EngineMoves.setGear(p, 3);
		EngineMoves.turbo(p);
		Quirks.setMeter(p, 50f);
		run(helper, p, 0.4);
		helper.runAfterDelay(40, () -> {
			helper.assertTrue(Math.abs(Quirks.meter(p) - 50f) < 0.01f, "sprinting at Gear 3 in Turbo builds no heat: " + Quirks.meter(p));
			EngineMoves.heat(p, 80f);
			EngineMoves.kick(p);
			helper.assertTrue(Math.abs(Quirks.meter(p) - 50f) < 0.01f && !Quirks.overloaded(p), "moves add no heat, no stall: " + Quirks.meter(p));
			helper.succeed();
		});
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void reciproTurboHitsHalfAgainHarder(GameTestHelper helper) {
		floor(helper);
		ServerPlayer p = engine(helper, 4.5, 2.0, 2.5);
		Zombie z = mob(helper, EntityType.ZOMBIE, 4.5, 2.0, 4.5, 100);
		EngineMoves.turbo(p);
		EngineMoves.setSpeed(p, 0.0);
		EngineMoves.kick(p);
		float t = taken(z, 100);
		helper.assertTrue(Math.abs(t - EngineConfig.KICK_DAMAGE * 1.5f) < EPS, "a standing kick is 7.5 in Turbo (+50%), took " + t);
		QuirkKits.of(Quirk.ENGINE).tick(p);
		helper.assertTrue(Math.abs(mod(p, Attributes.ATTACK_DAMAGE, EngineMoves.TURBO_DAMAGE_ID) - 0.5) < 1e-6, "and his plain melee +50%");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 60)
	public void reciproTurboEndsInAOneMinuteStall(GameTestHelper helper) {
		ServerPlayer p = engine(helper, 4.5, 2.0, 4.5);
		EngineMoves.setGear(p, 3);
		EngineMoves.turbo(p);
		pump(helper, p);
		// run it out: the timer reaches its end
		helper.runAfterDelay(2, () -> Quirks.setVal(p, Engine.V_TURBO, p.level().getGameTime() + 2));
		helper.runAfterDelay(8, () -> {
			helper.assertFalse(Engine.running(p, Engine.V_TURBO), "Turbo is over");
			helper.assertTrue(Quirks.overloaded(p) && Engine.stalled(p), "the engines stall");
			helper.assertTrue(Quirks.overloadLeft(p) > EngineConfig.TURBO_STALL_TICKS - 12 && EngineConfig.TURBO_STALL_TICKS == 1200, "for a minute: "
					+ Quirks.overloadLeft(p));
			helper.assertTrue(p.hasEffect(MobEffects.MOVEMENT_SLOWDOWN) && p.getEffect(MobEffects.MOVEMENT_SLOWDOWN).getAmplifier() == 2
					&& p.getEffect(MobEffects.MOVEMENT_SLOWDOWN).getDuration() > 1100, "a slow walk the whole time");
			helper.assertTrue(EngineMoves.gear(p) == 0 && mod(p, Attributes.MOVEMENT_SPEED, EngineMoves.GEAR_ID) == 0.0, "no gears");
			helper.assertTrue(mod(p, Attributes.ATTACK_DAMAGE, EngineMoves.TURBO_DAMAGE_ID) == 0.0, "the damage boost is gone");
			Quirks.edit(p, n -> n.abilityReadyAt.clear());
			QuirkAbilityManager.handle(p, AbilitySlot.SLOT_5, true);
			QuirkAbilityManager.handle(p, AbilitySlot.SLOT_1, true);
			helper.assertTrue(EngineMoves.gear(p) == 0 && Quirks.ready(p, EngineConfig.KICK), "no gears, no moves while stalled");
			helper.succeed();
		});
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 40)
	public void cAgainEndsTurboEarlyIntoTheStall(GameTestHelper helper) {
		ServerPlayer p = engine(helper, 4.5, 2.0, 4.5);
		EngineMoves.setGear(p, 2);
		QuirkAbilityManager.handle(p, AbilitySlot.SLOT_6, true);
		helper.assertTrue(Engine.running(p, Engine.V_TURBO), "C engages it");
		helper.assertTrue(!Quirks.ready(p, EngineConfig.TURBO), "and its cooldown runs");
		pump(helper, p);
		helper.runAfterDelay(2, () -> QuirkAbilityManager.handle(p, AbilitySlot.SLOT_6, false));
		helper.runAfterDelay(4, () -> QuirkAbilityManager.handle(p, AbilitySlot.SLOT_6, true));
		helper.runAfterDelay(8, () -> {
			helper.assertFalse(Engine.running(p, Engine.V_TURBO), "C again ends it despite the cooldown");
			helper.assertTrue(Quirks.overloadLeft(p) > EngineConfig.TURBO_STALL_TICKS - 12, "into the minute-long stall");
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

	/** v0.17.1 (user): the dash goes exactly where he aims -- turned to -X and looking up, he dashes along -X and upward. */
	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 40)
	public void reciproDashFollowsTheAim(GameTestHelper helper) {
		floor(helper);
		ServerPlayer p = engine(helper, 6.5, 2.0, 4.5);
		p.setYRot(90f); // facing -X
		p.setXRot(-30f); // looking up
		EngineMoves.dash(p);
		Vec3 v = p.getDeltaMovement();
		helper.assertTrue(v.x < -1.0 && Math.abs(v.z) < 0.2, "it follows the yaw (-X): " + v);
		helper.assertTrue(v.y > 0.3, "and the pitch (up): " + v);
		helper.succeed();
	}

	/** v0.17.1 (user): 60% less fall damage while a gear is engaged; full damage in neutral. */
	@GameTest(template = EMPTY_STRUCTURE)
	public void gearsCutFallDamageBySixtyPercent(GameTestHelper helper) {
		ServerPlayer p = engine(helper, 4.5, 2.0, 4.5);
		p.hurt(p.damageSources().fall(), 10f);
		float neutral = 20f - p.getHealth();
		p.setHealth(20f);
		p.invulnerableTime = 0;
		EngineMoves.setGear(p, 1);
		p.hurt(p.damageSources().fall(), 10f);
		float geared = 20f - p.getHealth();
		helper.assertTrue(Math.abs(neutral - 10f) < EPS, "neutral takes it all: " + neutral);
		helper.assertTrue(Math.abs(geared - 4f) < EPS, "Gear 1 takes 40%: " + geared);
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
	public void dashInTurboIsTheTurboStrike(GameTestHelper helper) {
		floor(helper);
		ServerPlayer p = engine(helper, 4.5, 2.0, 0.5);
		Zombie z = mob(helper, EntityType.ZOMBIE, 4.5, 2.0, 4.0, 100);
		Pig pig = mob(helper, EntityType.PIG, 1.5, 2.0, 4.0, 10);
		EngineMoves.turbo(p);
		pump(helper, p);
		EngineMoves.dash(p);
		helper.assertTrue(Engine.running(p, Engine.V_STRIKE), "X in Turbo is the Turbo Strike");
		helper.runAfterDelay(EngineConfig.STRIKE_TICKS + 3, () -> {
			helper.assertTrue(EngineMoves.strikeHit(p).contains(z.getId()), "the zombie in the path was hit");
			helper.assertTrue(Math.abs(taken(z, 100) - EngineConfig.STRIKE_DAMAGE * 1.5f) < EPS, "18 +50% = 27 damage, took " + taken(z, 100));
			helper.assertTrue(pig.getHealth() == 10f, "a pig 3 blocks off the path is left alone");
			helper.assertTrue(!Quirks.overloaded(p) && Engine.running(p, Engine.V_TURBO), "Turbo keeps running (no stall)");
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
