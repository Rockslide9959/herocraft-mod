package com.projecthero.mod.gametest;

import java.util.ArrayList;
import java.util.List;

import com.projecthero.mod.hero.AbilitySlot;
import com.projecthero.mod.hero.UniquePowers;
import com.projecthero.mod.hero.power.AbilityHelpers;
import com.projecthero.mod.hero.power.p05.GeoTerrain;
import com.projecthero.mod.quirk.Quirk;
import com.projecthero.mod.quirk.QuirkAbilityManager;
import com.projecthero.mod.quirk.QuirkKit;
import com.projecthero.mod.quirk.QuirkKits;
import com.projecthero.mod.quirk.Quirks;
import com.projecthero.mod.quirk.data.QuirkState;
import com.projecthero.mod.quirk.zerogravity.ZeroGravity;
import com.projecthero.mod.quirk.zerogravity.ZeroGravityConfig;
import com.projecthero.mod.quirk.zerogravity.ZeroGravityDebris;
import com.projecthero.mod.quirk.zerogravity.ZeroGravityFloat;
import com.projecthero.mod.quirk.zerogravity.ZeroGravityMoves;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/**
 * v0.16.7 Zero Gravity revamp (Uravity, {@code docs/QUIRK_REVAMP_V0167.md}): touch to float (mobs, items, blocks), Release to
 * drop (fall damage from the height, blocks smash and their hole is filled back exactly), Float Self, the Meteor Shower cloud and
 * its cleanup, Nausea and Settle -- plus one test per attack and the rules that matter most: gravity always comes back
 * (Release, Settle, stopAll, revoke, sickness, the load-time safety net), lifted ground is always put back, bosses never float.
 * Mock players are not ticked by the server, so tests that run over time drive the Quirk tick themselves ({@link #pump}).
 */
public class QuirkZeroGravityGameTests implements FabricGameTest {
	private static final float EPS = 0.6f;

	// ---------------------------------------------------------------- helpers

	@SuppressWarnings("removal")
	private static ServerPlayer mock(GameTestHelper helper, double x, double y, double z) {
		ServerPlayer p = helper.makeMockServerPlayerInLevel();
		p.setGameMode(GameType.SURVIVAL);
		Vec3 at = helper.absoluteVec(new Vec3(x, y, z));
		p.moveTo(at.x, at.y, at.z, 0.0f, 0.0f);
		p.setYHeadRot(0f);
		try {
			java.lang.reflect.Field f = ServerPlayer.class.getDeclaredField("spawnInvulnerableTime");
			f.setAccessible(true);
			f.setInt(p, 0);
		} catch (ReflectiveOperationException e) {
			throw new IllegalStateException(e);
		}
		return p;
	}

	/** Uravity facing +Z, every move ready. */
	private static ServerPlayer uravity(GameTestHelper helper, double x, double y, double z) {
		ServerPlayer p = mock(helper, x, y, z);
		helper.assertTrue(UniquePowers.forced(() -> Quirks.grant(p, Quirk.ZERO_GRAVITY)), "Zero Gravity is granted");
		Quirks.setMeter(p, 0f);
		ready(p);
		return p;
	}

