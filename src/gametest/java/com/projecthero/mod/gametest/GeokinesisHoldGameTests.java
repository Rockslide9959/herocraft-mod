package com.projecthero.mod.gametest;

import java.util.List;

import com.projecthero.mod.hero.AbilityRouter;
import com.projecthero.mod.hero.AbilitySlot;
import com.projecthero.mod.hero.ExperimentalPowers;
import com.projecthero.mod.hero.HoldTap;
import com.projecthero.mod.hero.Power;
import com.projecthero.mod.hero.Powers;
import com.projecthero.mod.hero.power.p05.GeoBurrowEntity;
import com.projecthero.mod.hero.power.p05.GeoFormEntity;
import com.projecthero.mod.hero.power.p05.GeoRockEntity;
import com.projecthero.mod.hero.power.p05.GeoTerrain;
import com.projecthero.mod.hero.power.p05.GeokinesisHandlers;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/**
 * v0.16.6 Geokinesis hold moves (HoldTap: a key held 0.6 s fires its hold move, a quick tap the old move): Stone Barrage,
 * Quicksand, Burrow, Fissure, Stone Dome, Avalanche -- each one's server effect and numbers -- plus a tap still doing the
 * old move and the Tunnel's new tap-on / tap-off toggle. A hold is a key-down, a real 13-tick wait (HoldTap times the hold
 * by game time), {@link AbilityRouter#serverTick} and the key-up. Mock players may or may not be ticked by the server
 * meanwhile, so Earth / cooldown checks allow for a few ticks of refill / count-down.
 */
public class GeokinesisHoldGameTests implements FabricGameTest {
	private static final String K = GeokinesisHandlers.KEY;

	// ---------------- helpers ----------------

