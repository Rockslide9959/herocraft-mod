package com.projecthero.mod.gametest;

import java.util.List;

import com.projecthero.mod.hero.Ability;
import com.projecthero.mod.hero.AbilityHandlers;
import com.projecthero.mod.hero.AbilityRouter;
import com.projecthero.mod.hero.AbilitySlot;
import com.projecthero.mod.hero.ExperimentalPowers;
import com.projecthero.mod.hero.Power;
import com.projecthero.mod.hero.Powers;
import com.projecthero.mod.hero.mutation.MutationManager;
import com.projecthero.mod.hero.power.p05.GeoBareHands;
import com.projecthero.mod.hero.power.p05.GeoBoulderEntity;
import com.projecthero.mod.hero.power.p05.GeoFormEntity;
import com.projecthero.mod.hero.power.p05.GeoRockEntity;
import com.projecthero.mod.hero.power.p05.GeoTerrain;
import com.projecthero.mod.hero.power.p05.GeokinesisHandlers;
import com.projecthero.mod.hero.visual.MutationMeters;
import com.projecthero.mod.hero.visual.MutationVisuals;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * v0.16.5 Geokinesis rework (docs/V0165_POWERS_PROPOSAL.md section 9): six keys (+ Shift versions) on a 0-100 Earth meter
 * that refills fast only on natural ground -- the meter, every move's numbers, the passives, Seismic Sense staying off the
 * server glow flag, and the temporary terrain (the wall, the sinkhole) always going back. Mock players are not reliably
 * ticked by the server, so the tests drive {@link ExperimentalPowers#serverTick} themselves. Damage dummies are no-AI iron
 * golems (100 HP, no armour, so the numbers come through exactly).
 */
public class GeokinesisV0165GameTests implements FabricGameTest {
	private static final String K = GeokinesisHandlers.KEY;

	// ---------------- helpers ----------------

	/**
	 * A survival mock geokinetic at relative (7.5, 2, 1.5) facing +Z, on a floor of {@code floor} (4 deep, y -2..1) with a
	 * clear 15x5x15 room above it.
	 */
	private static ServerPlayer hero(GameTestHelper helper, BlockState floor) {
		ServerPlayer p = helper.makeMockServerPlayerInLevel();
		p.setGameMode(GameType.SURVIVAL);
		for (BlockPos pos : BlockPos.betweenClosed(helper.absolutePos(new BlockPos(0, 2, 0)), helper.absolutePos(new BlockPos(14, 6, 15)))) {
			helper.getLevel().setBlock(pos, Blocks.AIR.defaultBlockState(), 2);
		}
		for (BlockPos pos : BlockPos.betweenClosed(helper.absolutePos(new BlockPos(0, -2, 0)), helper.absolutePos(new BlockPos(14, 1, 15)))) {
			helper.getLevel().setBlock(pos, floor, 2);
		}
		Vec3 at = helper.absoluteVec(new Vec3(7.5, 2.0, 1.5));
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
		return p;
	}

	private static ServerPlayer hero(GameTestHelper helper) {
		return hero(helper, Blocks.STONE.defaultBlockState());
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

	/** A no-AI iron golem dummy (100 HP, no armour) at relative (x, 2, z). */
	private static IronGolem dummy(GameTestHelper helper, int x, int z) {
		IronGolem g = helper.spawn(EntityType.IRON_GOLEM, new BlockPos(x, 2, z));
		g.setPersistenceRequired();
		g.setNoAi(true);
		return g;
	}

	private static Zombie zombie(GameTestHelper helper, int x, int z) {
		Zombie zb = helper.spawn(EntityType.ZOMBIE, new BlockPos(x, 2, z));
		zb.setPersistenceRequired();
		zb.setNoAi(true);
		zb.setOnGround(true);
		return zb;
	}

	private static void assertNear(GameTestHelper helper, double expected, double got, String what) {
		helper.assertTrue(Math.abs(expected - got) < 0.05, what + ": expected " + expected + ", got " + got);
	}

	private static <T extends net.minecraft.world.entity.Entity> List<T> around(GameTestHelper helper, Class<T> type, ServerPlayer p,
			double r) {
		return helper.getLevel().getEntitiesOfClass(type, p.getBoundingBox().inflate(r));
	}

	// ---------------- registry / HUD ----------------

	@GameTest(template = EMPTY_STRUCTURE)
	public void sixKeysEarthMeterAndEnabled(GameTestHelper helper) {
		Power power = Powers.byKey(K);
		helper.assertTrue(Powers.isEnabled(power) && Powers.isMutation(power), "Geokinesis is an enabled mutation again");
		helper.assertTrue(power.abilities().size() == 6, "six abilities (no H / N), got " + power.abilities().size());
		String[] ids = { "rock_shot", "earth_grip", "rock_surf", "earthquake", "stone_wall", "tunnel" };
		for (int i = 0; i < ids.length; i++) {
			Ability a = power.ability(AbilitySlot.byNumber(i + 1));
			helper.assertTrue(a != null && a.id().equals(ids[i]), "slot " + (i + 1) + " should be " + ids[i]);
			helper.assertTrue(AbilityHandlers.has(power, a), ids[i] + " has a handler");
		}
		var earth = MutationMeters.get(K, "earth");
		helper.assertTrue(earth != null && earth.always() && earth.style() == MutationMeters.Style.HAIRLINE,
				"Earth is an always-on Hairline");
		for (String flag : new String[] { "p05.armor", "p05.surf", "p05.tunnel", "p05.lifting" }) {
			helper.assertTrue(MutationVisuals.registeredFlags().contains(flag), flag + " is synced");
		}
		helper.succeed();
	}

	// ---------------- the Earth meter ----------------

	@GameTest(template = EMPTY_STRUCTURE)
	public void earthRefillsFastOnlyOnNaturalGround(GameTestHelper helper) {
		ServerPlayer p = hero(helper);
		helper.assertTrue(GeokinesisHandlers.earth(p) >= 99.9f, "the meter starts full");
		GeokinesisHandlers.setEarth(p, 50);
		p.setOnGround(true);
		tick(p, 20);
		assertNear(helper, 65, res(p, "earth"), "15 a second on stone");
		// planks are not natural ground
		BlockPos under = BlockPos.containing(p.getX(), p.getY() - 0.2, p.getZ());
		helper.getLevel().setBlock(under, Blocks.OAK_PLANKS.defaultBlockState(), 2);
		GeokinesisHandlers.setEarth(p, 50);
		p.setOnGround(true);
		tick(p, 20);
		assertNear(helper, 55, res(p, "earth"), "5 a second off natural ground");
		// in the air: also the slow rate
		helper.getLevel().setBlock(under, Blocks.STONE.defaultBlockState(), 2);
		GeokinesisHandlers.setEarth(p, 50);
		p.setOnGround(false);
		tick(p, 20);
		assertNear(helper, 55, res(p, "earth"), "5 a second in the air");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void movesRefuseWithoutTheEarthToPay(GameTestHelper helper) {
		ServerPlayer p = hero(helper);
		GeokinesisHandlers.setEarth(p, 7.0f);
		press(p, 1);
		helper.assertTrue(around(helper, GeoRockEntity.class, p, 6).isEmpty(), "Rock Shot (8) can't fire on 7 Earth");
		assertNear(helper, 7, res(p, "earth"), "nothing spent");
		helper.succeed();
	}

	// ---------------- R ----------------

	@GameTest(template = EMPTY_STRUCTURE)
	public void rockShotRisesFromTheGroundAndHits12(GameTestHelper helper) {
		ServerPlayer p = hero(helper, Blocks.DEEPSLATE.defaultBlockState());
		press(p, 1);
		var rocks = around(helper, GeoRockEntity.class, p, 6);
		helper.assertTrue(rocks.size() == 1, "one rock");
		GeoRockEntity rock = rocks.get(0);
		assertNear(helper, 12, rock.damage(), "Rock Shot damage");
		helper.assertTrue(rock.block().is(Blocks.DEEPSLATE), "the rock is torn from the deepslate floor");
		helper.assertTrue(rock.rising() == GeoRockEntity.RISE_TICKS, "it rises out of the ground before it flies");
		assertNear(helper, 92, res(p, "earth"), "8 Earth");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void boulderTossLiftsAndShattersFor26(GameTestHelper helper) {
		ServerPlayer p = hero(helper);
		IronGolem g = dummy(helper, 7, 9);
		p.setShiftKeyDown(true);
		AbilityRouter.handleInput(p, 1, true);
		p.setShiftKeyDown(false);
		var boulders = around(helper, GeoBoulderEntity.class, p, 6);
		helper.assertTrue(boulders.size() == 1 && boulders.get(0).held(), "a boulder is lifted and held");
		helper.assertTrue(GeokinesisHandlers.lifting(p), "lifting");
		assertNear(helper, 70, res(p, "earth"), "30 Earth");
		GeoBoulderEntity b = boulders.get(0);
		int hit = b.shatter(helper.getLevel(), g.position());
		helper.assertTrue(hit == 1, "the shatter hits the golem");
		assertNear(helper, 74, g.getHealth(), "26 damage");
		helper.assertTrue(!around(helper, GeoRockEntity.class, p, 16).isEmpty(), "it bursts into shards");
		helper.assertTrue(b.isRemoved(), "the boulder is gone");
		tick(p, 1);
		helper.assertFalse(GeokinesisHandlers.lifting(p), "the lift ends with the boulder");
		helper.succeed();
	}

	// ---------------- G ----------------

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 40)
	public void earthGripRootsTheTarget(GameTestHelper helper) {
		ServerPlayer p = hero(helper);
		Zombie z = zombie(helper, 7, 6);
		press(p, 2);
		var hands = around(helper, GeoFormEntity.class, p, 10);
		helper.assertTrue(hands.size() == 1 && hands.get(0).kind() == GeoFormEntity.GRIP && hands.get(0).targetId() == z.getId(),
				"stone hands grab the zombie");
		helper.assertTrue(hands.get(0).life() == GeokinesisHandlers.GRIP_TICKS, "for 3 s");
		assertNear(helper, 85, res(p, "earth"), "15 Earth");
		Vec3 start = z.position();
		helper.runAfterDelay(6, () -> {
			z.setDeltaMovement(1.0, 0, 0);
			helper.runAfterDelay(4, () -> {
				helper.assertTrue(z.position().distanceTo(start) < 0.3, "the zombie is held in place, moved "
						+ z.position().distanceTo(start));
				helper.succeed();
			});
		});
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 60)
	public void sinkholeBuriesTheTargetAndTheGroundGoesBack(GameTestHelper helper) {
		ServerPlayer p = hero(helper);
		Zombie z = zombie(helper, 7, 6);
		BlockPos feet = z.blockPosition();
		shiftPress(p, 2);
		assertNear(helper, 65, res(p, "earth"), "35 Earth");
		assertNear(helper, GeokinesisHandlers.SINKHOLE_CD, res(p, "sink_cd"), "15 s cooldown");
		helper.runAfterDelay(GeokinesisHandlers.SINKHOLE_CLOSE_TICKS + 2, () -> {
			helper.assertTrue(z.blockPosition().getY() == feet.getY() - 3, "dropped 3 blocks, at " + z.blockPosition() + " from " + feet);
			helper.assertTrue(helper.getLevel().getBlockState(feet.below()).is(Blocks.STONE)
					&& helper.getLevel().getBlockState(feet.below(2)).is(Blocks.STONE), "the ground closes over it");
			helper.assertTrue(helper.getLevel().getBlockState(feet.below(3)).isAir(), "its pocket stays while it is down there");
			helper.assertTrue(z.isInWall(), "it is suffocating");
			z.discard();
			helper.runAfterDelay(20, () -> {
				helper.assertTrue(helper.getLevel().getBlockState(feet.below(3)).is(Blocks.STONE), "the pocket is filled once it's gone");
				helper.succeed();
			});
		});
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void sinkholeRefusesBossesAndHardGround(GameTestHelper helper) {
		ServerPlayer p = hero(helper, Blocks.OAK_PLANKS.defaultBlockState());
		Zombie z = zombie(helper, 7, 6);
		BlockPos feet = z.blockPosition();
		shiftPress(p, 2);
		helper.assertTrue(z.blockPosition().equals(feet), "planks are not natural ground: nothing happens");
		assertNear(helper, 100, res(p, "earth"), "nothing spent");
		helper.assertTrue(helper.getLevel().getBlockState(feet.below()).is(Blocks.OAK_PLANKS), "the floor is untouched");
		helper.succeed();
	}

	// ---------------- X ----------------

	@GameTest(template = EMPTY_STRUCTURE)
	public void rockSurfTogglesWithAStepAssist(GameTestHelper helper) {
		ServerPlayer p = hero(helper);
		press(p, 3);
		helper.assertTrue(GeokinesisHandlers.surfing(p), "surfing");
		assertNear(helper, 80, res(p, "earth"), "20 Earth");
		tick(p, 1);
		Vec3 v = p.getDeltaMovement();
		helper.assertTrue(v.z > 0.7, "carried forward fast, got " + v);
		helper.assertTrue(p.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.STEP_HEIGHT) >= 3.99, "a 4-block step assist");
		float before = res(p, "earth");
		for (int i = 0; i < 200; i++) {
			p.setOnGround(true);
			tick(p, 1);
		}
		helper.assertTrue(GeokinesisHandlers.surfing(p), "v0.16.5: no time limit -- still surfing after 10 s");
		assertNear(helper, before - GeokinesisHandlers.COST_SURF_PER_SECOND * 10, res(p, "earth"), "1 Earth a second, no refill while riding");
		press(p, 3);
		helper.assertFalse(GeokinesisHandlers.surfing(p), "X again steps off");
		helper.assertTrue(p.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.STEP_HEIGHT) < 1.0, "and the step assist goes");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void tectonicPillarLaunchesTwelveBlocks(GameTestHelper helper) {
		ServerPlayer p = hero(helper);
		Zombie z = zombie(helper, 7, 6);
		shiftPress(p, 3);
		assertNear(helper, GeokinesisHandlers.PILLAR_LAUNCH, z.getDeltaMovement().y, "the target is launched");
		assertNear(helper, 85, res(p, "earth"), "15 Earth");
		helper.assertTrue(around(helper, GeoFormEntity.class, p, 10).stream().anyMatch(f -> f.kind() == GeoFormEntity.PILLAR),
				"a pillar model rises");
		// ~12 blocks with vanilla gravity and drag
		double y = 0, v = GeokinesisHandlers.PILLAR_LAUNCH, top = 0;
		for (int i = 0; i < 60; i++) {
			y += v;
			v = (v - 0.08) * 0.98;
			top = Math.max(top, y);
		}
		helper.assertTrue(Math.abs(top - 12.0) < 0.3, "apex " + top);
		// aiming at nothing: it launches you, with no fall damage after
		z.discard();
		shiftPress(p, 3);
		assertNear(helper, GeokinesisHandlers.PILLAR_LAUNCH, p.getDeltaMovement().y, "you are launched");
		helper.assertTrue(res(p, "nofall") > 0, "a no-fall window");
		helper.succeed();
	}

	// ---------------- Z ----------------

	@GameTest(template = EMPTY_STRUCTURE)
	public void earthquakeRingHits14WithinTen(GameTestHelper helper) {
		ServerPlayer p = hero(helper);
		IronGolem near = dummy(helper, 7, 6);
		IronGolem edge = dummy(helper, 7, 10);
		IronGolem far = dummy(helper, 7, 14);
		press(p, 4);
		var waves = around(helper, GeoFormEntity.class, p, 4);
		helper.assertTrue(waves.size() == 1 && waves.get(0).kind() == GeoFormEntity.QUAKE, "a quake ripple");
		assertNear(helper, 70, res(p, "earth"), "30 Earth");
		waves.get(0).finishQuake();
		assertNear(helper, 86, near.getHealth(), "14 near");
		assertNear(helper, 86, edge.getHealth(), "14 at the edge (8.5 blocks)");
		assertNear(helper, 100, far.getHealth(), "nothing past 10 blocks");
		helper.assertTrue(near.hasEffect(MobEffects.MOVEMENT_SLOWDOWN), "Slowness");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 40)
	public void earthSpikesLineHits18(GameTestHelper helper) {
		ServerPlayer p = hero(helper);
		IronGolem g = dummy(helper, 7, 8);
		shiftPress(p, 4);
		assertNear(helper, 75, res(p, "earth"), "25 Earth");
		helper.succeedWhen(() -> {
			assertNear(helper, 82, g.getHealth(), "18 from the spike line");
			helper.assertTrue(around(helper, GeoFormEntity.class, p, 14).stream().anyMatch(f -> f.kind() == GeoFormEntity.SPIKE),
					"spike models");
		});
	}

	// ---------------- V ----------------

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 30)
	public void stoneWallIsFiveByFourAndAlwaysGoesBack(GameTestHelper helper) {
		ServerPlayer p = hero(helper);
		press(p, 5);
		assertNear(helper, 85, res(p, "earth"), "15 Earth");
		BlockPos centre = BlockPos.containing(p.position().add(0, 0, 2.6));
		helper.runAfterDelay(5, () -> {
			var level = helper.getLevel();
			int placed = 0;
			for (int x = -2; x <= 2; x++) {
				for (int y = 0; y < GeokinesisHandlers.WALL_HEIGHT; y++) {
					BlockPos c = centre.offset(x, y, 0);
					if (level.getBlockState(c).is(Blocks.STONE) && GeoTerrain.isTemp(level, c)) {
						placed++;
					}
				}
			}
			helper.assertTrue(placed == GeokinesisHandlers.WALL_WIDTH * GeokinesisHandlers.WALL_HEIGHT, "a 5x4 wall, got " + placed);
			for (int x = -2; x <= 2; x++) {
				for (int y = 0; y < GeokinesisHandlers.WALL_HEIGHT; y++) {
					GeoTerrain.restoreNow(level, centre.offset(x, y, 0)); // what its timer / a server stop does
				}
			}
			for (int x = -2; x <= 2; x++) {
				for (int y = 0; y < GeokinesisHandlers.WALL_HEIGHT; y++) {
					helper.assertTrue(level.getBlockState(centre.offset(x, y, 0)).isAir() && !GeoTerrain.isTemp(level, centre.offset(x, y, 0)),
							"every wall block is put back");
				}
			}
			helper.succeed();
		});
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void stoneWallNeedsBuildPermission(GameTestHelper helper) {
		ServerPlayer p = hero(helper);
		p.setGameMode(GameType.ADVENTURE);
		press(p, 5);
		BlockPos centre = BlockPos.containing(p.position().add(0, 0, 2.6));
		helper.assertTrue(helper.getLevel().getBlockState(centre).isAir(), "no wall in adventure mode");
		assertNear(helper, 100, res(p, "earth"), "nothing spent");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void earthArmorHalvesDamageSlowsAndDrains(GameTestHelper helper) {
		ServerPlayer p = hero(helper);
		double speed = p.getAttributeValue(Attributes.MOVEMENT_SPEED);
		shiftPress(p, 5);
		helper.assertTrue(GeokinesisHandlers.armorOn(p), "armour on");
		assertNear(helper, 0.5, GeokinesisHandlers.damageFactor(p, p.damageSources().generic()), "-50% damage");
		assertNear(helper, speed * 0.8, p.getAttributeValue(Attributes.MOVEMENT_SPEED), "-20% speed");
		GeokinesisHandlers.setEarth(p, 50);
		tick(p, 20);
		assertNear(helper, 46, res(p, "earth"), "4 Earth a second, no refill while it drains");
		shiftPress(p, 5);
		helper.assertFalse(GeokinesisHandlers.armorOn(p), "armour off");
		assertNear(helper, speed, p.getAttributeValue(Attributes.MOVEMENT_SPEED), "speed back");
		helper.succeed();
	}

	// ---------------- C ----------------

	@GameTest(template = EMPTY_STRUCTURE)
	public void tunnelBoresThreeByThreeAndKeepsTheBlocks(GameTestHelper helper) {
		ServerPlayer p = hero(helper);
		BlockPos feet = p.blockPosition();
		for (int s = -1; s <= 1; s++) {
			for (int h = 0; h <= 2; h++) {
				for (int d = 1; d <= 3; d++) {
					helper.getLevel().setBlock(feet.offset(s, h, d), Blocks.STONE.defaultBlockState(), 2);
				}
			}
		}
		press(p, 6); // v0.16.6: a tap starts it (it used to be held)
		helper.assertTrue(GeokinesisHandlers.tunnelling(p), "boring");
		GeokinesisHandlers.setEarth(p, 50);
		tick(p, 20);
		for (int s = -1; s <= 1; s++) {
			for (int h = 0; h <= 2; h++) {
				helper.assertTrue(helper.getLevel().getBlockState(feet.offset(s, h, 1)).isAir(), "the 3x3 face ahead is bored out");
			}
		}
		helper.assertTrue(p.getInventory().countItem(Items.COBBLESTONE) >= 9, "you keep the blocks, got "
				+ p.getInventory().countItem(Items.COBBLESTONE));
		assertNear(helper, 47, res(p, "earth"), "3 Earth a second");
		press(p, 6);
		helper.assertFalse(GeokinesisHandlers.tunnelling(p), "v0.16.6: a second tap stops it");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void seismicSenseIsClientOnly(GameTestHelper helper) {
		ServerPlayer p = hero(helper);
		Zombie z = zombie(helper, 7, 12);
		shiftPress(p, 6);
		assertNear(helper, 80, res(p, "earth"), "20 Earth");
		assertNear(helper, GeokinesisHandlers.SEISMIC_TICKS, res(p, "seismic"), "10 s");
		helper.assertTrue(GeokinesisHandlers.sensing(p) && GeokinesisHandlers.sensedBy(p, z), "the zombie is sensed");
		helper.assertFalse(z.hasEffect(MobEffects.GLOWING) || z.hasGlowingTag(), "never the server glowing flag");
		z.teleportTo(z.getX(), z.getY(), z.getZ() + 30);
		helper.assertFalse(GeokinesisHandlers.sensedBy(p, z), "nothing past 32 blocks");
		helper.succeed();
	}

	// ---------------- passives ----------------

	@GameTest(template = EMPTY_STRUCTURE)
	public void passivesKnockbackMiningAndFalls(GameTestHelper helper) {
		ServerPlayer p = hero(helper);
		p.setOnGround(true);
		tick(p, 1);
		assertNear(helper, 0.5, p.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE), "50% knockback resistance on the ground");
		p.setOnGround(false);
		tick(p, 1);
		assertNear(helper, 0.0, p.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE), "none in the air");
		assertNear(helper, 2.0, GeoBareHands.miningSpeed(Blocks.STONE.defaultBlockState(), 1.0f), "stone 2x");
		assertNear(helper, 2.0, GeoBareHands.miningSpeed(Blocks.DIRT.defaultBlockState(), 1.0f), "dirt 2x");
		assertNear(helper, 1.0, GeoBareHands.miningSpeed(Blocks.OAK_PLANKS.defaultBlockState(), 1.0f), "planks unchanged");
		helper.assertTrue(GeokinesisHandlers.damageFactor(p, p.damageSources().fall()) < 0, "no fall damage onto stone");
		BlockPos under = BlockPos.containing(p.getX(), p.getY() - 0.2, p.getZ());
		helper.getLevel().setBlock(under, Blocks.OAK_PLANKS.defaultBlockState(), 2);
		assertNear(helper, 1.0, GeokinesisHandlers.damageFactor(p, p.damageSources().fall()), "a normal fall onto planks");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void losingThePowerTearsEverythingDown(GameTestHelper helper) {
		ServerPlayer p = hero(helper);
		shiftPress(p, 5);
		press(p, 3);
		helper.assertTrue(GeokinesisHandlers.armorOn(p) && GeokinesisHandlers.surfing(p), "armour + surf on");
		double base = p.getAttributeBaseValue(Attributes.MOVEMENT_SPEED);
		ExperimentalPowers.forget(p, Powers.byKey(K));
		helper.assertFalse(GeokinesisHandlers.owns(p), "forgotten");
		assertNear(helper, base, p.getAttributeValue(Attributes.MOVEMENT_SPEED), "the armour's slow is gone");
		assertNear(helper, 0.0, p.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE), "the knockback passive is gone");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void resonanceOnStoneStillGrantsGeokinesis(GameTestHelper helper) {
		ServerPlayer p = helper.makeMockServerPlayerInLevel();
		p.setGameMode(GameType.SURVIVAL);
		BlockPos stone = helper.absolutePos(new BlockPos(1, 1, 1));
		helper.getLevel().setBlockAndUpdate(stone, Blocks.STONE.defaultBlockState());
		helper.getLevel().setBlockAndUpdate(stone.above(), Blocks.AIR.defaultBlockState());
		helper.getLevel().setBlockAndUpdate(stone.above(2), Blocks.AIR.defaultBlockState());
		p.moveTo(stone.getX() + 0.5, stone.getY() + 1.0, stone.getZ() + 0.5);
		Power geo = Powers.byKey(K);
		p.addEffect(new net.minecraft.world.effect.MobEffectInstance(com.projecthero.mod.hero.mutation.ModMobEffects.UNSTABLE_MUTATION, 1200,
				com.projecthero.mod.hero.mutation.ModSerums.amplifierFor(geo), false, true, true));
		for (int i = 0; i < 220 && !ExperimentalPowers.owns(p, geo); i++) {
			MutationManager.serverTick(p);
		}
		helper.assertTrue(ExperimentalPowers.owns(p, geo), "the serum + standing on natural stone grants Geokinesis");
		helper.succeed();
	}

}
