package com.projecthero.mod.gametest;

import java.util.List;

import com.projecthero.mod.hero.Ability;
import com.projecthero.mod.hero.AbilityHandlers;
import com.projecthero.mod.hero.AbilityRouter;
import com.projecthero.mod.hero.AbilitySlot;
import com.projecthero.mod.hero.ExperimentalPowers;
import com.projecthero.mod.hero.Power;
import com.projecthero.mod.hero.Powers;
import com.projecthero.mod.hero.power.p25.GeyserEntity;
import com.projecthero.mod.hero.power.p25.HealingWaterEntity;
import com.projecthero.mod.hero.power.p25.TidalWaveEntity;
import com.projecthero.mod.hero.power.p25.WaterBoltEntity;
import com.projecthero.mod.hero.power.p25.WaterEntities;
import com.projecthero.mod.hero.power.p25.WaterFxEntity;
import com.projecthero.mod.hero.power.p25.WaterHandlers;
import com.projecthero.mod.hero.power.p25.WaterPrisonEntity;
import com.projecthero.mod.hero.power.p25.WaterWhipEntity;
import com.projecthero.mod.hero.visual.MutationMeters;
import com.projecthero.mod.hero.visual.MutationVisuals;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * v0.16.5 Water Manipulation rework (docs/V0165_POWERS_PROPOSAL.md section 12): six keys (+ Shift versions) on a 0-100
 * Water meter -- the meter's refill rules (10 / 25 in rain / 40 in water, paused while a held move pours), every move's
 * cost and numbers, the passives, and the cleanup rule: the water bodies are entities that run out on their own, and no
 * water block is ever left behind. Mock players are not reliably ticked by the server, so the tests drive
 * {@link ExperimentalPowers#serverTick} themselves. Target dummies are no-AI iron golems (100 HP, no armour, so the
 * damage numbers come through exactly).
 */
public class WaterV0165GameTests implements FabricGameTest {
	private static final String K = WaterHandlers.KEY;

	// ---------------- helpers ----------------

	/** A survival mock water bender at (2.5, 2, 1.5) on a stone floor, facing +Z, with a clear 5x4x9 room. */
	private static ServerPlayer hero(GameTestHelper helper) {
		ServerPlayer p = helper.makeMockServerPlayerInLevel();
		p.setGameMode(GameType.SURVIVAL);
		Vec3 at = helper.absoluteVec(new Vec3(2.5, 2.0, 1.5));
		BlockPos base = BlockPos.containing(at);
		for (BlockPos pos : BlockPos.betweenClosed(base.offset(-2, 0, -1), base.offset(2, 4, 7))) {
			helper.getLevel().setBlock(pos, Blocks.AIR.defaultBlockState(), 2);
		}
		for (BlockPos pos : BlockPos.betweenClosed(base.offset(-2, -1, -1), base.offset(2, -1, 7))) {
			helper.getLevel().setBlock(pos, Blocks.STONE.defaultBlockState(), 2);
		}
		p.moveTo(at.x, at.y, at.z, 0.0f, 0.0f);
		p.setYHeadRot(0);
		try {
			java.lang.reflect.Field f = ServerPlayer.class.getDeclaredField("spawnInvulnerableTime");
			f.setAccessible(true);
			f.setInt(p, 0);
		} catch (ReflectiveOperationException e) {
			throw new IllegalStateException(e);
		}
		Power power = Powers.byKey(K);
		ExperimentalPowers.grant(p, power);
		ExperimentalPowers.setActive(p, power);
		p.setOnGround(true);
		tick(p, 1);
		return p;
	}

	private static float res(ServerPlayer p, String name) {
		return ExperimentalPowers.getResource(p, Powers.byKey(K), name);
	}

	private static void tick(ServerPlayer p, int n) {
		for (int i = 0; i < n; i++) {
			ExperimentalPowers.serverTick(p);
		}
	}

	private static void press(ServerPlayer p, int slot) {
		AbilityRouter.handleInput(p, slot, true);
		AbilityRouter.handleInput(p, slot, false);
	}

