package com.projecthero.mod.gametest;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.projecthero.mod.ProjectHeroMod;
import com.projecthero.mod.event.EventSavedData;
import com.projecthero.mod.event.EventState;
import com.projecthero.mod.event.EventTypes;
import com.projecthero.mod.galactus.Galactus;
import com.projecthero.mod.galactus.GalactusAnims;
import com.projecthero.mod.galactus.GalactusConfig;
import com.projecthero.mod.galactus.GalactusEvent;
import com.projecthero.mod.galactus.GalactusRewards;
import com.projecthero.mod.galactus.GalactusTerrain;
import com.projecthero.mod.galactus.block.GalactusBlocks;
import com.projecthero.mod.galactus.block.HeraldsBeaconBlock;
import com.projecthero.mod.galactus.entity.ElementConverterEntity;
import com.projecthero.mod.galactus.entity.GalactusEntity;
import com.projecthero.mod.galactus.entity.GalactusWeakPointEntity;
import com.projecthero.mod.galactus.entity.PowerPylonEntity;
import com.projecthero.mod.galactus.entity.SilverSurferEntity;
import com.projecthero.mod.galactus.item.GalactusItems;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * v0.16.1: Galactus: The Devourer. Like the Frost Giant tests, the timed event is never run end to end: each test drives
 * one piece directly ({@code instant}: no falls / arrivals; converters 3 blocks out, no guards; pylons 3 out; Galactus 6
 * blocks away) and every event is aborted / finished and removed before the test ends. One event per batch (only one may
 * run within 256 blocks). Monsters need a non-Peaceful world; mock players report creative, so they are switched to
 * Survival to count as fighters.
 */
public class GalactusGameTests implements FabricGameTest {

	private static GalactusEvent startPinned(GameTestHelper helper) {
		helper.getLevel().getServer().setDifficulty(Difficulty.HARD, true);
		BlockPos at = helper.absolutePos(new BlockPos(4, 2, 4));
		GalactusEvent e = Galactus.start(helper.getLevel(), at, null, Galactus.Source.COMMAND, x -> {
			x.instant = true;
			x.converterRingOverride = 3.0;
			x.pylonRingOverride = 3.0;
			x.galactusDistanceOverride = 6.0;
			x.noGuards = true;
		});
		helper.assertTrue(e != null, "Galactus starts");
		return e;
	}

	private static void end(ServerLevel level, GalactusEvent e) {
		if (e != null && e.state().active()) {
			e.abort(level);
		}
		if (e != null) {
			GalactusTerrain.get(level).revertAllNow(level, e.id());
			EventSavedData.get(level).remove(e.id());
		}
	}