	/** A survival mock geokinetic at relative (7.5, 2, 1.5) facing +Z on 4 deep of stone (y -2..1), a clear room above. */
	private static ServerPlayer hero(GameTestHelper helper) {
		ServerPlayer p = helper.makeMockServerPlayerInLevel();
		p.setGameMode(GameType.SURVIVAL);
		// tests run at random far positions: force-load the room's chunks so the rocks / the burrow / the forms keep ticking
		// (the 13-tick hold gives the tickets time to make them entity-ticking before anything is summoned)
		BlockPos lo = helper.absolutePos(new BlockPos(-8, 0, -8));
		BlockPos hi = helper.absolutePos(new BlockPos(24, 0, 24));
		for (int cx = Math.min(lo.getX(), hi.getX()) >> 4; cx <= Math.max(lo.getX(), hi.getX()) >> 4; cx++) {
			for (int cz = Math.min(lo.getZ(), hi.getZ()) >> 4; cz <= Math.max(lo.getZ(), hi.getZ()) >> 4; cz++) {
				helper.getLevel().setChunkForced(cx, cz, true);
			}
		}
		for (BlockPos pos : BlockPos.betweenClosed(helper.absolutePos(new BlockPos(0, 2, 0)), helper.absolutePos(new BlockPos(14, 7, 15)))) {
			helper.getLevel().setBlock(pos, Blocks.AIR.defaultBlockState(), 2);
		}
		for (BlockPos pos : BlockPos.betweenClosed(helper.absolutePos(new BlockPos(0, -2, 0)), helper.absolutePos(new BlockPos(14, 1, 15)))) {
			helper.getLevel().setBlock(pos, Blocks.STONE.defaultBlockState(), 2);
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
		GeokinesisHandlers.earth(p); // seed the meter (full)
		return p;
	}

	private static float res(ServerPlayer p, String name) {
		return ExperimentalPowers.getResource(p, Powers.byKey(K), name);
	}

	/** A quick tap: key-down then key-up straight away. */
	private static void tap(ServerPlayer p, int slot) {
		AbilityRouter.dispatchExperimental(p, AbilitySlot.byNumber(slot), true);
		AbilityRouter.serverTick(p);
		AbilityRouter.dispatchExperimental(p, AbilitySlot.byNumber(slot), false);
	}

	/** Holds the key past {@link HoldTap#HOLD_TICKS} (real ticks), lets go, then runs {@code then}. */
	private static void hold(GameTestHelper helper, ServerPlayer p, int slot, Runnable then) {
		AbilityRouter.dispatchExperimental(p, AbilitySlot.byNumber(slot), true);
		helper.runAfterDelay(HoldTap.HOLD_TICKS + 1, () -> {
			AbilityRouter.serverTick(p);
			AbilityRouter.dispatchExperimental(p, AbilitySlot.byNumber(slot), false);
			then.run();
		});
	}

	/** {@code cost} came off a full meter (allowing a few ticks of refill since). */
	private static void spent(GameTestHelper helper, ServerPlayer p, float cost) {
		float e = res(p, "earth");
		helper.assertTrue(e <= 100 - cost + 14 * GeokinesisHandlers.REGEN_NATURAL + 0.05f && e >= 100 - cost - 0.05f,
				"spent " + cost + " Earth, meter at " + e);
	}

	/** The hold move's own cooldown is running (allowing a few ticks of count-down). */
	private static void cooling(GameTestHelper helper, ServerPlayer p, String timer, int ticks) {
		float t = res(p, timer);
		helper.assertTrue(t <= ticks && t >= ticks - 16, timer + " should be ~" + ticks + ", got " + t);
	}

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

	private static <T extends net.minecraft.world.entity.Entity> List<T> around(GameTestHelper helper, Class<T> type, ServerPlayer p,
			double r) {
		return helper.getLevel().getEntitiesOfClass(type, p.getBoundingBox().inflate(r));
	}

	// ---------------- the framework hook-up / taps ----------------

	@GameTest(template = EMPTY_STRUCTURE)
	public void everyKeyHasAHoldMove(GameTestHelper helper) {
		for (String id : new String[] { "rock_shot", "earth_grip", "rock_surf", "earthquake", "stone_wall", "tunnel" }) {
			helper.assertTrue(HoldTap.hasHold(K, id), id + " is tap-vs-hold");
		}
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void aTapStillFiresRockShot(GameTestHelper helper) {
		ServerPlayer p = hero(helper);
		AbilityRouter.dispatchExperimental(p, AbilitySlot.byNumber(1), true);
		helper.assertTrue(around(helper, GeoRockEntity.class, p, 6).isEmpty(), "nothing on the key-down (tap vs hold not known yet)");
		AbilityRouter.dispatchExperimental(p, AbilitySlot.byNumber(1), false);
		var rocks = around(helper, GeoRockEntity.class, p, 6);
		helper.assertTrue(rocks.size() == 1 && rocks.get(0).mode() == GeoRockEntity.MODE_SHOT
				&& rocks.get(0).rising() == GeoRockEntity.RISE_TICKS, "the quick release fires the old Rock Shot");
		helper.assertTrue(Math.abs(res(p, "earth") - 92) < 0.05f, "8 Earth, as before");
		helper.assertTrue(res(p, "barrage_cd") < 0.5f, "no Barrage cooldown from a tap");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void tunnelIsATapToggleNow(GameTestHelper helper) {
		ServerPlayer p = hero(helper);
		tap(p, 6);
		helper.assertTrue(GeokinesisHandlers.tunnelling(p), "a tap starts the Tunnel");
		for (int i = 0; i < 10; i++) {
			ExperimentalPowers.serverTick(p);
		}
		helper.assertTrue(GeokinesisHandlers.tunnelling(p), "it keeps boring with the key up");
		tap(p, 6);
		helper.assertFalse(GeokinesisHandlers.tunnelling(p), "a second tap stops it");
		tap(p, 6);
		helper.assertTrue(GeokinesisHandlers.tunnelling(p), "on again");
		GeokinesisHandlers.setEarth(p, 0.1f);
		ExperimentalPowers.serverTick(p);
		ExperimentalPowers.serverTick(p);
		helper.assertFalse(GeokinesisHandlers.tunnelling(p), "it stops when the Earth runs out");
		helper.succeed();
	}

	// ---------------- hold R: Stone Barrage ----------------

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 140, batch = "geo_hold_barrage") // alone: neighbours' quakes would hit the dummy
	public void holdRStoneBarrageFiresFiveStonesFor8Each(GameTestHelper helper) {
		ServerPlayer p = hero(helper);
		IronGolem g = dummy(helper, 7, 8);
		hold(helper, p, 1, () -> {
			var stones = around(helper, GeoRockEntity.class, p, 4);
			helper.assertTrue(stones.size() == GeokinesisHandlers.BARRAGE_STONES, "five stones, got " + stones.size());
			helper.assertTrue(stones.stream().allMatch(GeoRockEntity::orbiting), "circling you first");
			helper.assertTrue(stones.stream().allMatch(s -> Math.abs(s.damage() - GeokinesisHandlers.BARRAGE_DAMAGE) < 0.01f), "8 each");
			spent(helper, p, GeokinesisHandlers.COST_BARRAGE);
			cooling(helper, p, "barrage_cd", GeokinesisHandlers.BARRAGE_CD);
			// held again while it cools: nothing
			hold(helper, p, 1, () -> {
				long mine = around(helper, GeoRockEntity.class, p, 30).stream().filter(r -> r.getOwner() == p).count();
				helper.assertTrue(mine <= GeokinesisHandlers.BARRAGE_STONES, "no second barrage on cooldown, " + mine + " stones");
				helper.succeedWhen(() -> helper.assertTrue(Math.abs(g.getHealth() - (100 - 5 * GeokinesisHandlers.BARRAGE_DAMAGE)) < 0.05f,
						"all five land: 40, golem at " + g.getHealth()));
			});
		});
	}

	// ---------------- hold G: Quicksand ----------------

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 160, batch = "geo_hold_quicksand")
	public void holdGQuicksandSinksTheTargetAndHardensBack(GameTestHelper helper) {
		ServerPlayer p = hero(helper);
		Zombie z = zombie(helper, 7, 6);
		BlockPos under = z.blockPosition().below();
		hold(helper, p, 2, () -> {
			var level = helper.getLevel();
			helper.assertTrue(level.getBlockState(under).isAir() && GeoTerrain.isTemp(level, under), "the ground under it gives way");
			helper.assertTrue(around(helper, GeoFormEntity.class, p, 12).stream().anyMatch(f -> f.kind() == GeoFormEntity.QUICKSAND),
					"a churning sand pool");
			BlockPos me = p.blockPosition().below();
			helper.assertTrue(level.getBlockState(me).is(Blocks.STONE), "never under you");
			spent(helper, p, GeokinesisHandlers.COST_QUICKSAND);
			cooling(helper, p, "quicksand_cd", GeokinesisHandlers.QUICKSAND_CD);
			helper.runAfterDelay(20, () -> {
				helper.assertTrue(z.getY() < under.getY() + 0.5, "sunk waist-deep, at y " + z.getY() + " (ground top " + (under.getY() + 1) + ")");
				helper.assertTrue(z.hasEffect(MobEffects.MOVEMENT_SLOWDOWN), "slowed in it");
				helper.runAfterDelay(GeokinesisHandlers.QUICKSAND_TICKS, () -> {
					helper.assertTrue(level.getBlockState(under).is(Blocks.STONE) && !GeoTerrain.isTemp(level, under), "it hardens back");
					helper.assertTrue(z.getY() >= under.getY() + 0.99, "the zombie is lifted out on top, at " + z.getY());
					helper.succeed();
				});
			});
		});
	}

	// ---------------- hold X: Burrow ----------------

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 120, batch = "geo_hold_burrow")
	public void holdXBurrowTravelsUndergroundStopsAtBedrockAndBursts(GameTestHelper helper) {
		ServerPlayer p = hero(helper);
		// a band of bedrock across the way at z = 10, at body height under the ground
		for (int x = 0; x <= 14; x++) {
			for (int y = -1; y <= 0; y++) {
				helper.getLevel().setBlock(helper.absolutePos(new BlockPos(x, y, 10)), Blocks.BEDROCK.defaultBlockState(), 2);
			}
		}
		IronGolem g = dummy(helper, 7, 11);
		double startZ = p.getZ();
		double surface = p.getY();
		hold(helper, p, 3, () -> {
			helper.assertTrue(p.getVehicle() instanceof GeoBurrowEntity, "riding the burrow");
			helper.assertTrue(GeokinesisHandlers.burrowing(p), "burrowing");
			spent(helper, p, GeokinesisHandlers.COST_BURROW);
			cooling(helper, p, "burrow_cd", GeokinesisHandlers.BURROW_CD);
			helper.runAfterDelay(GeoBurrowEntity.SINK_TICKS + 4, () -> {
				helper.assertTrue(p.getY() < surface - 2.0, "under the ground, at y " + p.getY() + " (surface " + surface + ")");
				helper.assertFalse(GeokinesisHandlers.damageFactor(p, p.damageSources().inWall()) > 0, "no suffocating down there");
				helper.succeedWhen(() -> {
					helper.assertFalse(p.isPassenger(), "burst out");
					double rel = p.getZ() - helper.absoluteVec(Vec3.ZERO).z;
					helper.assertTrue(rel < 10.0 && p.getZ() - startZ > 5.0, "stopped short of the bedrock, at z " + rel);
					helper.assertTrue(p.getY() >= surface - 0.05, "back on the surface, at y " + p.getY());
					helper.assertTrue(Math.abs(g.getHealth() - (100 - GeokinesisHandlers.BURST_DAMAGE)) < 0.05f,
							"the burst hits the golem for 10, at " + g.getHealth());
				});
			});
		});
	}