	private static void shiftPress(ServerPlayer p, int slot) {
		p.setShiftKeyDown(true);
		AbilityRouter.handleInput(p, slot, true);
		AbilityRouter.handleInput(p, slot, false);
		p.setShiftKeyDown(false);
	}

	/** The mock player's "standing in water" flag (normally set by its own entity tick). */
	private static void setInWater(ServerPlayer p, boolean wet) {
		try {
			java.lang.reflect.Field f = net.minecraft.world.entity.Entity.class.getDeclaredField("wasTouchingWater");
			f.setAccessible(true);
			f.setBoolean(p, wet);
		} catch (ReflectiveOperationException e) {
			throw new IllegalStateException(e);
		}
	}

	/** A no-AI iron golem dummy (100 HP, no armour) at relative (x, 2, z). */
	private static IronGolem dummy(GameTestHelper helper, int x, int z) {
		IronGolem g = helper.spawn(EntityType.IRON_GOLEM, new BlockPos(x, 2, z));
		g.setPersistenceRequired();
		g.setNoAi(true);
		return g;
	}

	private static <T extends WaterFxEntity> List<T> bodies(ServerPlayer p, Class<T> type) {
		// only this hero's (neighbouring tests run side by side)
		return p.serverLevel().getEntitiesOfClass(type, p.getBoundingBox().inflate(40), e -> e.ownerId() == p.getId());
	}

	private static void assertNear(GameTestHelper helper, float expected, float got, String what) {
		helper.assertTrue(Math.abs(expected - got) < 0.05f, what + ": expected " + expected + ", got " + got);
	}

	/** How many water blocks there are in the area round the hero (the moves must never leave any). */
	private static int waterBlocks(ServerLevel level, BlockPos around) {
		int n = 0;
		for (BlockPos b : BlockPos.betweenClosed(around.offset(-8, -4, -4), around.offset(8, 12, 24))) {
			if (level.getFluidState(b).is(net.minecraft.tags.FluidTags.WATER)) {
				n++;
			}
		}
		return n;
	}

	// ---------------- registry / HUD ----------------

	@GameTest(template = EMPTY_STRUCTURE)
	public void sixKeysWaterMeterAndEnabled(GameTestHelper helper) {
		Power power = Powers.byKey(K);
		helper.assertTrue(Powers.isEnabled(power) && Powers.isMutation(power), "Water Manipulation is an enabled mutation again");
		helper.assertTrue(power.abilities().size() == 6, "six abilities (no H / N), got " + power.abilities().size());
		String[] ids = { "water_shot", "water_whip", "riptide", "tidal_wave", "water_shield", "aquatic_form" };
		for (int i = 0; i < ids.length; i++) {
			Ability a = power.ability(AbilitySlot.byNumber(i + 1));
			helper.assertTrue(a != null && a.id().equals(ids[i]), "slot " + (i + 1) + " should be " + ids[i]);
			helper.assertTrue(AbilityHandlers.has(power, a), ids[i] + " has a handler");
		}
		var meter = MutationMeters.get(K, WaterHandlers.METER);
		helper.assertTrue(meter != null && meter.always() && meter.style() == MutationMeters.Style.HAIRLINE,
				"Water is an always-on Hairline");
		helper.assertTrue(meter.max() == 100.0f, "the meter runs 0-100");
		for (String timer : new String[] { "surf", "prison", "prison_cd", "heal_cd" }) {
			helper.assertTrue(MutationMeters.get(K, timer) != null, timer + " timer");
		}
		for (String flag : new String[] { "p25.jet", "p25.shield", "p25.surf", "p25.aquatic" }) {
			helper.assertTrue(MutationVisuals.registeredFlags().contains(flag), flag + " is synced");
		}
		helper.assertTrue(WaterEntities.WATER_BOLT != null && WaterEntities.TIDAL_WAVE != null && WaterEntities.GEYSER != null
				&& WaterEntities.WATER_PRISON != null && WaterEntities.WATER_WHIP != null && WaterEntities.HEALING_WATER != null,
				"the water bodies are registered entities");
		helper.succeed();
	}

