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
import com.projecthero.mod.quirk.zerogravity.ZeroGravityMeter;
import com.projecthero.mod.quirk.zerogravity.ZeroGravityDebris;
import com.projecthero.mod.quirk.zerogravity.ZeroGravityFloat;
import com.projecthero.mod.quirk.zerogravity.ZeroGravityMoves;
import com.projecthero.mod.quirk.zerogravity.ZeroGravityUltimate;
import com.projecthero.mod.quirk.PlusUltra;
import com.projecthero.mod.quirk.meter.QuirkMeters;

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
			// no p.tickCount++ here: the level already counts a mock player's ticks (ServerLevel#tickNonPassenger), and a second
			// step made tickCount jump by 2 -- every "tickCount % 2 / % 20" check (float pickup, the Burning eyes) then never fired.
			// The server's own Quirk tick (AbilityRouter) usually runs first; this one is deduped by Quirks then.
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
		// v0.17.1 revamp layout (docs/QUIRKS_REVAMP_PROPOSAL.md 3.7)
		helper.assertTrue(kit.idsOf(AbilitySlot.SLOT_1)[0].equals(ZeroGravityConfig.COMET) && kit.idsOf(AbilitySlot.SLOT_1)[1].equals(ZeroGravityConfig.BARRAGE),
				"R = Comet Toss / Debris Barrage");
		helper.assertTrue(kit.idsOf(AbilitySlot.SLOT_3)[0].equals(ZeroGravityConfig.LEAP) && kit.idsOf(AbilitySlot.SLOT_3)[1].equals(ZeroGravityConfig.PUSH),
				"X = Zero-G Leap / Weightless Push (movement)");
		helper.assertTrue(ZeroGravityConfig.SELF.equals(kit.holdIdOf(AbilitySlot.SLOT_3)), "hold X = Float Self");
		helper.assertTrue(kit.idsOf(AbilitySlot.SLOT_5)[0].equals(ZeroGravityConfig.TOUCH) && kit.idsOf(AbilitySlot.SLOT_5)[1].equals(ZeroGravityConfig.RELEASE),
				"V = Touch / Release (utility)");
		helper.assertTrue(ZeroGravityConfig.CATCH.equals(kit.holdIdOf(AbilitySlot.SLOT_5)), "hold V = Catch");
		helper.assertTrue(kit.idsOf(AbilitySlot.SLOT_6)[0].equals(ZeroGravityConfig.SHOWER) && kit.idsOf(AbilitySlot.SLOT_6)[1].equals(ZeroGravityConfig.BOMB),
				"C = Meteor Shower / Gravity Bomb (an attack on C again)");
		helper.assertTrue(kit.usableWhileOverloaded(AbilitySlot.SLOT_5, true), "Settle (Shift + hold V) works while sick");
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

	@GameTest(template = EMPTY_STRUCTURE, skyAccess = true, timeoutTicks = 100) // no barrier roof: Release must see open sky
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
			// straight up at the open sky: no Crane spot, it simply drops. (At -70 degrees under the cage's barrier roof the Crane
			// found a cell under the roof, set the planks down there for good, and the hole was -- rightly -- never refilled.)
			p.setXRot(-90f);
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
			float expected = ZeroGravityConfig.nausea().perWeightPerSecond * ZeroGravityConfig.nausea().selfWeight * 2f * ZeroGravityMeter.BUILD; // v0.17.1 pacing
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
		helper.assertTrue(z.getDeltaMovement().z < -0.5 && z.getDeltaMovement().y > 0.2, "thrown back over her shoulder: " + z.getDeltaMovement());
		helper.assertTrue(z.getZ() <= z0 + 0.01, "not pushed forward");
		helper.assertTrue(floated(z) && ZeroGravityFloat.floatedBy(p, z), "v0.17.1: the throw leaves it floating");
		ZeroGravityMoves.endAll(p);
		helper.succeed();
	}

	// ---------------------------------------------------------------- v0.17.1: the Gunhead Meteor combo route

	/** Gunhead's three beats on {@code z}: the last is the throw that floats it and opens Gunhead Meteor. */
	private static void gunheadThrow(GameTestHelper helper, ServerPlayer p, Zombie z) {
		helper.assertTrue(ZeroGravityMoves.gunhead(p) == z, "Gunhead aimed at the zombie");
		ZeroGravityMoves.gunheadStepForTest(p);
		ZeroGravityMoves.gunheadStepForTest(p);
		helper.assertTrue(ZeroGravityFloat.floatedBy(p, z), "the throw floats it");
	}

	/** Lets the lifted zombie go, sets it on the floor at {@code at} (no fall), and returns its health just before the slam lands. */
	private static float landNow(GameTestHelper helper, ServerPlayer p, Zombie z, Vec3 at) {
		ZeroGravityMoves.slamDropForTest(p);
		Vec3 abs = helper.absoluteVec(at);
		z.moveTo(abs.x, abs.y, abs.z);
		z.setDeltaMovement(Vec3.ZERO);
		z.fallDistance = 0f;
		z.setOnGround(true);
		z.invulnerableTime = 0;
		return z.getHealth();
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 60)
	public void gunheadMeteorSlamsTheSameTargetHarder(GameTestHelper helper) {
		floor(helper);
		ServerPlayer p = uravity(helper, 4.5, 2.0, 2.5);
		Zombie z = zombie(helper, 4.5, 2.0, 4.5, 100);
		helper.assertTrue(com.projecthero.mod.quirk.QuirkCombos.routes(Quirk.ZERO_GRAVITY).contains(ZeroGravityMeter.GUNHEAD_METEOR),
				"Zero Gravity has its combo route");
		gunheadThrow(helper, p, z);
		helper.assertTrue(com.projecthero.mod.quirk.QuirkCombos.openWindow(p) == ZeroGravityMeter.GUNHEAD_METEOR, "the throw opens Gunhead Meteor");
		ready(p);
		int before = com.projecthero.mod.quirk.QuirkCombos.count(p);
		helper.assertTrue(ZeroGravityMoves.slam(p) == z, "the slam takes the same zombie");
		helper.assertTrue(com.projecthero.mod.quirk.QuirkCombos.last(p) == ZeroGravityMeter.GUNHEAD_METEOR
				&& com.projecthero.mod.quirk.QuirkCombos.count(p) == before + 1, "Gunhead then Slam on the same target: Gunhead Meteor");
		helper.assertTrue(Math.abs(ZeroGravityMoves.slamBonusForTest(p, z) - 1.5f) < 0.001f, "the slam carries the x1.5 bonus");
		float hp = landNow(helper, p, z, new Vec3(4.5, 2.0, 4.5));
		pump(helper, p);
		helper.runAfterDelay(4, () -> {
			float took = hp - z.getHealth();
			helper.assertTrue(Math.abs(took - ZeroGravityConfig.SLAM_DAMAGE * 1.5f) <= EPS,
					"the slam came down 1.5x harder: took " + took + ", want " + ZeroGravityConfig.SLAM_DAMAGE * 1.5f);
			helper.assertTrue(ZeroGravityMoves.slamBonusForTest(p, z) == 1f, "the bonus is spent");
			ZeroGravityMoves.endAll(p);
			helper.succeed();
		});
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 60)
	public void gunheadMeteorNeedsTheSameTarget(GameTestHelper helper) {
		floor(helper);
		ServerPlayer p = uravity(helper, 4.5, 2.0, 2.5);
		Zombie a = zombie(helper, 4.5, 2.0, 4.5, 100);
		gunheadThrow(helper, p, a);
		// the thrown one is moved well out of reach; a second zombie stands where it was
		Vec3 away = helper.absoluteVec(new Vec3(0.5, 2.0, 0.5));
		a.moveTo(away.x, away.y, away.z);
		Zombie b = zombie(helper, 4.5, 2.0, 4.5, 100);
		ready(p);
		int before = com.projecthero.mod.quirk.QuirkCombos.count(p);
		helper.assertTrue(ZeroGravityMoves.slam(p) == b, "the slam takes the other zombie");
		helper.assertTrue(com.projecthero.mod.quirk.QuirkCombos.count(p) == before, "threw one, slammed another: no combo");
		helper.assertTrue(ZeroGravityMoves.slamBonusForTest(p, b) == 1f, "no bonus on the other zombie");
		float hp = landNow(helper, p, b, new Vec3(4.5, 2.0, 4.5));
		pump(helper, p);
		helper.runAfterDelay(4, () -> {
			float took = hp - b.getHealth();
			helper.assertTrue(Math.abs(took - ZeroGravityConfig.SLAM_DAMAGE) <= EPS, "a plain slam: took " + took);
			ZeroGravityMoves.endAll(p);
			helper.succeed();
		});
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 100)
	public void gunheadMeteorClosesAfterItsWindow(GameTestHelper helper) {
		floor(helper);
		ServerPlayer p = uravity(helper, 4.5, 2.0, 2.5);
		Zombie z = zombie(helper, 4.5, 2.0, 4.5, 100);
		gunheadThrow(helper, p, z);
		int before = com.projecthero.mod.quirk.QuirkCombos.count(p);
		helper.runAfterDelay(ZeroGravityMeter.GUNHEAD_METEOR.window + 6, () -> {
			helper.assertTrue(com.projecthero.mod.quirk.QuirkCombos.openWindow(p) == null, "the window closed");
			// back in front of her, still and in reach
			Vec3 at = helper.absoluteVec(new Vec3(4.5, 2.0, 4.5));
			z.moveTo(at.x, at.y, at.z);
			z.setDeltaMovement(Vec3.ZERO);
			ready(p);
			helper.assertTrue(ZeroGravityMoves.slam(p) == z, "the slam takes the zombie");
			helper.assertTrue(com.projecthero.mod.quirk.QuirkCombos.count(p) == before, "too late: no combo");
			helper.assertTrue(ZeroGravityMoves.slamBonusForTest(p, z) == 1f, "no bonus");
			ZeroGravityMoves.endAll(p);
			helper.succeed();
		});
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
			helper.assertTrue(m > 1.5f * ZeroGravityMeter.BUILD, "nausea rose (paced): " + m);
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
			QuirkAbilityManager.handle(p, AbilitySlot.SLOT_5, false);
			helper.assertTrue(Quirks.ready(p, ZeroGravityConfig.TOUCH), "Touch was refused while sick");
			p.setShiftKeyDown(true);
			QuirkAbilityManager.handle(p, AbilitySlot.SLOT_5, true); // Shift + hold V
		});
		helper.runAfterDelay(3 + ZeroGravityConfig.HOLD_TICKS + 3, () -> {
			helper.assertTrue(ZeroGravityMoves.settling(p), "Shift + hold V Settle works while sick");
			QuirkAbilityManager.handle(p, AbilitySlot.SLOT_5, false);
			p.setShiftKeyDown(false);
			helper.succeed();
		});
	}

	// ================================================================ v0.17.1 revamp

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 60)
	public void tapXLeapsHoldXFloatsWhileHeld(GameTestHelper helper) {
		floor(helper);
		ServerPlayer p = uravity(helper, 4.5, 2.0, 1.5);
		pump(helper, p);
		QuirkAbilityManager.handle(p, AbilitySlot.SLOT_3, true);
		helper.runAfterDelay(1, () -> {
			QuirkAbilityManager.handle(p, AbilitySlot.SLOT_3, false);
			helper.assertTrue(ZeroGravityMoves.leaping(p) && !ZeroGravity.selfFloating(p), "a tap: the Zero-G Leap");
			ZeroGravityMoves.endAll(p);
			ready(p);
			QuirkAbilityManager.handle(p, AbilitySlot.SLOT_3, true);
		});
		helper.runAfterDelay(2 + ZeroGravityConfig.HOLD_TICKS + 3, () -> {
			helper.assertTrue(ZeroGravity.selfFloating(p) && p.isNoGravity(), "held: Float Self");
			QuirkAbilityManager.handle(p, AbilitySlot.SLOT_3, false);
			helper.assertTrue(!ZeroGravity.selfFloating(p) && !p.isNoGravity(), "let go: her gravity is back");
			helper.succeed();
		});
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 60)
	public void tapVTouchesHoldVCatches(GameTestHelper helper) {
		floor(helper);
		ServerPlayer p = uravity(helper, 4.5, 2.0, 1.5);
		Zombie near = zombie(helper, 4.5, 2.0, 3.5, 100);
		pump(helper, p);
		QuirkAbilityManager.handle(p, AbilitySlot.SLOT_5, true);
		helper.runAfterDelay(1, () -> {
			QuirkAbilityManager.handle(p, AbilitySlot.SLOT_5, false);
			helper.assertTrue(floated(near), "a tap: Touch");
			ZeroGravityMoves.endAll(p);
			ready(p);
			QuirkAbilityManager.handle(p, AbilitySlot.SLOT_5, true);
		});
		helper.runAfterDelay(2 + ZeroGravityConfig.HOLD_TICKS + 3, () -> {
			helper.assertTrue(ZeroGravityMoves.catching(p), "held: Catch");
			Zombie falling = zombie(helper, 6.5, 9.0, 4.5, 100);
			falling.setOnGround(false);
			falling.fallDistance = 6f;
			falling.setDeltaMovement(0, -1.0, 0);
			helper.runAfterDelay(4, () -> {
				helper.assertTrue(falling.hasEffect(MobEffects.SLOW_FALLING), "whoever falls while she holds it is caught");
				QuirkAbilityManager.handle(p, AbilitySlot.SLOT_5, false);
				helper.assertFalse(ZeroGravityMoves.catching(p), "let go: the catch ends");
				helper.succeed();
			});
		});
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void weightlessPushDashesTwiceBeforeShesLanded(GameTestHelper helper) {
		ServerPlayer p = uravity(helper, 4.5, 6.0, 1.5);
		p.setOnGround(false);
		helper.assertTrue(ZeroGravityMoves.push(p), "first push");
		helper.assertTrue(p.getDeltaMovement().z > 1.2, "a hard dash along her look: " + p.getDeltaMovement());
		helper.assertTrue(ZeroGravityMoves.pushesLeft(p) == ZeroGravityConfig.PUSH_AIR_USES - 1, "one used");
		ready(p);
		helper.assertTrue(ZeroGravityMoves.push(p), "second push");
		ready(p);
		helper.assertFalse(ZeroGravityMoves.push(p), "no third before she lands");
		p.setOnGround(true);
		ready(p);
		helper.assertTrue(ZeroGravityMoves.push(p), "landed: pushes again");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 80)
	public void debrisBarrageFiresEveryPieceAlongTheAim(GameTestHelper helper) {
		floor(helper);
		ServerPlayer p = uravity(helper, 4.5, 2.0, 1.0);
		Zombie z = zombie(helper, 4.5, 2.0, 5.5, 100);
		p.lookAt(net.minecraft.commands.arguments.EntityAnchorArgument.Anchor.EYES, z.position().add(0, 1.0, 0));
		int n = ZeroGravityMoves.debrisShield(p, null);
		helper.assertTrue(n > 0, "the shield has rubble");
		pump(helper, p);
		helper.runAfterDelay(4, () -> {
			int fired = ZeroGravityMoves.barrage(p);
			helper.assertTrue(fired == n, "every piece fires, " + fired + " of " + n);
			helper.assertFalse(ZeroGravityDebris.shieldUp(p), "the shield is spent");
		});
		helper.runAfterDelay(40, () -> {
			helper.assertTrue(taken(z, 100) >= ZeroGravityConfig.BARRAGE_DAMAGE - EPS, "the barrage hit, took " + taken(z, 100));
			helper.succeed();
		});
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 60)
	public void cometTossWithNothingAfloatThrowsAChunkOfGround(GameTestHelper helper) {
		floor(helper);
		ServerPlayer p = uravity(helper, 4.5, 2.0, 1.0);
		Zombie z = zombie(helper, 4.5, 2.0, 6.5, 100);
		p.lookAt(net.minecraft.commands.arguments.EntityAnchorArgument.Anchor.EYES, z.position().add(0, 1.0, 0));
		Object got = ZeroGravityMoves.cometToss(p);
		helper.assertTrue("chunk".equals(got), "a chunk of the ground, got " + got);
		helper.assertTrue(ZeroGravityDebris.count(p, ZeroGravityDebris.THROWN) == 1, "flying");
		pump(helper, p);
		helper.runAfterDelay(30, () -> {
			helper.assertTrue(taken(z, 100) >= ZeroGravityConfig.COMET_DAMAGE - EPS, "it hit for 10, took " + taken(z, 100));
			helper.assertTrue(ZeroGravityDebris.displays(p).isEmpty(), "and smashed");
			for (int x = 0; x <= 7; x++) {
				for (int zz = 0; zz <= 7; zz++) {
					helper.assertBlockPresent(Blocks.STONE, new BlockPos(x, 1, zz));
				}
			}
			helper.succeed();
		});
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 200)
	public void gravityBombLiftsASlabAndDropsItOnTheAim(GameTestHelper helper) {
		// a three-deep floor to tear the slab out of
		for (int x = 0; x <= 9; x++) {
			for (int y = -1; y <= 1; y++) {
				for (int z = 0; z <= 9; z++) {
					helper.getLevel().setBlock(helper.absolutePos(new BlockPos(x, y, z)), Blocks.DIRT.defaultBlockState(), 3);
				}
			}
		}
		ServerPlayer p = uravity(helper, 4.5, 2.0, 0.5);
		Zombie z = zombie(helper, 4.5, 2.0, 8.0, 100);
		int n = ZeroGravityMoves.gravityBomb(p);
		helper.assertTrue(n > 20 && n <= ZeroGravityConfig.BOMB_WIDTH * ZeroGravityConfig.BOMB_WIDTH * ZeroGravityConfig.BOMB_DEPTH, "a 5x3x5 slab, " + n);
		helper.assertTrue(ZeroGravityDebris.bombUp(p), "it hangs overhead");
		pump(helper, p);
		helper.runAfterDelay(30, () -> {
			p.lookAt(net.minecraft.commands.arguments.EntityAnchorArgument.Anchor.EYES, z.position());
			helper.assertTrue(ZeroGravityMoves.gravityBomb(p) == -1, "Shift+C again drops it");
		});
		helper.runAfterDelay(110, () -> {
			helper.assertFalse(ZeroGravityDebris.bombUp(p), "it landed");
			helper.assertTrue(taken(z, 100) >= ZeroGravityConfig.BOMB_DAMAGE * 0.5f - EPS, "it crushed the target, took " + taken(z, 100));
			helper.assertTrue(ZeroGravityDebris.displays(p).isEmpty(), "the slab is gone");
			for (int x = 2; x <= 6; x++) {
				for (int zz = 3; zz <= 7; zz++) {
					helper.assertBlockPresent(Blocks.DIRT, new BlockPos(x, 1, zz));
				}
			}
			helper.succeed();
		});
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 120)
	public void craneSetsATouchedBlockDownWhereShePoints(GameTestHelper helper) {
		floor(helper);
		ServerPlayer p = uravity(helper, 4.5, 2.0, 1.5);
		BlockPos rel = new BlockPos(4, 2, 3);
		BlockPos abs = helper.absolutePos(rel);
		helper.getLevel().setBlock(abs, Blocks.OAK_PLANKS.defaultBlockState(), 3);
		p.lookAt(net.minecraft.commands.arguments.EntityAnchorArgument.Anchor.EYES, Vec3.atCenterOf(abs));
		Object got = ZeroGravityMoves.touch(p);
		if (!AbilityHelpers.canGrief()) {
			helper.assertTrue(got == null, "terrain damage off: a block is never taken");
			helper.succeed();
			return;
		}
		helper.assertTrue(abs.equals(got), "touched: it floats");
		pump(helper, p);
		helper.runAfterDelay(10, () -> {
			// point at the top of the floor, three blocks further on
			p.lookAt(net.minecraft.commands.arguments.EntityAnchorArgument.Anchor.EYES, helper.absoluteVec(new Vec3(4.5, 2.0, 6.5)));
			helper.assertTrue(ZeroGravityMoves.release(p) == 1, "Release");
		});
		helper.runAfterDelay(80, () -> {
			helper.assertBlockPresent(Blocks.OAK_PLANKS, new BlockPos(4, 2, 6));
			helper.assertTrue(helper.getLevel().getBlockState(abs).isAir() && !GeoTerrain.isTemp(helper.getLevel(), abs),
					"moved, not broken: the old cell stays empty and is off the ledger");
			helper.assertTrue(ZeroGravityDebris.displays(p).isEmpty(), "no display left");
			helper.succeed();
		});
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 100)
	public void floatedAnimalsFollowHerThenSettle(GameTestHelper helper) {
		floor(helper);
		ServerPlayer p = uravity(helper, 4.5, 2.0, 1.5);
		net.minecraft.world.entity.animal.Cow cow = EntityType.COW.create(helper.getLevel());
		Vec3 at = helper.absoluteVec(new Vec3(4.5, 2.0, 3.0));
		cow.moveTo(at.x, at.y, at.z, 0f, 0f);
		helper.getLevel().addFreshEntity(cow);
		// she reaches for it: a cow 1.5 blocks away is all below her level gaze (its top is 0.2 under her eyes, its middle ~31
		// degrees down -- outside the touch's 18-degree assist), so a straight-ahead touch rightly misses it
		p.lookAt(net.minecraft.commands.arguments.EntityAnchorArgument.Anchor.EYES, cow.position().add(0, cow.getBbHeight() * 0.5, 0));
		helper.assertTrue(ZeroGravityMoves.touch(p) == cow, "the touch floats the cow");
		helper.assertTrue(ZeroGravityFloat.herded(p, cow), "and it follows her");
		pump(helper, p);
		p.teleportTo(p.getX() + 6.0, p.getY(), p.getZ());
		double d0 = cow.distanceTo(p);
		helper.runAfterDelay(40, () -> {
			helper.assertTrue(cow.distanceTo(p) < d0 - 2.0, "it drifted after her: " + d0 + " -> " + cow.distanceTo(p));
			ZeroGravityFloat.endHerdForTest(p, cow);
		});
		helper.runAfterDelay(44, () -> {
			helper.assertTrue(!cow.isNoGravity() && cow.hasEffect(MobEffects.SLOW_FALLING), "20 s later it is set down softly");
			helper.succeed();
		});
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void lightOnHerFeetBelowQueasy(GameTestHelper helper) {
		ServerPlayer p = uravity(helper, 4.5, 2.0, 1.5);
		QuirkKit kit = QuirkKits.of(Quirk.ZERO_GRAVITY);
		var fall = helper.getLevel().damageSources().fall();
		Quirks.setMeter(p, 20f);
		helper.assertTrue(kit.modifyIncomingDamage(p, fall, 6f) == 0f, "Steady: no fall damage");
		Quirks.setMeter(p, 60f);
		helper.assertTrue(kit.modifyIncomingDamage(p, fall, 6f) == 6f, "Queasy: it hurts again");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 60)
	public void droppedItemsDriftToHer(GameTestHelper helper) {
		floor(helper);
		ServerPlayer p = uravity(helper, 4.5, 2.0, 1.5);
		ItemEntity item = new ItemEntity(helper.getLevel(), 0, 0, 0, new ItemStack(Items.APPLE));
		Vec3 at = helper.absoluteVec(new Vec3(4.5, 2.1, 5.5));
		item.moveTo(at.x, at.y, at.z);
		item.setPickUpDelay(0);
		helper.getLevel().addFreshEntity(item);
		double d0 = item.distanceTo(p);
		pump(helper, p);
		helper.runAfterDelay(30, () -> {
			helper.assertTrue(!item.isAlive() || item.distanceTo(p) < d0 - 1.0, "float pickup: it drifted in, " + d0 + " -> " + item.distanceTo(p));
			helper.succeed();
		});
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void nauseaBandsRaiseCapacityThenDropDamage(GameTestHelper helper) {
		ServerPlayer p = uravity(helper, 4.5, 2.0, 1.5);
		Quirks.setMeter(p, 20f);
		helper.assertTrue(ZeroGravityMoves.capacity(p, ZeroGravityConfig.TOUCH_MAX) == ZeroGravityConfig.TOUCH_MAX, "Steady: 5");
		helper.assertTrue(QuirkMeters.damageMult(p) == 1f, "x1");
		Quirks.setMeter(p, 60f);
		helper.assertTrue(ZeroGravityMoves.capacity(p, ZeroGravityConfig.TOUCH_MAX) == 6, "Queasy: +25% float capacity");
		Quirks.setMeter(p, 90f);
		helper.assertTrue(Math.abs(QuirkMeters.damageMult(p) - 1.3f) < 1.0e-4f, "Pushing it: x1.3 drop damage");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 60)
	public void floatedThingsTurnSlowly(GameTestHelper helper) {
		floor(helper);
		ServerPlayer p = uravity(helper, 4.5, 2.0, 1.5);
		Zombie z = zombie(helper, 4.5, 2.0, 3.5, 100);
		helper.assertTrue(ZeroGravityMoves.touch(p) == z, "floats");
		float y0 = z.getYRot();
		pump(helper, p);
		helper.runAfterDelay(20, () -> {
			helper.assertTrue(Math.abs(z.getYRot() - y0) > 10f, "it revolves while it floats: " + y0 + " -> " + z.getYRot());
			ZeroGravityMoves.endAll(p);
			helper.succeed();
		});
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 160)
	public void fullReleaseFloatsEverythingThenCrashesItDown(GameTestHelper helper) {
		floor(helper);
		ServerPlayer p = uravity(helper, 4.5, 2.0, 4.0);
		Zombie a = zombie(helper, 1.5, 2.0, 6.5, 100);
		Zombie b = zombie(helper, 7.0, 2.0, 1.5, 100);
		Zombie boss = zombie(helper, 6.5, 2.0, 6.5, 500);
		a.setNoAi(false); // (a NoAI mob does not move at all: no float, no fall)
		b.setNoAi(false);
		int n = ZeroGravityUltimate.fire(p);
		helper.assertTrue(n >= 3, "everything round her floats, " + n);
		helper.assertTrue(floated(a) && floated(b), "the mobs float up");
		helper.assertTrue(boss.isNoGravity() && boss.hasEffect(MobEffects.MOVEMENT_SLOWDOWN), "the boss is rooted and lifted");
		pump(helper, p);
		helper.runAfterDelay(30, () -> {
			helper.assertTrue(a.getY() > helper.absoluteVec(new Vec3(0, 4.0, 0)).y, "risen high, y " + a.getY());
			helper.assertTrue(Quirks.meter(p) < 5f, "no Nausea builds while it runs: " + Quirks.meter(p));
			ZeroGravityUltimate.clapForTest(p);
			helper.assertTrue(grounded(a) && grounded(b) && !boss.isNoGravity(), "Release!: everything has its gravity back");
		});
		helper.runAfterDelay(30 + ZeroGravityConfig.FR_SICK_DELAY + 8, () -> {
			helper.assertTrue(taken(a, 100) >= ZeroGravityConfig.FR_SLAM_DAMAGE - EPS, "slammed down, took " + taken(a, 100));
			helper.assertTrue(Quirks.overloaded(p) && Quirks.meter(p) >= 90f, "the drawback: sick (Nausea set to 100, draining), " + Quirks.meter(p));
			helper.assertTrue(!ZeroGravityUltimate.running(p), "over");
			helper.succeed();
		});
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 60)
	public void plusUltraHoldFiresFullRelease(GameTestHelper helper) {
		floor(helper);
		ServerPlayer p = uravity(helper, 4.5, 2.0, 4.0);
		Zombie z = zombie(helper, 4.5, 2.0, 6.5, 100);
		pump(helper, p);
		PlusUltra.set(p, 100f);
		QuirkAbilityManager.handle(p, AbilitySlot.SLOT_6, true);
		helper.runAfterDelay(PlusUltra.HOLD_TICKS + 3, () -> {
			helper.assertTrue(ZeroGravityUltimate.running(p), "hold C at 100: Full Release");
			helper.assertTrue(floated(z), "it floats");
			QuirkAbilityManager.handle(p, AbilitySlot.SLOT_6, false);
			ZeroGravityMoves.endAll(p);
			helper.assertTrue(grounded(z), "stopAll lets it down");
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