	private static void ready(ServerPlayer p) {
		Quirks.edit(p, n -> {
			n.animId = QuirkState.ANIM_NONE;
			n.abilityReadyAt.clear();
		});
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

	private static Zombie zombie(GameTestHelper helper, double x, double y, double z, double health) {
		helper.getLevel().getServer().setDifficulty(Difficulty.NORMAL, true);
		Zombie m = EntityType.ZOMBIE.create(helper.getLevel());
		Vec3 at = helper.absoluteVec(new Vec3(x, y, z));
		m.moveTo(at.x, at.y, at.z, 180.0f, 0.0f);
		m.setNoAi(true);
		m.setPersistenceRequired();
		m.getAttribute(Attributes.MAX_HEALTH).setBaseValue(health);
		m.setHealth((float) health);
		m.getAttribute(Attributes.ARMOR).setBaseValue(0.0);
		m.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.CARVED_PUMPKIN));
		helper.getLevel().addFreshEntity(m);
		return m;
	}

	private static boolean floated(Mob m) {
		return m.isNoGravity() && m.getTags().contains(ZeroGravity.TAG_FLOAT);
	}

	private static boolean grounded(Mob m) {
		return !m.isNoGravity() && !m.getTags().contains(ZeroGravity.TAG_FLOAT);
	}

	private static float taken(Mob m, double health) {
		return (float) health - m.getHealth();
	}

	// ---------------------------------------------------------------- the kit

	@GameTest(template = EMPTY_STRUCTURE)
	public void zeroGravityKitFollowsTheSlotRules(GameTestHelper helper) {
		QuirkKit kit = QuirkKits.of(Quirk.ZERO_GRAVITY);
		for (AbilitySlot s : new AbilitySlot[] { AbilitySlot.SLOT_1, AbilitySlot.SLOT_2, AbilitySlot.SLOT_3, AbilitySlot.SLOT_4, AbilitySlot.SLOT_5,
				AbilitySlot.SLOT_6 }) {
			helper.assertTrue(kit.idsOf(s).length == 2, "a tap and a Shift move on " + s);
			for (String id : kit.idsOf(s)) {
				helper.assertTrue(id.startsWith("quirk_zero_gravity_") && kit.maxCooldown(id) > 0, "id and cooldown of " + id);
			}
		}
		helper.assertTrue(kit.idsOf(AbilitySlot.SLOT_3)[0].equals(ZeroGravityConfig.SELF), "X = Float Self (movement)");
		helper.assertTrue(kit.idsOf(AbilitySlot.SLOT_5)[0].equals(ZeroGravityConfig.TOUCH) && kit.idsOf(AbilitySlot.SLOT_5)[1].equals(ZeroGravityConfig.RELEASE),
				"V = Touch / Release (utility)");
		helper.assertTrue(kit.usableWhileOverloaded(AbilitySlot.SLOT_6, true), "Settle works while sick");
		helper.assertFalse(kit.usableWhileOverloaded(AbilitySlot.SLOT_5, false), "Touch does not");
		helper.succeed();
	}

	// ---------------------------------------------------------------- V: Touch / Shift+V: Release

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 140)
	public void touchFloatsAMobAndReleaseDropsItWithFallDamage(GameTestHelper helper) {
		floor(helper);
		ServerPlayer p = uravity(helper, 4.5, 2.0, 1.5);
		Zombie z = zombie(helper, 4.5, 2.0, 3.5, 100);
		z.setNoAi(false);
		helper.assertTrue(ZeroGravityMoves.touch(p) == z, "the touch floats the zombie");
		helper.assertTrue(floated(z) && ZeroGravityFloat.floatedBy(p, z), "weightless");
		helper.assertTrue(taken(z, 100) >= ZeroGravityConfig.TOUCH_DAMAGE - EPS, "the five-finger hit");
		ZeroGravityFloat.liftTo(p, z, 7.0, 0.5);
		pump(helper, p);
		helper.runAfterDelay(30, () -> {
			helper.assertTrue(z.getY() > helper.absoluteVec(new Vec3(0, 6.0, 0)).y, "it rose, y " + z.getY());
			float before = taken(z, 100);
			z.invulnerableTime = 0;
			helper.assertTrue(ZeroGravityMoves.release(p) == 1, "Release drops it");
			helper.assertTrue(grounded(z), "gravity back");
			helper.runAfterDelay(40, () -> {
				helper.assertTrue(taken(z, 100) - before >= 2f, "fall damage from the height, took " + (taken(z, 100) - before));
				helper.succeed();
			});
		});
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void touchFloatsADroppedItem(GameTestHelper helper) {
		floor(helper);
		ServerPlayer p = uravity(helper, 4.5, 2.0, 1.5);
		ItemEntity item = new ItemEntity(helper.getLevel(), 0, 0, 0, new ItemStack(Items.APPLE));
		Vec3 at = helper.absoluteVec(new Vec3(4.5, 3.3, 3.5));
		item.moveTo(at.x, at.y, at.z);
		item.setDeltaMovement(Vec3.ZERO);
		helper.getLevel().addFreshEntity(item);
		helper.assertTrue(ZeroGravityMoves.touch(p) == item, "the touch floats the apple");
		helper.assertTrue(item.isNoGravity() && ZeroGravityFloat.isFloating(item), "it is weightless");
		ZeroGravityMoves.release(p);
		helper.assertTrue(!item.isNoGravity() && !item.getTags().contains(ZeroGravity.TAG_FLOAT), "Release gives it its gravity back");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 100)
	public void touchLiftsABlockAndItsHoleIsFilledWhenItLands(GameTestHelper helper) {
		floor(helper);
		ServerPlayer p = uravity(helper, 4.5, 2.0, 1.5);
		BlockPos rel = new BlockPos(4, 3, 3);
		BlockPos abs = helper.absolutePos(rel);
		helper.getLevel().setBlock(abs, Blocks.OAK_PLANKS.defaultBlockState(), 3);
		Object got = ZeroGravityMoves.touch(p);
		if (!AbilityHelpers.canGrief()) {
			helper.assertTrue(got == null, "terrain damage off: a block is never taken");
			helper.assertBlockPresent(Blocks.OAK_PLANKS, rel);
			helper.succeed();
			return;
		}
		helper.assertTrue(abs.equals(got), "the touch lifted the block, got " + got);
		helper.assertTrue(helper.getLevel().getBlockState(abs).isAir() && GeoTerrain.isTemp(helper.getLevel(), abs), "its hole is on the ledger");
		helper.assertTrue(ZeroGravityDebris.count(p, ZeroGravityDebris.HELD) == 1 && ZeroGravityDebris.displays(p).size() == 1, "it floats as a falling-block display");
		pump(helper, p);
		helper.runAfterDelay(10, () -> {
			helper.assertTrue(ZeroGravityMoves.release(p) == 1, "Release drops it");
			helper.runAfterDelay(40, () -> {
				helper.assertTrue(ZeroGravityDebris.displays(p).isEmpty(), "it smashed and is gone");
				helper.assertBlockPresent(Blocks.OAK_PLANKS, rel);
				helper.assertTrue(!GeoTerrain.isTemp(helper.getLevel(), abs), "and the hole is filled back exactly");
				helper.succeed();
			});
		});
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void touchCapsAtFiveCreatures(GameTestHelper helper) {
		floor(helper);
		ServerPlayer p = uravity(helper, 4.5, 2.0, 1.5);
		for (int i = 0; i < ZeroGravityConfig.TOUCH_MAX; i++) {
			helper.assertTrue(ZeroGravityFloat.start(p, zombie(helper, 0.5 + i, 2.0, 6.5, 100), false), "float " + i);
		}
		Zombie extra = zombie(helper, 4.5, 2.0, 3.0, 100);
		ZeroGravityMoves.touch(p);
		helper.assertTrue(grounded(extra), "the sixth is refused");
		ZeroGravityMoves.endAll(p);
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void bossesNeverFloat(GameTestHelper helper) {
		floor(helper);
		ServerPlayer p = uravity(helper, 4.5, 2.0, 1.5);
		Zombie boss = zombie(helper, 4.5, 2.0, 3.5, 500);
		helper.assertTrue(ZeroGravityMoves.touch(p) == null, "refused");
		helper.assertTrue(grounded(boss) && !ZeroGravityFloat.start(p, boss, false), "a boss never floats");
		helper.assertTrue(500f - boss.getHealth() <= 500f * ZeroGravityConfig.BOSS_MAX_FRACTION_PER_HIT + EPS, "capped hit");
		helper.succeed();
	}

	// ---------------------------------------------------------------- X: Float Self / Shift+X: Leap

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 60)
	public void floatSelfTogglesHerGravityAndFillsNausea(GameTestHelper helper) {
		ServerPlayer p = uravity(helper, 4.5, 2.0, 1.5);
		helper.assertTrue(ZeroGravityMoves.floatSelf(p), "on");
		helper.assertTrue(p.isNoGravity() && ZeroGravity.selfFloating(p) && p.getAbilities().mayfly, "she floats");
		pump(helper, p);
		helper.runAfterDelay(40, () -> {
			float expected = ZeroGravityConfig.nausea().perWeightPerSecond * ZeroGravityConfig.nausea().selfWeight * 2f;
			helper.assertTrue(Quirks.meter(p) >= expected * 0.9f, "her own body fills Nausea, got " + Quirks.meter(p));
			ready(p);
			helper.assertFalse(ZeroGravityMoves.floatSelf(p), "off");
			helper.assertTrue(!p.isNoGravity() && !p.getTags().contains(ZeroGravity.TAG_FLOAT) && !p.getAbilities().mayfly, "her gravity is back");
			helper.succeed();
		});
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void leapCutsGravityUntilShesDone(GameTestHelper helper) {
		ServerPlayer p = uravity(helper, 4.5, 2.0, 1.5);
		ZeroGravityMoves.leap(p);
		double g = p.getAttributeValue(Attributes.GRAVITY);
		helper.assertTrue(g < 0.08 * (1.0 - ZeroGravityConfig.LEAP_GRAVITY_CUT) + 0.001, "almost weightless: " + g);
		helper.assertTrue(ZeroGravity.leaping(p), "the leap shows");
		ZeroGravityMoves.endAll(p);
		helper.assertTrue(Math.abs(p.getAttributeValue(Attributes.GRAVITY) - 0.08) < 1.0e-6, "gravity back after stopAll");
		helper.succeed();
	}

	// ---------------------------------------------------------------- R: Meteor Shower / Shift+R: Shield

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 200)
	public void meteorShowerCloudRainsOnTheTargetAndTheGroundComesBack(GameTestHelper helper) {
		floor(helper);
		ServerPlayer p = uravity(helper, 4.5, 2.0, 1.5);
		Zombie z = zombie(helper, 4.5, 2.0, 6.0, 100);
		List<BlockPos> taken = new ArrayList<>();
		int n = ZeroGravityMoves.meteorShower(p, taken);
		helper.assertTrue(n > 0 && ZeroGravityDebris.cloudUp(p), "a rubble cloud, " + n + " pieces");
		helper.assertTrue(ZeroGravityDebris.displays(p).size() == n, "the rubble is block displays");
		if (AbilityHelpers.canGrief()) {
			helper.assertTrue(!taken.isEmpty(), "real ground was lifted");
			for (BlockPos bp : taken) {
				helper.assertTrue(helper.getLevel().getBlockState(bp).isAir() && GeoTerrain.isTemp(helper.getLevel(), bp), "the hole is temporary");
			}
		}
		pump(helper, p);
		helper.runAfterDelay(45, () -> {
			double top = helper.absoluteVec(new Vec3(0, 7.0, 0)).y;
			long high = ZeroGravityDebris.displays(p).stream().filter(d -> d.getY() > top).count();
			helper.assertTrue(high >= n / 2, "the cloud hangs high overhead, " + high + " of " + n);
			helper.assertTrue(ZeroGravityMoves.release(p) == n, "Release rains it all");
			helper.assertFalse(ZeroGravityDebris.cloudUp(p), "no cloud now");
		});
		helper.runAfterDelay(120, () -> {
			helper.assertTrue(ZeroGravityDebris.displays(p).isEmpty(), "every piece smashed and is gone");
			helper.assertTrue(taken(z, 100) > 0f, "the rain hit the target");
			for (BlockPos bp : taken) {
				helper.assertTrue(!helper.getLevel().getBlockState(bp).isAir() && !GeoTerrain.isTemp(helper.getLevel(), bp), "restored at " + bp);
			}
			for (int x = 0; x <= 7; x++) {
				for (int zz = 0; zz <= 7; zz++) {
					helper.assertBlockPresent(Blocks.STONE, new BlockPos(x, 1, zz));
				}
			}
			helper.succeed();
		});
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void meteorShowerCleansUpWhenEverythingStops(GameTestHelper helper) {
		floor(helper);
		ServerPlayer p = uravity(helper, 4.5, 2.0, 1.5);
		List<BlockPos> taken = new ArrayList<>();
		ZeroGravityMoves.meteorShower(p, taken);
		helper.assertTrue(!ZeroGravityDebris.displays(p).isEmpty(), "a cloud");
		QuirkKits.of(Quirk.ZERO_GRAVITY).stopAll(p);
		helper.assertTrue(ZeroGravityDebris.displays(p).isEmpty() && ZeroGravityDebris.count(p) == 0, "stopAll removes every piece");
		for (BlockPos bp : taken) {
			helper.assertTrue(!helper.getLevel().getBlockState(bp).isAir() && !GeoTerrain.isTemp(helper.getLevel(), bp), "the ground is back at once");
		}
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 80)
	public void debrisShieldStopsProjectiles(GameTestHelper helper) {
		floor(helper);
		ServerPlayer p = uravity(helper, 4.5, 2.0, 1.5);
		pump(helper, p);
		ZeroGravityMoves.debrisShield(p, null);
		helper.assertTrue(ZeroGravityDebris.shieldUp(p), "the shield orbits");
		Arrow a = EntityType.ARROW.create(helper.getLevel());
		Vec3 from = helper.absoluteVec(new Vec3(4.5, 3.0, 7.0));
		a.moveTo(from.x, from.y, from.z, 0f, 0f);
		a.setDeltaMovement(0, 0, -1.2);
		helper.getLevel().addFreshEntity(a);
		float dmg = QuirkKits.of(Quirk.ZERO_GRAVITY).modifyIncomingDamage(p, helper.getLevel().damageSources().arrow(a, null), 6f);
		helper.assertTrue(dmg == 0f, "projectile damage is stopped");
		helper.runAfterDelay(10, () -> {
			helper.assertTrue(a.isRemoved(), "the arrow was caught by the rubble");
			ZeroGravityMoves.endAll(p);
			helper.assertTrue(ZeroGravityDebris.displays(p).isEmpty(), "stopAll drops the rubble");
			helper.succeed();
		});
	}

	// ---------------------------------------------------------------- G: Comet Toss / Shift+G: Slam

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 60)
	public void cometTossHurlsAFloaterIntoAWall(GameTestHelper helper) {
		floor(helper);
		for (int x = 0; x <= 7; x++) {
			for (int y = 2; y <= 5; y++) {
				helper.getLevel().setBlock(helper.absolutePos(new BlockPos(x, y, 7)), Blocks.STONE.defaultBlockState(), 3);
			}
		}
		ServerPlayer p = uravity(helper, 4.5, 2.0, 1.0);
		Zombie z = zombie(helper, 4.5, 2.0, 3.0, 100);
		z.setNoAi(false);
		ZeroGravityFloat.start(p, z, false);
		helper.assertTrue(ZeroGravityMoves.cometToss(p) == z, "the floated zombie is thrown");
		helper.assertTrue(z.getDeltaMovement().z > 1.5, "hard, along her aim: " + z.getDeltaMovement());
		pump(helper, p);
		helper.runAfterDelay(20, () -> {
			helper.assertTrue(taken(z, 100) >= ZeroGravityConfig.COMET_DAMAGE - EPS, "it smashed into the wall, took " + taken(z, 100));
			helper.assertTrue(grounded(z), "and its gravity is back");
			helper.succeed();
		});
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 120)
	public void zeroGravitySlamLiftsItThenSlamsItDown(GameTestHelper helper) {
		floor(helper);
		ServerPlayer p = uravity(helper, 4.5, 2.0, 1.5);
		Zombie z = zombie(helper, 4.5, 2.0, 4.0, 100);
		z.setNoAi(false);
		double y0 = z.getY();
		helper.assertTrue(ZeroGravityMoves.slam(p) == z, "the slam takes the zombie");
		pump(helper, p);
		helper.runAfterDelay(ZeroGravityConfig.SLAM_LIFT_TICKS - 2, () -> {
			helper.assertTrue(z.getY() > y0 + 4.0, "it shot up, " + (z.getY() - y0));
		});
		helper.runAfterDelay(60, () -> {
			helper.assertTrue(grounded(z), "let go");
			helper.assertTrue(taken(z, 100) >= ZeroGravityConfig.SLAM_DAMAGE + ZeroGravityConfig.TOUCH_DAMAGE - EPS, "slammed, took " + taken(z, 100));
			helper.succeed();
		});
	}

	// ---------------------------------------------------------------- Z: Gunhead / Shift+Z: Field

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 40)
	public void gunheadStrikesTwiceThenThrowsOverHerShoulder(GameTestHelper helper) {
		floor(helper);
		ServerPlayer p = uravity(helper, 4.5, 2.0, 2.5);
		Zombie z = zombie(helper, 4.5, 2.0, 4.5, 100);
		double z0 = z.getZ();
		helper.assertTrue(ZeroGravityMoves.gunhead(p) == z, "aimed");
		ZeroGravityMoves.gunheadStepForTest(p);
		ZeroGravityMoves.gunheadStepForTest(p);
		float sum = ZeroGravityConfig.GUNHEAD_DAMAGE[0] + ZeroGravityConfig.GUNHEAD_DAMAGE[1] + ZeroGravityConfig.GUNHEAD_DAMAGE[2];
		helper.assertTrue(Math.abs(taken(z, 100) - sum) <= EPS, "three hits, took " + taken(z, 100));
		helper.assertTrue(z.getDeltaMovement().z < -0.5 && z.getDeltaMovement().y > 0.3, "thrown back over her shoulder: " + z.getDeltaMovement());
		helper.assertTrue(z.getZ() <= z0 + 0.01, "not pushed forward");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 160)
	public void zeroGravityFieldFloatsEverythingInsideThenDrops(GameTestHelper helper) {
		floor(helper);
		ServerPlayer p = uravity(helper, 4.5, 2.0, 1.5);
		p.setXRot(35f);
		Zombie z = zombie(helper, 4.5, 2.0, 4.5, 100);
		ItemEntity item = new ItemEntity(helper.getLevel(), 0, 0, 0, new ItemStack(Items.APPLE));
		Vec3 at = helper.absoluteVec(new Vec3(3.5, 2.1, 4.5));
		item.moveTo(at.x, at.y, at.z);
		helper.getLevel().addFreshEntity(item);
		ZeroGravityMoves.field(p);
		helper.assertTrue(ZeroGravityMoves.fieldUp(p), "the Field is up");
		helper.assertTrue(floated(z) && item.isNoGravity(), "the zombie and the apple float");
		pump(helper, p);
		helper.runAfterDelay(ZeroGravityConfig.FIELD_TICKS + 5, () -> {
			helper.assertFalse(ZeroGravityMoves.fieldUp(p), "the Field is over");
			helper.assertTrue(grounded(z) && !item.isNoGravity(), "everything dropped");
			helper.succeed();
		});
	}

	// ---------------------------------------------------------------- C: Catch / Shift+C: Settle

	@GameTest(template = EMPTY_STRUCTURE)
	public void catchStopsAFall(GameTestHelper helper) {
		floor(helper);
		ServerPlayer p = uravity(helper, 4.5, 2.0, 1.5);
		Zombie z = zombie(helper, 4.5, 9.0, 5.5, 100);
		z.setOnGround(false);
		z.fallDistance = 6f;
		z.setDeltaMovement(0, -1.0, 0);
		helper.assertTrue(ZeroGravityMoves.catchFalling(p) >= 1, "caught");
		helper.assertTrue(z.fallDistance == 0f && z.hasEffect(MobEffects.SLOW_FALLING) && z.getDeltaMovement().y > 0, "its fall stopped");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 60)
	public void settleLetsEverythingDownSafelyAndVentsNausea(GameTestHelper helper) {
		floor(helper);
		ServerPlayer p = uravity(helper, 4.5, 2.0, 1.5);
		p.setOnGround(true);
		Zombie z = zombie(helper, 4.5, 2.0, 4.0, 100);
		ZeroGravityFloat.start(p, z, false);
		ZeroGravityMoves.meteorShower(p, null);
		Quirks.setMeter(p, 80f);
		ZeroGravityMoves.settle(p);
		helper.assertTrue(grounded(z) && z.hasEffect(MobEffects.SLOW_FALLING), "it floats down softly");
		helper.assertTrue(ZeroGravityDebris.displays(p).isEmpty(), "the rubble dissolved");
		helper.assertTrue(ZeroGravityMoves.settling(p), "she settles");
		pump(helper, p);
		helper.runAfterDelay(20, () -> {
			float vented = 80f - Quirks.meter(p);
			helper.assertTrue(vented >= ZeroGravityConfig.nausea().settlePerSecond * 0.8f, "Nausea drains fast, vented " + vented);
			helper.succeed();
		});
	}

	// ---------------------------------------------------------------- Nausea

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 100)
	public void nauseaRisesWithWeightAndFallsWhenNothingFloats(GameTestHelper helper) {
		floor(helper);
		ServerPlayer p = uravity(helper, 4.5, 2.0, 1.5);
		for (int i = 0; i < 3; i++) {
			ZeroGravityFloat.start(p, zombie(helper, 1.5 + i * 2.0, 2.0, 5.5, 100), false);
		}
		pump(helper, p);
		helper.runAfterDelay(40, () -> {
			float m = Quirks.meter(p);
			helper.assertTrue(m > 1.5f, "nausea rose: " + m);
			ZeroGravityMoves.release(p);
			helper.runAfterDelay(30, () -> {
				helper.assertTrue(Quirks.meter(p) < m, "and falls again: " + Quirks.meter(p));
				helper.succeed();
			});
		});
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 40)
	public void fullNauseaDropsEverythingAndOnlySettleWorks(GameTestHelper helper) {
		floor(helper);
		ServerPlayer p = uravity(helper, 4.5, 2.0, 1.5);
		Zombie z = zombie(helper, 4.5, 2.0, 5.5, 100);
		ZeroGravityFloat.start(p, z, false);
		ZeroGravityMoves.floatSelf(p);
		Quirks.setMeter(p, 99.99f);
		pump(helper, p);
		helper.runAfterDelay(3, () -> {
			helper.assertTrue(Quirks.overloaded(p), "she is sick");
			helper.assertTrue(p.hasEffect(MobEffects.CONFUSION), "Nausea effect");
			helper.assertTrue(grounded(z) && !p.isNoGravity(), "everything dropped, her too");
			QuirkAbilityManager.handle(p, AbilitySlot.SLOT_5, true);
			helper.assertTrue(Quirks.ready(p, ZeroGravityConfig.TOUCH), "Touch was refused while sick");
			QuirkAbilityManager.handle(p, AbilitySlot.SLOT_5, false);
			p.setShiftKeyDown(true);
			QuirkAbilityManager.handle(p, AbilitySlot.SLOT_6, true);
			p.setShiftKeyDown(false);
			helper.assertTrue(ZeroGravityMoves.settling(p), "Shift+C Settle works while sick");
			helper.succeed();
		});
	}

	// ---------------------------------------------------------------- safety nets

	@GameTest(template = EMPTY_STRUCTURE)
	public void revokeAndStopAllAlwaysGiveGravityBack(GameTestHelper helper) {
		floor(helper);
		ServerPlayer p = uravity(helper, 4.5, 2.0, 1.5);
		Zombie a = zombie(helper, 2.5, 2.0, 5.5, 100);
		Zombie b = zombie(helper, 6.5, 2.0, 5.5, 100);
		ZeroGravityFloat.start(p, a, false);
		ZeroGravityFloat.start(p, b, true);
		ZeroGravityMoves.floatSelf(p);
		helper.assertTrue(floated(a) && floated(b) && p.isNoGravity(), "all floating");
		QuirkKits.of(Quirk.ZERO_GRAVITY).stopAll(p);
		helper.assertTrue(grounded(a) && grounded(b) && !p.isNoGravity(), "stopAll: all back");
		ready(p);
		ZeroGravityFloat.start(p, a, false);
		ZeroGravityMoves.floatSelf(p);
		Quirks.revoke(p);
		helper.assertTrue(grounded(a) && !p.isNoGravity() && !p.getTags().contains(ZeroGravity.TAG_FLOAT), "revoke: all back");
		helper.assertTrue(!ZeroGravityFloat.isFloating(a), "nothing is left in the books");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void anOrphanedFloaterGetsItsGravityBackOnLoad(GameTestHelper helper) {
		Zombie z = zombie(helper, 4.5, 2.0, 4.5, 100);
		// what a chunk saved mid-float looks like when it is loaded again
		z.setNoGravity(true);
		z.addTag(ZeroGravity.TAG_FLOAT);
		ZeroGravityFloat.cleanup(z);
		helper.assertTrue(grounded(z), "its gravity is back");
		helper.succeed();
	}
}