	// ---------------- the meter ----------------

	@GameTest(template = EMPTY_STRUCTURE)
	public void meterRefillsTenTwentyFiveAndForty(GameTestHelper helper) {
		ServerPlayer p = hero(helper);
		helper.assertTrue(WaterHandlers.water(p) >= 99.9f, "the meter starts full");
		helper.assertTrue(WaterHandlers.regenPerSecond(p) == 10.0f, "10 a second on dry land, got " + WaterHandlers.regenPerSecond(p));
		WaterHandlers.setWater(p, 0);
		tick(p, 20);
		assertNear(helper, 10.0f, WaterHandlers.water(p), "a second on dry land");
		// rain falling on you
		ServerLevel level = helper.getLevel();
		float rain = level.getRainLevel(1.0f);
		level.setRainLevel(1.0f);
		boolean wet = level.isRainingAt(p.blockPosition());
		float inRain = WaterHandlers.regenPerSecond(p);
		level.setRainLevel(rain);
		if (wet) {
			helper.assertTrue(inRain == 25.0f, "25 a second in rain, got " + inRain);
		}
		// standing in water
		setInWater(p, true);
		helper.assertTrue(WaterHandlers.regenPerSecond(p) == 40.0f, "40 a second in water, got " + WaterHandlers.regenPerSecond(p));
		WaterHandlers.setWater(p, 0);
		tick(p, 20);
		assertNear(helper, 40.0f, WaterHandlers.water(p), "a second in water");
		setInWater(p, false);
		tick(p, 400);
		assertNear(helper, 100.0f, WaterHandlers.water(p), "and it caps at 100");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void heldMovesPourOutWaterAndPauseTheRefill(GameTestHelper helper) {
		ServerPlayer p = hero(helper);
		WaterHandlers.setWater(p, 50);
		p.setShiftKeyDown(true);
		AbilityRouter.handleInput(p, 1, true); // hold Shift+R: the Hydro Jet
		p.setShiftKeyDown(false);
		helper.assertTrue(WaterHandlers.jetting(p), "the jet is pouring");
		tick(p, 20);
		assertNear(helper, 50.0f - WaterHandlers.COST_JET_PER_SECOND, WaterHandlers.water(p), "12 a second, no refill while it pours");
		AbilityRouter.handleInput(p, 1, false);
		helper.assertFalse(WaterHandlers.jetting(p), "release stops it");
		WaterHandlers.setWater(p, 50);
		AbilityRouter.handleInput(p, 5, true); // hold V: the Water Shield
		helper.assertTrue(WaterHandlers.shielding(p), "the shield is up");
		tick(p, 20);
		assertNear(helper, 50.0f - WaterHandlers.COST_SHIELD_PER_SECOND, WaterHandlers.water(p), "6 a second, no refill while held");
		AbilityRouter.handleInput(p, 5, false);
		helper.assertFalse(WaterHandlers.shielding(p), "release drops it");
		// a dry meter refuses a move outright
		WaterHandlers.setWater(p, 5);
		press(p, 1);
		assertNear(helper, 5.0f, WaterHandlers.water(p), "Water Shot (8) refused with 5 left");
		helper.assertTrue(bodies(p, WaterBoltEntity.class).isEmpty(), "and nothing was fired");
		helper.succeed();
	}

	// ---------------- R ----------------

	@GameTest(template = EMPTY_STRUCTURE)
	public void waterShotTwelveAndPutsOutFire(GameTestHelper helper) {
		ServerPlayer p = hero(helper);
		IronGolem g = dummy(helper, 2, 6);
		press(p, 1);
		assertNear(helper, 100.0f - WaterHandlers.COST_SHOT, WaterHandlers.water(p), "8 Water");
		List<WaterBoltEntity> bolts = bodies(p, WaterBoltEntity.class);
		helper.assertTrue(bolts.size() == 1, "a water bolt is fired, got " + bolts.size());
		g.setRemainingFireTicks(200);
		bolts.get(0).hitEntity(p, g);
		assertNear(helper, 88.0f, g.getHealth(), "the bolt hits for 12");
		helper.assertFalse(g.isOnFire(), "and puts out the fire");
		bolts.get(0).discard();
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void hydroJetEightASecondWithPushBack(GameTestHelper helper) {
		ServerPlayer p = hero(helper);
		IronGolem g = dummy(helper, 2, 5);
		helper.assertTrue(Math.abs(WaterHandlers.JET_DAMAGE_PER_SECOND * WaterHandlers.JET_PULSE / 20.0f * (20 / WaterHandlers.JET_PULSE) - 8.0f) < 1e-4,
				"two pulses a second make 8");
		int touched = WaterHandlers.jetBeam(p, helper.getLevel(), true);
		helper.assertTrue(touched == 1, "the beam reaches the golem, got " + touched);
		assertNear(helper, 96.0f, g.getHealth(), "one pulse is 4");
		helper.assertTrue(g.getDeltaMovement().z > 0.1, "and it is pushed back along the beam, vz=" + g.getDeltaMovement().z);
		helper.succeed();
	}

	// ---------------- G ----------------

	@GameTest(template = EMPTY_STRUCTURE)
	public void waterWhipTwelveAndPullsIn(GameTestHelper helper) {
		ServerPlayer p = hero(helper);
		IronGolem g = dummy(helper, 2, 7);
		press(p, 2);
		assertNear(helper, 100.0f - WaterHandlers.COST_WHIP, WaterHandlers.water(p), "10 Water");
		assertNear(helper, 88.0f, g.getHealth(), "the lash hits for 12");
		helper.assertTrue(g.getDeltaMovement().z < -0.3, "and hauls it in toward you, vz=" + g.getDeltaMovement().z);
		List<WaterWhipEntity> whips = bodies(p, WaterWhipEntity.class);
		helper.assertTrue(whips.size() == 1 && whips.get(0).targetId() == g.getId(), "the whip model wraps the target");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void waterPrisonTrapsDrownsAndCoolsDown(GameTestHelper helper) {
		ServerPlayer p = hero(helper);
		IronGolem g = dummy(helper, 2, 5);
		shiftPress(p, 2);
		assertNear(helper, 100.0f - WaterHandlers.COST_PRISON, WaterHandlers.water(p), "35 Water");
		helper.assertTrue(res(p, "prison_cd") >= WaterHandlers.PRISON_CD - 0.5f, "a 12 s cooldown");
		List<WaterPrisonEntity> prisons = bodies(p, WaterPrisonEntity.class);
		helper.assertTrue(prisons.size() == 1 && prisons.get(0).targetId() == g.getId(), "the sphere closes round the target");
		prisons.get(0).squeeze(p, g);
		assertNear(helper, 96.0f, g.getHealth(), "a second of drowning is 4");
		helper.assertTrue(4 * 20 == WaterHandlers.PRISON_TICKS, "it holds 4 s");
		// still cooling: a second Shift+G is refused
		shiftPress(p, 2);
		assertNear(helper, 100.0f - WaterHandlers.COST_PRISON, WaterHandlers.water(p), "no second prison while cooling");
		prisons.get(0).discard();
		helper.succeed();
	}

	// ---------------- X ----------------

	@GameTest(template = EMPTY_STRUCTURE)
	public void riptideLaunchesAndSpins(GameTestHelper helper) {
		ServerPlayer p = hero(helper);
		press(p, 3);
		assertNear(helper, 100.0f - WaterHandlers.COST_RIPTIDE, WaterHandlers.water(p), "15 Water");
		helper.assertTrue(p.isAutoSpinAttack(), "a real riptide spin attack");
		helper.assertTrue(p.getDeltaMovement().length() > 2.5, "a Riptide III launch, speed " + p.getDeltaMovement().length());
		helper.assertTrue(WaterHandlers.damageFactor(p, p.damageSources().fall()) < 0, "no fall damage from the landing");
		// v0.16.5 (user): still none after a long flight -- until he is down again
		p.setOnGround(false);
		tick(p, 120);
		helper.assertTrue(WaterHandlers.damageFactor(p, p.damageSources().fall()) < 0, "no fall damage 6 s into the flight");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void waveRideSixSecondsAndKnocksMobsAside(GameTestHelper helper) {
		ServerPlayer p = hero(helper);
		shiftPress(p, 3);
		assertNear(helper, 100.0f - WaterHandlers.COST_SURF, WaterHandlers.water(p), "25 Water");
		helper.assertTrue(WaterHandlers.surfing(p) && res(p, "surf") >= WaterHandlers.SURF_TICKS - 1.5f, "6 s on the wave");
		helper.assertTrue(p.getAttributeValue(Attributes.STEP_HEIGHT) > 1.0, "it rides up steps");
		IronGolem g = dummy(helper, 2, 2);
		g.moveTo(p.getX() + 0.3, p.getY(), p.getZ() + 1.2);
		int n = WaterHandlers.knockAside(p, helper.getLevel());
		helper.assertTrue(n == 1, "the mob in front is knocked aside, got " + n);
		helper.assertTrue(Math.abs(g.getDeltaMovement().x) > 0.5, "sideways, vx=" + g.getDeltaMovement().x);
		shiftPress(p, 3);
		helper.assertFalse(WaterHandlers.surfing(p), "Shift+X again steps off");
		helper.assertTrue(p.getAttributeValue(Attributes.STEP_HEIGHT) < 1.0, "and the step height goes back");
		helper.succeed();
	}

	// ---------------- Z ----------------

	@GameTest(template = EMPTY_STRUCTURE)
	public void tidalWaveFourteenAndSweepsAlong(GameTestHelper helper) {
		ServerPlayer p = hero(helper);
		IronGolem g = dummy(helper, 2, 3);
		press(p, 4);
		assertNear(helper, 100.0f - WaterHandlers.COST_TIDAL, WaterHandlers.water(p), "40 Water");
		List<TidalWaveEntity> waves = bodies(p, TidalWaveEntity.class);
		helper.assertTrue(waves.size() == 1, "a wave entity, not water blocks");
		TidalWaveEntity w = waves.get(0);
		w.moveTo(g.getX(), g.getY(), g.getZ() - 0.5);
		g.setRemainingFireTicks(200);
		w.sweep(helper.getLevel(), p);
		assertNear(helper, 86.0f, g.getHealth(), "it hits for 14");
		helper.assertFalse(g.isOnFire(), "puts out fire");
		helper.assertTrue(g.getDeltaMovement().z >= TidalWaveEntity.SPEED, "and carries it along, vz=" + g.getDeltaMovement().z);
		w.sweep(helper.getLevel(), p);
		assertNear(helper, 86.0f, g.getHealth(), "but only once");
		helper.assertTrue(TidalWaveEntity.SPEED * TidalWaveEntity.RUN == 16.0 && TidalWaveEntity.HALF_WIDTH * 2 == 7.0,
				"7 wide, 16 blocks");
		w.discard();
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void geyserLaunchesTheTargetTenBlocks(GameTestHelper helper) {
		ServerPlayer p = hero(helper);
		IronGolem g = dummy(helper, 2, 5);
		shiftPress(p, 4);
		assertNear(helper, 100.0f - WaterHandlers.COST_GEYSER, WaterHandlers.water(p), "20 Water");
		List<GeyserEntity> gs = bodies(p, GeyserEntity.class);
		helper.assertTrue(gs.size() == 1 && gs.get(0).targetId() == g.getId(), "the geyser is under the target");
		int n = gs.get(0).erupt(p);
		helper.assertTrue(n == 1 && Math.abs(g.getDeltaMovement().y - GeyserEntity.LAUNCH_SPEED) < 1e-6, "it is thrown up");
		// the launch speed carries a mob ~10 blocks under vanilla gravity and drag
		double y = 0, vy = GeyserEntity.LAUNCH_SPEED;
		for (int i = 0; i < 60 && vy > 0; i++) {
			y += vy;
			vy = (vy - 0.08) * 0.98;
		}
		helper.assertTrue(y > 9.5 && y < 10.6, "about 10 blocks, got " + y);
		gs.get(0).discard();
		helper.succeed();
	}

	// ---------------- V ----------------

	@GameTest(template = EMPTY_STRUCTURE)
	public void waterShieldSwallowsProjectiles(GameTestHelper helper) {
		ServerPlayer p = hero(helper);
		AbilityRouter.handleInput(p, 5, true);
		Arrow arrow = new Arrow(EntityType.ARROW, helper.getLevel());
		arrow.moveTo(p.getX(), p.getY() + 1.0, p.getZ() + 2.0);
		arrow.setDeltaMovement(0, 0, -1.5);
		helper.getLevel().addFreshEntity(arrow);
		int n = WaterHandlers.stopProjectiles(p, helper.getLevel());
		helper.assertTrue(n == 1 && arrow.isRemoved(), "the arrow is swallowed by the sphere");
		helper.assertTrue(WaterHandlers.damageFactor(p, p.damageSources().arrow(arrow, null)) < 0, "projectile damage never lands");
		p.setRemainingFireTicks(100);
		tick(p, 1);
		helper.assertFalse(p.isOnFire(), "fire goes out");
		AbilityRouter.handleInput(p, 5, false);
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 140)
	public void healingWaterEightHeartsAndCleanses(GameTestHelper helper) {
		ServerPlayer p = hero(helper);
		p.setHealth(4.0f);
		p.addEffect(new MobEffectInstance(MobEffects.POISON, 400, 1));
		p.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 400, 0));
		shiftPress(p, 5);
		assertNear(helper, 100.0f - WaterHandlers.COST_HEAL, WaterHandlers.water(p), "40 Water");
		helper.assertTrue(res(p, "heal_cd") >= WaterHandlers.HEAL_CD - 0.5f, "a 20 s cooldown");
		helper.assertFalse(p.hasEffect(MobEffects.POISON) || p.hasEffect(MobEffects.WEAKNESS), "harmful effects washed off");
		helper.assertTrue(bodies(p, HealingWaterEntity.class).size() == 1, "the healing swirl wraps you");
		helper.assertTrue(HealingWaterEntity.HEAL_PER_PULSE * (HealingWaterEntity.LIFE / HealingWaterEntity.PULSE) == 16.0f,
				"16 health in all");
		helper.runAfterDelay(HealingWaterEntity.LIFE + 6, () -> {
			helper.assertTrue(p.getHealth() >= 19.9f, "8 hearts over 4 s, health now " + p.getHealth());
			helper.assertTrue(bodies(p, HealingWaterEntity.class).isEmpty(), "and the swirl is gone");
			helper.succeed();
		});
	}

	// ---------------- C ----------------

	@GameTest(template = EMPTY_STRUCTURE)
	public void aquaticFormBuffsInWater(GameTestHelper helper) {
		ServerPlayer p = hero(helper);
		double base = p.getAttributeValue(Attributes.ATTACK_DAMAGE);
		press(p, 6);
		helper.assertTrue(WaterHandlers.aquaticActive(p), "Aquatic Form is on");
		assertNear(helper, 100.0f, WaterHandlers.water(p), "and free");
		tick(p, 1);
		helper.assertTrue(p.hasEffect(MobEffects.DOLPHINS_GRACE), "Dolphin's Grace");
		helper.assertTrue(Math.abs(p.getAttributeValue(Attributes.ATTACK_DAMAGE) - base) < 1e-3, "no melee bonus on land");
		setInWater(p, true);
		tick(p, 1);
		helper.assertTrue(Math.abs(p.getAttributeValue(Attributes.ATTACK_DAMAGE) - base - 6.0) < 1e-3, "+6 melee in water");
		helper.assertTrue(p.hasEffect(MobEffects.NIGHT_VISION), "night vision underwater");
		setInWater(p, false);
		press(p, 6);
		tick(p, 1);
		helper.assertFalse(WaterHandlers.aquaticActive(p), "toggled off");
		helper.assertTrue(Math.abs(p.getAttributeValue(Attributes.ATTACK_DAMAGE) - base) < 1e-3, "the bonus goes");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void drawWaterPullsAndPlacesSources(GameTestHelper helper) {
		ServerPlayer p = hero(helper);
		ServerLevel level = helper.getLevel();
		BlockPos eye = BlockPos.containing(p.getEyePosition());
		BlockPos source = eye.offset(0, 0, 3);
		level.setBlock(source, Blocks.WATER.defaultBlockState(), 2);
		WaterHandlers.setWater(p, 50);
		shiftPress(p, 6);
		helper.assertTrue(level.getBlockState(source).isAir(), "the source is pulled out of the world");
		assertNear(helper, 80.0f, WaterHandlers.water(p), "+30 Water");
		// nothing to draw: a source is placed against the wall you aim at
		BlockPos wall = eye.offset(0, 0, 5);
		level.setBlock(wall, Blocks.STONE.defaultBlockState(), 2);
		shiftPress(p, 6);
		BlockPos placed = wall.offset(0, 0, -1);
		helper.assertTrue(level.getBlockState(placed).is(Blocks.WATER), "a water source is placed in front of the wall");
		assertNear(helper, 75.0f, WaterHandlers.water(p), "for 5 Water");
		level.setBlock(placed, Blocks.AIR.defaultBlockState(), 2);
		helper.succeed();
	}

	// ---------------- passives ----------------

	@GameTest(template = EMPTY_STRUCTURE)
	public void passivesBreathingSwimmingAndFire(GameTestHelper helper) {
		ServerPlayer p = hero(helper);
		helper.assertTrue(p.hasEffect(MobEffects.WATER_BREATHING), "water breathing");
		helper.assertTrue(p.getAttributeValue(Attributes.WATER_MOVEMENT_EFFICIENCY) >= 1.0, "fast swimming");
		assertNear(helper, 0.7f, WaterHandlers.damageFactor(p, p.damageSources().inFire()), "-30% fire");
		assertNear(helper, 0.7f, WaterHandlers.damageFactor(p, p.damageSources().lava()), "-30% lava");
		assertNear(helper, 1.0f, WaterHandlers.damageFactor(p, p.damageSources().generic()), "nothing else changes");
		p.setRemainingFireTicks(200);
		tick(p, 1);
		helper.assertFalse(p.isOnFire(), "fire on you goes out at once");
		helper.assertTrue(helper.getLevel().damageSources().source(DamageTypes.IN_FIRE).is(net.minecraft.tags.DamageTypeTags.IS_FIRE),
				"(in-fire is in the fire tag)");
		helper.succeed();
	}

	// ---------------- cleanup ----------------

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 200)
	public void noWaterBlocksAreEverLeftBehind(GameTestHelper helper) {
		ServerPlayer p = hero(helper);
		ServerLevel level = helper.getLevel();
		BlockPos at = p.blockPosition();
		int before = waterBlocks(level, at);
		IronGolem g = dummy(helper, 2, 5);
		press(p, 1); // Water Shot
		press(p, 2); // Water Whip
		shiftPress(p, 2); // Water Prison
		press(p, 4); // Tidal Wave
		shiftPress(p, 4); // Geyser
		shiftPress(p, 5); // Healing Water
		helper.assertTrue(waterBlocks(level, at) == before, "no water block placed by any move");
		AABB area = new AABB(at).inflate(48);
		helper.runAfterDelay(120, () -> {
			helper.assertTrue(waterBlocks(level, at) == before, "and none appear later, got " + (waterBlocks(level, at) - before));
			int left = level.getEntitiesOfClass(WaterFxEntity.class, area, e -> e.ownerId() == p.getId()).size();
			helper.assertTrue(left == 0, "every water body has run out on its own, " + left + " left");
			helper.assertTrue(g.isAlive(), "(the dummy survived)");
			helper.succeed();
		});
	}
}
