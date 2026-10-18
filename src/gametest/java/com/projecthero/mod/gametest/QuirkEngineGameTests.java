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
 * v0.16.7 Engine (Tenya Iida / Ingenium): one test per move -- the server effect and its number -- plus the Stall and
 * the passive modifiers coming off on revoke. Mock players are not ticked by the server, so tests that run over time
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

	@GameTest(template = EMPTY_STRUCTURE)
	public void engineKitRegistered(GameTestHelper helper) {
		helper.assertTrue(QuirkKits.of(Quirk.ENGINE).idsOf(AbilitySlot.SLOT_1).length == 2, "the Engine kit has its moves");
		helper.assertTrue(QuirkKits.of(Quirk.ENGINE).idsOf(AbilitySlot.SLOT_6)[1].equals(EngineConfig.COOL), "Shift+C is Cool Down");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 40)
	public void passiveSpeedAndStepComeOffOnRevoke(GameTestHelper helper) {
		ServerPlayer p = engine(helper, 4.5, 2.0, 4.5);
		Quirks.tick(p);
		helper.assertTrue(Math.abs(mod(p, Attributes.MOVEMENT_SPEED, EngineMoves.PASSIVE_ID) - EngineConfig.PASSIVE_SPEED) < 1e-6, "Speed I passive");
		helper.assertTrue(Math.abs(mod(p, Attributes.STEP_HEIGHT, EngineMoves.STEP_ID) - EngineConfig.STEP_BONUS) < 1e-6, "one-block step");
		EngineMoves.burst(p);
		QuirkKits.of(Quirk.ENGINE).tick(p);
		helper.assertTrue(mod(p, Attributes.MOVEMENT_SPEED, EngineMoves.GEAR_ID) > 0.0, "the Burst gear is on");
		Quirks.revoke(p);
		helper.assertTrue(mod(p, Attributes.MOVEMENT_SPEED, EngineMoves.PASSIVE_ID) == 0.0, "passive speed removed");
		helper.assertTrue(mod(p, Attributes.MOVEMENT_SPEED, EngineMoves.GEAR_ID) == 0.0, "gear speed removed");
		helper.assertTrue(mod(p, Attributes.STEP_HEIGHT, EngineMoves.STEP_ID) == 0.0, "step assist removed");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 40)
	public void fullHeatStallsTheEngines(GameTestHelper helper) {
		ServerPlayer p = engine(helper, 4.5, 2.0, 4.5);
		Quirks.tick(p);
		EngineMoves.gearUp(p);
		Quirks.setMeter(p, 98f);
		EngineMoves.heat(p, 5f);
		helper.assertTrue(Quirks.overloaded(p), "100 heat stalls");
		helper.assertTrue(p.hasEffect(MobEffects.MOVEMENT_SLOWDOWN) && p.getEffect(MobEffects.MOVEMENT_SLOWDOWN).getAmplifier() == 2, "Slowness III");
		helper.assertTrue(!Engine.running(p, Engine.V_GEAR), "the gear dropped out");
		p.tickCount++;
		helper.runAfterDelay(1, () -> {
			Quirks.tick(p);
			helper.assertTrue(mod(p, Attributes.MOVEMENT_SPEED, EngineMoves.PASSIVE_ID) == 0.0, "no Speed I while stalled");
			helper.assertTrue(mod(p, Attributes.MOVEMENT_SPEED, EngineMoves.GEAR_ID) == 0.0, "no gear while stalled");
			QuirkAbilityManager.handle(p, AbilitySlot.SLOT_1, true);
			helper.assertTrue(!Engine.running(p, Engine.V_KICK), "no moves while stalled");
			helper.succeed();
		});
	}

	// ---------------------------------------------------------------- R

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 60)
	public void reciproKickLandsThreeKicks(GameTestHelper helper) {
		floor(helper);
		ServerPlayer p = engine(helper, 4.5, 2.0, 2.5);
		Zombie z = mob(helper, EntityType.ZOMBIE, 4.5, 2.0, 4.5, 100);
		pump(helper, p);
		EngineMoves.kick(p);
		helper.runAfterDelay(20, () -> {
			float t = taken(z, 100);
			float want = EngineConfig.KICK_HITS * EngineConfig.KICK_DAMAGE;
			helper.assertTrue(Math.abs(t - want) < EPS, "three kicks of 5, took " + t);
			helper.succeed();
		});
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 40)
	public void spinKickHitsAllAround(GameTestHelper helper) {
		floor(helper);
		ServerPlayer p = engine(helper, 4.5, 2.0, 4.5);
		Zombie front = mob(helper, EntityType.ZOMBIE, 4.5, 2.0, 6.5, 100);
		Zombie back = mob(helper, EntityType.ZOMBIE, 4.5, 2.0, 2.5, 100);
		int n = EngineMoves.spin(p);
		helper.assertTrue(n == 2, "both hit, got " + n);
		helper.assertTrue(Math.abs(taken(front, 100) - EngineConfig.SPIN_DAMAGE) < EPS && Math.abs(taken(back, 100) - EngineConfig.SPIN_DAMAGE) < EPS,
				"7 each");
		helper.succeed();
	}

	// ---------------------------------------------------------------- G

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 60)
	public void reciproBurstSpeedsUpThenStalls(GameTestHelper helper) {
		ServerPlayer p = engine(helper, 4.5, 2.0, 4.5);
		pump(helper, p);
		helper.assertTrue(EngineMoves.burst(p), "Burst starts");
		helper.runAfterDelay(3, () -> {
			helper.assertTrue(Math.abs(mod(p, Attributes.MOVEMENT_SPEED, EngineMoves.GEAR_ID) - EngineConfig.BURST_SPEED) < 1e-6, "+100% speed");
			helper.assertTrue(!Quirks.overloaded(p), "not stalled while it runs");
			// G again ends it -> the stall
			QuirkAbilityManager.handle(p, AbilitySlot.SLOT_2, true);
			helper.runAfterDelay(3, () -> {
				helper.assertTrue(Quirks.overloaded(p), "Burst ends in a stall");
				helper.assertTrue(Quirks.overloadLeft(p) <= EngineConfig.BURST_STALL_TICKS, "the short stall");
				helper.succeed();
			});
		});
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 60)
	public void gearUpIsFasterAtDoubleHeat(GameTestHelper helper) {
		ServerPlayer p = engine(helper, 4.5, 2.0, 4.5);
		pump(helper, p);
		EngineMoves.gearUp(p);
		float start = Quirks.meter(p);
		helper.runAfterDelay(20, () -> {
			helper.assertTrue(Math.abs(mod(p, Attributes.MOVEMENT_SPEED, EngineMoves.GEAR_ID) - EngineConfig.GEAR_SPEED) < 1e-6, "Gear Up speed");
			float rose = Quirks.meter(p) - start;
			helper.assertTrue(rose > EngineConfig.GEAR_HEAT_PER_SECOND * 0.8f && rose < EngineConfig.GEAR_HEAT_PER_SECOND * 1.3f, "6 heat a second, rose " + rose);
			helper.succeed();
		});
	}

	// ---------------------------------------------------------------- X

	@GameTest(template = EMPTY_STRUCTURE)
	public void engineLeapLaunchesHighAndCushions(GameTestHelper helper) {
		ServerPlayer p = engine(helper, 4.5, 2.0, 4.5);
		EngineMoves.leap(p);
		helper.assertTrue(Math.abs(p.getDeltaMovement().y - EngineConfig.LEAP_UP) < 1e-6, "launched up " + p.getDeltaMovement().y);
		helper.assertTrue(EngineMoves.softLanding(p), "the landing is cushioned");
		float after = QuirkKits.of(Quirk.ENGINE).modifyIncomingDamage(p, p.damageSources().fall(), 10f);
		helper.assertTrue(after == 0f, "no fall damage");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void airDashBoostsOncePerJump(GameTestHelper helper) {
		ServerPlayer p = engine(helper, 4.5, 4.0, 4.5);
		p.setOnGround(false);
		helper.assertTrue(EngineMoves.airDash(p), "the dash");
		Vec3 v = p.getDeltaMovement();
		helper.assertTrue(v.z > EngineConfig.AIR_DASH_SPEED * 0.9, "boosted the way he looks, " + v);
		Quirks.edit(p, n -> n.abilityReadyAt.clear());
		helper.assertTrue(!EngineMoves.airDash(p), "once per jump");
		helper.succeed();
	}

	// ---------------------------------------------------------------- Z

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 40)
	public void reciproExtendHitsEverythingInThePath(GameTestHelper helper) {
		floor(helper);
		ServerPlayer p = engine(helper, 4.5, 2.0, 0.5);
		Zombie a = mob(helper, EntityType.ZOMBIE, 4.5, 2.0, 3.5, 100);
		Zombie b = mob(helper, EntityType.ZOMBIE, 4.5, 2.0, 6.5, 100);
		pump(helper, p);
		EngineMoves.extend(p);
		helper.runAfterDelay(EngineConfig.EXTEND_TICKS + 3, () -> {
			helper.assertTrue(EngineMoves.extended(p).contains(a.getId()) && EngineMoves.extended(p).contains(b.getId()), "both in the path hit");
			helper.assertTrue(Math.abs(taken(a, 100) - EngineConfig.EXTEND_DAMAGE) < EPS, "10 damage, took " + taken(a, 100));
			helper.succeed();
		});
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void afterburnerKickLaunchesFar(GameTestHelper helper) {
		floor(helper);
		ServerPlayer p = engine(helper, 4.5, 2.0, 2.5);
		Zombie z = mob(helper, EntityType.ZOMBIE, 4.5, 2.0, 4.5, 100);
		LivingEntity t = EngineMoves.afterburner(p);
		helper.assertTrue(t == z, "the zombie in front is kicked");
		helper.assertTrue(Math.abs(taken(z, 100) - EngineConfig.AFTERBURNER_DAMAGE) < EPS, "12 damage");
		helper.assertTrue(z.getDeltaMovement().z > EngineConfig.AFTERBURNER_LAUNCH * 0.9, "launched far, " + z.getDeltaMovement());
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void afterburnerKickDoesNotLaunchBosses(GameTestHelper helper) {
		floor(helper);
		ServerPlayer p = engine(helper, 4.5, 2.0, 2.5);
		Zombie z = mob(helper, EntityType.ZOMBIE, 4.5, 2.0, 4.5, 1000);
		EngineMoves.afterburner(p);
		helper.assertTrue(taken(z, 1000) <= 1000 * EngineConfig.BOSS_MAX_FRACTION_PER_HIT + EPS, "boss-capped");
		helper.assertTrue(z.getDeltaMovement().horizontalDistance() < 0.5, "a boss is not launched");
		helper.succeed();
	}

	// ---------------------------------------------------------------- V

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 40)
	public void rescueCarryPicksUpAndSetsDown(GameTestHelper helper) {
		floor(helper);
		ServerPlayer p = engine(helper, 4.5, 2.0, 2.5);
		Pig pig = mob(helper, EntityType.PIG, 4.5, 2.0, 4.0, 10);
		p.setXRot(45f);
		LivingEntity got = EngineMoves.carry(p);
		helper.assertTrue(got == pig && pig.getVehicle() == p && Engine.carrying(p), "the pig is carried");
		Quirks.edit(p, n -> n.abilityReadyAt.clear());
		helper.assertTrue(EngineMoves.carry(p) == null && pig.getVehicle() == null && !Engine.carrying(p), "V again sets it down");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void rescueCarryRefusesBosses(GameTestHelper helper) {
		floor(helper);
		ServerPlayer p = engine(helper, 4.5, 2.0, 2.5);
		Zombie z = mob(helper, EntityType.ZOMBIE, 4.5, 2.0, 4.0, 1000);
		p.setXRot(30f);
		helper.assertTrue(EngineMoves.carry(p) == null && z.getVehicle() == null, "a boss can't be carried");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void rescueThrowHurlsTheLoad(GameTestHelper helper) {
		floor(helper);
		ServerPlayer p = engine(helper, 4.5, 2.0, 2.5);
		Pig pig = mob(helper, EntityType.PIG, 4.5, 2.0, 4.0, 10);
		helper.assertTrue(EngineMoves.throwCarried(p) == null, "nothing to throw yet");
		p.setXRot(45f);
		EngineMoves.carry(p);
		p.setXRot(0f);
		LivingEntity thrown = EngineMoves.throwCarried(p);
		helper.assertTrue(thrown == pig && pig.getVehicle() == null && !Engine.carrying(p), "thrown off");
		helper.assertTrue(pig.getDeltaMovement().z > EngineConfig.THROW_SPEED * 0.9, "the way he looks, " + pig.getDeltaMovement());
		helper.assertTrue(pig.hasEffect(MobEffects.SLOW_FALLING), "a non-hostile floats down");
		helper.succeed();
	}

	// ---------------------------------------------------------------- C

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 40)
	public void reciproTurboHitsWhatHePasses(GameTestHelper helper) {
		floor(helper);
		ServerPlayer p = engine(helper, 4.5, 2.0, 0.5);
		Zombie z = mob(helper, EntityType.ZOMBIE, 4.5, 2.0, 4.0, 100);
		Pig pig = mob(helper, EntityType.PIG, 2.5, 2.0, 4.0, 10);
		EngineMoves.turbo(p);
		Vec3 to = helper.absoluteVec(new Vec3(4.5, 2.0, 7.0));
		p.teleportTo(to.x, to.y, to.z);
		p.tickCount++;
		helper.runAfterDelay(1, () -> {
			Quirks.tick(p);
			helper.assertTrue(Math.abs(mod(p, Attributes.MOVEMENT_SPEED, EngineMoves.GEAR_ID) - EngineConfig.TURBO_SPEED) < 1e-6, "Turbo speed");
			helper.assertTrue(EngineMoves.turboHit(p).contains(z.getId()), "the zombie he passed was hit");
			helper.assertTrue(Math.abs(taken(z, 100) - EngineConfig.TURBO_DAMAGE) < EPS, "8 damage");
			helper.assertTrue(pig.getHealth() == 10f, "a pig 2 blocks off the path is left alone");
			helper.succeed();
		});
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 40)
	public void coolDownVentsFiftyAndEndsBurstWithoutStall(GameTestHelper helper) {
		ServerPlayer p = engine(helper, 4.5, 2.0, 4.5);
		pump(helper, p);
		EngineMoves.burst(p);
		Quirks.setMeter(p, 80f);
		EngineMoves.coolDown(p);
		helper.assertTrue(Math.abs(Quirks.meter(p) - 30f) < 0.5f, "vented 50, now " + Quirks.meter(p));
		helper.assertTrue(!Engine.running(p, Engine.V_BURST), "the Burst ended");
		helper.assertTrue(p.getDeltaMovement().horizontalDistance() < 1e-6, "stopped dead");
		helper.runAfterDelay(5, () -> {
			helper.assertTrue(!Quirks.overloaded(p), "no stall after venting");
			helper.succeed();
		});
	}
}