	private static ServerPlayer survivor(GameTestHelper helper) {
		ServerPlayer p = helper.makeMockServerPlayerInLevel();
		p.setGameMode(GameType.SURVIVAL);
		// Resistance V: nothing the Herald or Galactus throws can down the only fighter mid-test (that would end it)
		p.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.DAMAGE_RESISTANCE, 2000, 4));
		return p;
	}

	/** A 9 x 9 patch of grass (with tufts) round the structure's centre on y = 1, a chest in one corner. */
	private static BlockPos buildMeadow(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		for (int x = 0; x < 9; x++) {
			for (int z = 0; z < 9; z++) {
				level.setBlockAndUpdate(helper.absolutePos(new BlockPos(x, 0, z)), Blocks.DIRT.defaultBlockState());
				level.setBlockAndUpdate(helper.absolutePos(new BlockPos(x, 1, z)), Blocks.GRASS_BLOCK.defaultBlockState());
				level.setBlockAndUpdate(helper.absolutePos(new BlockPos(x, 2, z)),
						(x + z) % 3 == 0 ? Blocks.SHORT_GRASS.defaultBlockState() : Blocks.AIR.defaultBlockState());
			}
		}
		BlockPos chest = helper.absolutePos(new BlockPos(8, 2, 8));
		level.setBlockAndUpdate(chest, Blocks.CHEST.defaultBlockState());
		return chest;
	}

	private static Map<BlockPos, BlockState> snapshot(GameTestHelper helper) {
		Map<BlockPos, BlockState> out = new HashMap<>();
		for (int x = -14; x <= 22; x++) {
			for (int y = -2; y <= 6; y++) {
				for (int z = -14; z <= 22; z++) {
					BlockPos p = helper.absolutePos(new BlockPos(x, y, z));
					out.put(p, helper.getLevel().getBlockState(p));
				}
			}
		}
		return out;
	}

	private static void assertSame(GameTestHelper helper, Map<BlockPos, BlockState> before, String what) {
		for (Map.Entry<BlockPos, BlockState> e : before.entrySet()) {
			BlockState now = helper.getLevel().getBlockState(e.getKey());
			if (!now.equals(e.getValue())) {
				helper.fail(what + ": " + e.getKey() + " was " + e.getValue() + ", now " + now);
			}
		}
	}

	// ---------------------------------------------------------------- the contract and the config

	@GameTest(template = EMPTY_STRUCTURE)
	public void galactusIsRegisteredWithItsContractAndConfig(GameTestHelper helper) {
		helper.assertTrue(EventTypes.isRegistered(GalactusEvent.TYPE_ID), "galactus is a registered event type");
		helper.assertTrue(EventTypes.create(GalactusEvent.TYPE_ID, UUID.randomUUID()) instanceof GalactusEvent, "loads back as a Galactus event");
		var recipe = helper.getLevel().getRecipeManager().byKey(ProjectHeroMod.id("heralds_beacon"));
		helper.assertTrue(recipe.isPresent(), "the Herald's Beacon recipe");
		boolean star = false;
		for (var ing : recipe.orElseThrow().value().getIngredients()) {
			star |= ing.test(new ItemStack(Items.NETHER_STAR));
		}
		helper.assertTrue(star, "it costs a nether star");
		for (GalactusAnims.Clip c : GalactusAnims.ALL) {
			helper.assertTrue(c.hit() < c.length(), c.id() + ": the hit lands inside the clip");
			helper.assertTrue(!c.loops() || c.hit() < 0, c.id() + ": loops never carry a hit");
		}
		for (String model : GalactusAnims.MODELS) {
			helper.assertFalse(GalactusAnims.of(model).isEmpty(), model + " has clips");
		}
		helper.assertTrue(GalactusConfig.converterCountFor(1) == 3 && GalactusConfig.converterCountFor(3) == 4
				&& GalactusConfig.converterCountFor(5) == 5 && GalactusConfig.converterCountFor(12) == 5, "3 converters, +1 per two extra fighters, at most 5");
		helper.assertTrue(GalactusConfig.arena().radius == 80.0 && GalactusConfig.trigger().enabled, "an 80-block arena, on by default");
		helper.succeed();
	}

	// ---------------------------------------------------------------- start / end

	@GameTest(template = EMPTY_STRUCTURE, batch = "galactus_start", timeoutTicks = 40)
	public void galactusStartsAndEnds(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		GalactusEvent e = startPinned(helper);
		SilverSurferEntity s;
		try {
			helper.assertTrue(e.state() == EventState.RUNNING && e.phase() == GalactusEvent.Phase.ARRIVAL, "running, arriving");
			BlockState beacon = level.getBlockState(e.center());
			helper.assertTrue(beacon.is(GalactusBlocks.HERALDS_BEACON) && beacon.getValue(HeraldsBeaconBlock.ACTIVE), "the Herald's Beacon is lit");
			helper.assertTrue(Galactus.start(level, helper.absolutePos(new BlockPos(5, 2, 5)), null, Galactus.Source.COMMAND) == null,
					"a second one nearby is refused");
			s = e.startHerald(level);
			helper.assertTrue(s != null && e.ownsEntity(s) && e.phase() == GalactusEvent.Phase.HERALD, "the Herald descends, owned by the event");
			helper.assertTrue(s.getMaxHealth() == (float) GalactusConfig.heraldHealthFor(e.fighterCount()),
					"Herald health for the fighters present (mock players from earlier batches may join the arena)");
		} finally {
			end(level, e);
		}
		BlockState after = level.getBlockState(e.center());
		helper.assertTrue(after.is(GalactusBlocks.HERALDS_BEACON) && !after.getValue(HeraldsBeaconBlock.ACTIVE), "abort: the beacon goes dormant");
		helper.assertTrue(!s.isAlive() || s.isRemoved(), "the Herald is gone");
		helper.assertTrue(GalactusEvent.find(level, e.id()) == null, "nothing of it remains");
		level.setBlockAndUpdate(e.center(), Blocks.AIR.defaultBlockState());
		helper.succeed();
	}

	// ---------------------------------------------------------------- the Herald turns

	// Every test here runs in ONE tick and ends its event before returning: batches can overlap, and a Galactus still
	// running from an earlier batch would refuse the next one (one per 256 blocks).
	@GameTest(template = EMPTY_STRUCTURE, batch = "galactus_surfer", timeoutTicks = 40)
	public void surferTurnsAllyAtTenPercentAndCannotBeKilledAfter(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		GalactusEvent e = startPinned(helper);
		try {
			ServerPlayer p = survivor(helper);
			e.addFighter(p.getUUID());
			SilverSurferEntity s = e.startHerald(level);
			helper.assertTrue(s != null && !s.isAlly(), "a hostile Herald");
			s.invulnerableTime = 0;
			s.hurt(level.damageSources().playerAttack(p), 100000f);
			helper.assertTrue(s.isAlive(), "he can't be killed in the duel");
			helper.assertTrue(Math.abs(s.getHealth() - s.turnHealth()) < 0.01, "he stops at 10%: " + s.getHealth() + " vs " + s.turnHealth());
			helper.assertTrue(s.checkTurn(level), "at 10% his AI turns him (the first check of every hostile tick)");
			helper.assertTrue(s.isTurning() && e.phase() == GalactusEvent.Phase.TURN, "turning, and the event follows: " + e.phase());
			s.becomeAlly(level);
			helper.assertTrue(s.isAlly() && e.phase() == GalactusEvent.Phase.WORLDSHIP, "an ally, and the Worldship comes");
			s.invulnerableTime = 0;
			helper.assertFalse(s.hurt(level.damageSources().playerAttack(p), 100000f), "protected: nothing hurts him now");
			helper.assertTrue(s.isAlive() && s.getHealth() == s.getMaxHealth(), "full health, alive");
			helper.assertTrue(s.isAlliedTo(p), "on the fighters' side");
		} finally {
			end(level, e);
		}
		level.setBlockAndUpdate(e.center(), Blocks.AIR.defaultBlockState());
		helper.succeed();
	}

	// ---------------------------------------------------------------- terrain

	@GameTest(template = EMPTY_STRUCTURE, batch = "galactus_terrain", timeoutTicks = 40)
	public void convertersRecordAndRevertTerrainExactlyAndNeverTouchBlockEntities(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos chest = buildMeadow(helper);
		GalactusEvent e = startPinned(helper);
		try {
			Map<BlockPos, BlockState> before = snapshot(helper);
			BlockPos c = helper.absolutePos(new BlockPos(2, 1, 2));
			ElementConverterEntity conv = e.spawnConverter(level, 0, c.getX() + 0.5, c.getZ() + 0.5);
			helper.assertTrue(conv != null && conv.landed() && !conv.isShielded(), "a converter, down, unguarded");
			int changed = e.debugConsume(level, conv, 6, 40);
			GalactusTerrain terrain = GalactusTerrain.get(level);
			helper.assertTrue(changed > 10 && terrain.count(e.id(), 0) == changed, "it ate the meadow (" + changed + " blocks), all recorded");
			BlockPos under = helper.absolutePos(new BlockPos(2, 1, 2));
			helper.assertTrue(level.getBlockState(under).is(GalactusBlocks.CONSUMED_GROUND), "grass becomes Consumed Ground");
			helper.assertTrue(terrain.originalAt(e.id(), under).is(Blocks.GRASS_BLOCK), "the original grass is recorded");
			helper.assertTrue(level.getBlockState(chest).is(Blocks.CHEST) && level.getBlockEntity(chest) != null, "the chest is untouched");
			for (int dx = -3; dx <= 3; dx++) {
				for (int dz = -3; dz <= 3; dz++) {
					if (dx * dx + dz * dz > 9) {
						continue; // the clearance is a 3-block circle round the block entity, not a square
					}
					for (int dy = -2; dy <= 1; dy++) {
						helper.assertFalse(terrain.tracks(e.id(), chest.offset(dx, dy, dz)), "nothing within 3 of the chest is consumed");
					}
				}
			}
			conv.kill();
			helper.assertTrue(e.partsEarned() == 1, "the converter drops a Nullifier part");
			terrain.tickRevert(level.getServer(), 100000);
			helper.assertTrue(terrain.count(e.id(), 0) == 0, "every block of that converter is put back");
			assertSame(helper, before, "after the converter fell");
		} finally {
			end(level, e);
		}
		level.setBlockAndUpdate(e.center(), Blocks.AIR.defaultBlockState());
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE, batch = "galactus_loss", timeoutTicks = 40)
	public void consumptionFillsAndTheLossRevertsEverything(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		buildMeadow(helper);
		GalactusEvent e = startPinned(helper);
		try {
			level.setBlockAndUpdate(e.center(), Blocks.AIR.defaultBlockState()); // a loss burns the beacon out
			Map<BlockPos, BlockState> before = snapshot(helper);
			// the hazard patch first, so the converter's bite can't take those cells
			e.transmute(level, Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(6, 2, 6))), 1, 400);
			e.startWorldship(level);
			List<ElementConverterEntity> live = e.liveConverters(level);
			helper.assertTrue(live.size() == GalactusConfig.converterCountFor(e.fighterCount()) && e.phase() == GalactusEvent.Phase.WORLDSHIP, "3 converters, +1 per two extra fighters, got " + live.size());
			double rate = e.consumptionRate(level);
			helper.assertTrue(Math.abs(rate - live.size() * GalactusConfig.consumption().perConverterPerMinute / 60.0) < 1e-6, "5%/min per converter, got " + rate);
			e.addConsumption(level, rate * 60);
			helper.assertTrue(Math.abs(e.consumption() - live.size() * 5.0) < 1e-6, "the bar fills (" + e.consumption() + ")");
			int changed = e.debugConsume(level, live.get(0), 5, 30);
			GalactusTerrain terrain = GalactusTerrain.get(level);
			helper.assertTrue(changed > 0 && terrain.count(e.id(), GalactusTerrain.GROUP_HAZARD) > 0, "land eaten and a hazard patch down");
			e.debugSetConsumption(level, 100.0);
			helper.assertTrue(e.phase() == GalactusEvent.Phase.CONSUMED, "100%: the world is consumed");
			e.debugFinishConsumed(level);
			helper.assertTrue(e.state() == EventState.FAILED, "a loss");
			terrain.tickRevert(level.getServer(), 100000);
			helper.assertTrue(terrain.count(e.id()) == 0, "nothing left in the ledger");
			for (ElementConverterEntity c : live) {
				helper.assertTrue(c.isRemoved() || !c.isAlive(), "the converters are gone");
			}
			assertSame(helper, before, "after the loss");
		} finally {
			end(level, e);
		}
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE, batch = "galactus_abort", timeoutTicks = 40)
	public void hazardBlocksAreCleanedOnAbort(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		buildMeadow(helper);
		GalactusEvent e = startPinned(helper);
		try {
			level.setBlockAndUpdate(e.center(), Blocks.AIR.defaultBlockState());
			Map<BlockPos, BlockState> before = snapshot(helper);
			e.startGalactus(level);
			e.transmute(level, Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(4, 2, 4))), 2, 400);
			GalactusTerrain terrain = GalactusTerrain.get(level);
			int hazard = terrain.count(e.id(), GalactusTerrain.GROUP_HAZARD);
			helper.assertTrue(hazard >= 9, "a patch of cosmic hazard (" + hazard + ")");
			helper.assertTrue(level.getBlockState(helper.absolutePos(new BlockPos(4, 1, 4))).is(GalactusBlocks.COSMIC_HAZARD), "under the centre");
			helper.assertTrue(e.zones().size() == 1, "and its damage zone");
			e.abort(level);
			terrain.tickRevert(level.getServer(), 100000);
			helper.assertTrue(terrain.count(e.id()) == 0 && e.zones().isEmpty(), "abort: all taken back");
			level.setBlockAndUpdate(e.center(), Blocks.AIR.defaultBlockState());
			assertSame(helper, before, "after the abort");
		} finally {
			end(level, e);
		}
		helper.succeed();
	}

	// ---------------------------------------------------------------- Galactus

	@GameTest(template = EMPTY_STRUCTURE, batch = "galactus_hold", timeoutTicks = 40)
	public void galactusHoldsAtOneHealthUntilTheNullifier(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		GalactusEvent e = startPinned(helper);
		try {
			ServerPlayer p = survivor(helper);
			e.addFighter(p.getUUID());
			GalactusEntity g = e.startGalactus(level);
			helper.assertTrue(g != null && e.phase() == GalactusEvent.Phase.GALACTUS, "Galactus stands");
			helper.assertTrue(g.getMaxHealth() == (float) GalactusConfig.galactusHealthFor(e.fighterCount()), "health for the fighters present");
			helper.assertTrue(e.livePylons(level).size() == 4, "four Power Pylons");
			g.noAutoMoves = true;
			for (int i = 0; i < 5; i++) {
				g.invulnerableTime = 0;
				g.hurt(level.damageSources().playerAttack(p), 1.0e6f);
			}
			helper.assertTrue(g.isAlive() && g.getHealth() == 1.0f, "he holds at 1, got " + g.getHealth());
			g.kill();
			helper.assertTrue(g.isAlive() && g.getHealth() == 1.0f, "even /kill can't finish him");
			helper.assertTrue(e.nullifierMoment(), "at 1 health the Nullifier is called for");
			helper.assertTrue(e.state() == EventState.RUNNING, "and the event goes on until it is used");
		} finally {
			end(level, e);
		}
		level.setBlockAndUpdate(e.center(), Blocks.AIR.defaultBlockState());
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE, batch = "galactus_weak", timeoutTicks = 40)
	public void weakPointsTakeFullDamageTheBodyReduced(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		GalactusEvent e = startPinned(helper);
		try {
			ServerPlayer p = survivor(helper);
			e.addFighter(p.getUUID());
			GalactusEntity g = e.startGalactus(level);
			g.noAutoMoves = true;
			g.keepWeakPoints(level); // what his every tick does
			GalactusWeakPointEntity w = g.weakPoint(GalactusWeakPointEntity.KIND_EMBLEM);
			helper.assertTrue(w != null, "the chest emblem is placed on him");
			helper.assertTrue(w.sealed(), "sealed while the pylons stand");
			float w0 = w.getHealth();
			helper.assertFalse(w.hurt(level.damageSources().playerAttack(p), 50f), "a sealed weak point takes nothing");
			helper.assertTrue(w.getHealth() == w0, "untouched");
			for (PowerPylonEntity pylon : e.livePylons(level)) {
				pylon.kill();
			}
			helper.assertTrue(e.pylonsStanding(level) == 0, "every pylon down");
			g.keepWeakPoints(level);
			helper.assertFalse(w.sealed(), "the weak points open with the last pylon");
			g.setHealth(g.getMaxHealth());
			float h0 = g.getHealth();
			g.invulnerableTime = 0;
			g.hurt(level.damageSources().playerAttack(p), 100f);
			float body = h0 - g.getHealth();
			helper.assertTrue(Math.abs(body - 100f * (float) GalactusConfig.galactus().bodyDamageMultiplier) < 0.01f, "the body takes a fifth: " + body);
			float h1 = g.getHealth();
			float wh = w.getHealth();
			w.invulnerableTime = 0;
			helper.assertTrue(w.hurt(level.damageSources().playerAttack(p), 100f), "an open weak point is hurt");
			helper.assertTrue(Math.abs(wh - w.getHealth() - 100f) < 0.01f, "it takes the full hit");
			helper.assertTrue(Math.abs(h1 - g.getHealth() - 100f) < 0.01f, "and so does he (pass-through): " + (h1 - g.getHealth()));
			helper.assertTrue(e.weakPointHealth(GalactusWeakPointEntity.KIND_EMBLEM) == w.getHealth(), "the event keeps its health (saved)");
			float h2 = g.getHealth();
			w.invulnerableTime = 0;
			w.hurt(level.damageSources().playerAttack(p), 1.0e6f);
			helper.assertTrue(e.weakPointBroken(GalactusWeakPointEntity.KIND_EMBLEM), "broken");
			helper.assertTrue(h2 - g.getHealth() > g.getMaxHealth() * GalactusConfig.weakPoints().breakDamageFraction - 1, "breaking it costs him more");
		} finally {
			end(level, e);
		}
		level.setBlockAndUpdate(e.center(), Blocks.AIR.defaultBlockState());
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE, batch = "galactus_win", timeoutTicks = 40)
	public void theNullifierWinsAndPaysFirstClearItemsOnce(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		GalactusEvent e = startPinned(helper);
		try {
			ServerPlayer p = survivor(helper);
			p.moveTo(Vec3.atBottomCenterOf(e.center()).add(2, 0, 2));
			e.addFighter(p.getUUID());
			int clears = GalactusRewards.clears(p);
			GalactusEntity g = e.startGalactus(level);
			g.noAutoMoves = true;
			helper.assertTrue(e.useNullifier(level, p), "the Nullifier is used on him");
			helper.assertTrue(e.phase() == GalactusEvent.Phase.RETREAT && g.move() == GalactusEntity.Move.RETREAT, "he retreats");
			e.debugFinishRetreat(level);
			helper.assertTrue(e.state() == EventState.COMPLETED, "a win, got " + e.state());
			helper.assertTrue(level.getBlockState(e.center()).is(Blocks.CHEST), "the beacon becomes the reward chest");
			helper.assertTrue(GalactusRewards.clears(p) == clears + 1, "the clear is recorded");
			if (clears == 0) {
				helper.assertTrue(p.getInventory().countItem(GalactusItems.SURFBOARD) == 1 && p.getInventory().countItem(GalactusItems.POWER_COSMIC) == 1,
						"first clear: the Board and the Power Cosmic");
			}
			helper.assertTrue(GalactusRewards.recordClear(p).isEmpty(), "a second clear pays no first-clear items");
			helper.assertTrue(p.getInventory().countItem(GalactusItems.ULTIMATE_NULLIFIER) == 0, "the event's items never outlive it");
		} finally {
			end(level, e);
		}
		level.setBlockAndUpdate(e.center(), Blocks.AIR.defaultBlockState());
		helper.succeed();
	}
}