	// ---------------- hold Z: Fissure ----------------

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 200, batch = "geo_hold_fissure")
	public void holdZFissureDropsMobsIn16AndCloses(GameTestHelper helper) {
		ServerPlayer p = hero(helper);
		IronGolem g = dummy(helper, 7, 8);
		BlockPos line = helper.absolutePos(new BlockPos(7, 1, 5));
		hold(helper, p, 4, () -> {
			spent(helper, p, GeokinesisHandlers.COST_FISSURE);
			cooling(helper, p, "fissure_cd", GeokinesisHandlers.FISSURE_CD);
			helper.assertTrue(around(helper, GeoFormEntity.class, p, 6).stream().anyMatch(f -> f.kind() == GeoFormEntity.FISSURE), "the crack's lips");
			helper.runAfterDelay(20, () -> {
				var level = helper.getLevel();
				for (int d = 0; d < GeokinesisHandlers.FISSURE_DEPTH; d++) {
					helper.assertTrue(level.getBlockState(line.below(d)).isAir() && GeoTerrain.isTemp(level, line.below(d)),
							"the crack is open 3 deep along your look");
				}
				helper.assertTrue(level.getBlockState(p.blockPosition().below()).is(Blocks.STONE), "never under you");
				helper.assertTrue(Math.abs(g.getHealth() - (100 - GeokinesisHandlers.FISSURE_DAMAGE)) < 0.05f, "16 damage, golem at " + g.getHealth());
				double floor = helper.absoluteVec(new Vec3(0, 2 - GeokinesisHandlers.FISSURE_DEPTH, 0)).y;
				helper.assertTrue(g.getY() < floor + 0.5, "it dropped 3 blocks in, at " + g.getY());
				helper.runAfterDelay(GeokinesisHandlers.FISSURE_TICKS, () -> {
					for (int d = 0; d < GeokinesisHandlers.FISSURE_DEPTH; d++) {
						helper.assertTrue(level.getBlockState(line.below(d)).is(Blocks.STONE), "the crack closes, the ground put back");
					}
					helper.assertTrue(g.getY() >= floor + GeokinesisHandlers.FISSURE_DEPTH - 0.05, "pushed back up onto the surface, at " + g.getY());
					helper.succeed();
				});
			});
		});
	}

	// ---------------- hold V: Stone Dome ----------------

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 60, batch = "geo_hold_dome")
	public void holdVStoneDomeClosesRoundYouAndShovesMobsOut(GameTestHelper helper) {
		ServerPlayer p = hero(helper);
		Vec3 mid = helper.absoluteVec(new Vec3(7.5, 2.0, 7.5));
		p.moveTo(mid.x, mid.y, mid.z, 0.0f, 0.0f);
		Zombie z = zombie(helper, 7, 9);
		hold(helper, p, 5, () -> {
			spent(helper, p, GeokinesisHandlers.COST_DOME);
			cooling(helper, p, "dome_cd", GeokinesisHandlers.DOME_CD);
			double d = Math.sqrt(z.distanceToSqr(mid.x, z.getY(), mid.z));
			helper.assertTrue(d > GeokinesisHandlers.DOME_RADIUS + 0.5, "the zombie is shoved out, now " + d + " away");
			helper.runAfterDelay(12, () -> {
				var level = helper.getLevel();
				BlockPos centre = BlockPos.containing(mid);
				var rings = GeokinesisHandlers.domeRings(centre);
				int placed = 0;
				int total = 0;
				for (var ring : rings) {
					for (BlockPos c : ring) {
						total++;
						if (level.getBlockState(c).is(Blocks.STONE) && GeoTerrain.isTemp(level, c)) {
							placed++;
						}
					}
				}
				helper.assertTrue(placed == total, "the whole dome is up: " + placed + " of " + total);
				helper.assertTrue(level.getBlockState(centre.above(GeokinesisHandlers.DOME_RADIUS)).is(Blocks.STONE), "closed overhead");
				helper.assertTrue(level.getBlockState(centre.above()).isAir(), "hollow inside");
				for (var ring : rings) {
					for (BlockPos c : ring) {
						GeoTerrain.restoreNow(level, c); // what its 8 s timer does
					}
				}
				for (var ring : rings) {
					for (BlockPos c : ring) {
						helper.assertTrue(level.getBlockState(c).isAir() && !GeoTerrain.isTemp(level, c), "every dome block is put back");
					}
				}
				helper.succeed();
			});
		});
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 40)
	public void holdVStoneDomeNeedsBuildPermission(GameTestHelper helper) {
		ServerPlayer p = hero(helper);
		p.setGameMode(GameType.ADVENTURE);
		hold(helper, p, 5, () -> {
			helper.assertTrue(res(p, "earth") >= 99.9f, "nothing spent in adventure mode");
			helper.assertTrue(res(p, "dome_cd") < 0.5f, "no cooldown either");
			helper.succeed();
		});
	}

	// ---------------- hold C: Avalanche ----------------

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 140, batch = "geo_hold_avalanche")
	public void holdCAvalancheRainsRealRocksFor6Each(GameTestHelper helper) {
		ServerPlayer p = hero(helper);
		IronGolem g = dummy(helper, 7, 10);
		hold(helper, p, 6, () -> {
			helper.assertFalse(GeokinesisHandlers.tunnelling(p), "holding C does not start the Tunnel");
			spent(helper, p, GeokinesisHandlers.COST_AVALANCHE);
			cooling(helper, p, "avalanche_cd", GeokinesisHandlers.AVALANCHE_CD);
			helper.assertTrue(around(helper, GeoFormEntity.class, p, 16).stream().anyMatch(f -> f.kind() == GeoFormEntity.AVALANCHE),
					"the rubble heap at the target");
			Vec3 c = GeokinesisHandlers.avalancheCentre(p);
			helper.assertTrue(c.distanceTo(g.position()) < 1.5, "it comes down where you aim, " + c + " vs " + g.position());
			helper.runAfterDelay(8, () -> {
				var rocks = helper.getLevel().getEntitiesOfClass(GeoRockEntity.class, g.getBoundingBox().inflate(6, 16, 6), GeoRockEntity::falling);
				helper.assertTrue(!rocks.isEmpty(), "real rocks falling out of the sky");
				helper.assertTrue(rocks.stream().allMatch(r -> Math.abs(r.damage() - GeokinesisHandlers.AVALANCHE_DAMAGE) < 0.01f), "6 each");
				helper.succeedWhen(() -> {
					float lost = 100 - g.getHealth();
					helper.assertTrue(lost >= 2 * GeokinesisHandlers.AVALANCHE_DAMAGE - 0.05f && Math.abs(lost % 6) < 0.05f,
							"rocks land on the golem, 6 a time: lost " + lost);
				});
			});
		});
	}
}
