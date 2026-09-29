package com.projecthero.mod.gametest;

import java.util.List;

import com.projecthero.mod.symbiote.SymbioteHost;
import com.projecthero.mod.symbiote.block.SymbioteBlocks;
import com.projecthero.mod.symbiote.entity.SymbioteEntity;
import com.projecthero.mod.symbiote.entity.SymbioteEntityTypes;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Husk;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/**
 * v0.13.19: the free Symbiote as a living creature -- the Symbiote Meteorite releases it, it takes over
 * a mob it reaches, a lab Symbiote never leaves its cell, and pre-0.13.19 saves still load.
 *
 * <p>Husks rather than zombies: they do not burn in the test world's daylight (a burning mob would
 * also scare the Symbiote off). Every test lays its own stone floor, and removes any free Symbiote it
 * leaves behind so it cannot crawl into a neighbouring test and take that test's mobs.
 */
public class SymbioteCreatureGameTests implements FabricGameTest {

	private static void floor(GameTestHelper helper, int size) {
		for (int x = 0; x < size; x++) {
			for (int z = 0; z < size; z++) {
				helper.setBlock(x, 0, z, Blocks.STONE);
			}
		}
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void breakingTheMeteoriteReleasesAFreeSymbiote(GameTestHelper helper) {
		floor(helper, 5);
		BlockPos rel = new BlockPos(2, 1, 2);
		helper.setBlock(rel, SymbioteBlocks.SYMBIOTE_METEORITE);
		BlockPos abs = helper.absolutePos(rel);
		ServerLevel level = helper.getLevel();
		helper.assertTrue(SymbioteEntity.near(level, abs.getX() + 0.5, abs.getY() + 0.5, abs.getZ() + 0.5, 3.0).isEmpty(),
				"no Symbiote before the rock is broken");

		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		player.setGameMode(GameType.SURVIVAL);
		helper.assertTrue(player.gameMode.destroyBlock(abs), "the player breaks the meteorite");
		helper.assertBlockPresent(Blocks.AIR, rel);

		List<SymbioteEntity> released = SymbioteEntity.near(level, abs.getX() + 0.5, abs.getY() + 0.5, abs.getZ() + 0.5, 3.0);
		helper.assertTrue(released.size() == 1, "exactly one Symbiote crawls out, got " + released.size());
		SymbioteEntity blob = released.get(0);
		helper.assertFalse(blob.isConfined(), "a meteorite Symbiote is free to roam");
		blob.discard();
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 120)
	public void aFreeSymbioteTakesOverAHusk(GameTestHelper helper) {
		floor(helper, 6);
		Husk husk = helper.spawn(EntityType.HUSK, new BlockPos(3, 1, 2));
		helper.assertTrue(SymbioteEntity.isValidHost(husk), "a husk is a valid host");
		Vec3 at = helper.absoluteVec(new Vec3(2.5, 1.0, 2.5));
		SymbioteEntity blob = SymbioteEntity.spawn(helper.getLevel(), at.x, at.y, at.z);
		helper.assertTrue(blob != null, "the Symbiote spawns");

		helper.succeedWhen(() -> {
			helper.assertTrue(SymbioteHost.is(husk), "the husk becomes a Symbiote Host");
			helper.assertTrue(blob.isRemoved(), "and the free Symbiote is consumed by the takeover");
			helper.assertFalse(SymbioteEntity.isValidHost(husk), "an existing host is never taken twice");
		});
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 200)
	public void aConfinedLabSymbioteNeverLeavesItsCell(GameTestHelper helper) {
		floor(helper, 9);
		Vec3 home = helper.absoluteVec(new Vec3(4.5, 1.0, 4.5));
		SymbioteEntity blob = SymbioteEntity.spawnConfined(helper.getLevel(), home.x, home.y, home.z,
				SymbioteEntity.LAB_HOME_RADIUS);
		helper.assertTrue(blob != null && blob.isConfined(), "a lab Symbiote is confined");
		// bait just outside its cell: a free Symbiote would go for it, a confined one must not
		Husk husk = helper.spawn(EntityType.HUSK, new BlockPos(8, 1, 4));
		husk.setPersistenceRequired();
		// rooted to the spot, so it cannot stroll into the cell by itself and muddy the result
		husk.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED).setBaseValue(0.0);

		double[] worst = { 0.0 };
		int[] ticks = { 0 };
		helper.onEachTick(() -> {
			if (blob.isRemoved()) {
				return;
			}
			ticks[0]++;
			if (ticks[0] % 20 == 0) {
				blob.setDeltaMovement(0.9, 0.2, -0.6); // something shoves it hard toward the glass
			}
			double dx = blob.getX() - home.x;
			double dz = blob.getZ() - home.z;
			worst[0] = Math.max(worst[0], Math.sqrt(dx * dx + dz * dz));
		});
		helper.runAtTickTime(160, () -> {
			helper.assertTrue(ticks[0] > 100, "the watch actually ran (" + ticks[0] + " ticks)");
			helper.assertFalse(blob.isRemoved(), "it did not take a host from outside its cell");
			helper.assertFalse(SymbioteHost.is(husk), "the husk outside the cell is untouched");
			helper.assertTrue(worst[0] <= SymbioteEntity.LAB_HOME_RADIUS + 1.0e-6,
					"it never left its " + SymbioteEntity.LAB_HOME_RADIUS + "-block cell (worst " + worst[0] + ")");
			blob.discard();
			helper.succeed();
		});
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 40)
	public void aPreUpdateSaveStillLoads(GameTestHelper helper) {
		floor(helper, 7);
		// (a) an old meteor/host Symbiote: only HoverY was ever saved -> loads as a free Symbiote
		SymbioteEntity free = oldSave(helper, new Vec3(1.5, 1.2, 1.5));

		// (b) an old lab Symbiote above a lodestone pedestal inside tinted glass -> re-adopts the cell
		BlockPos pedestal = new BlockPos(4, 1, 4);
		for (int dx = -1; dx <= 1; dx++) {
			for (int dz = -1; dz <= 1; dz++) {
				for (int y = 1; y <= 4; y++) {
					if (dx != 0 || dz != 0 || y == 4) {
						helper.setBlock(pedestal.offset(dx, y - 1, dz), Blocks.TINTED_GLASS);
					}
				}
			}
		}
		helper.setBlock(pedestal, Blocks.LODESTONE);
		SymbioteEntity lab = oldSave(helper, new Vec3(4.5, 2.2, 4.5));

		helper.succeedWhen(() -> {
			helper.assertTrue(free.home() != null, "the old free Symbiote got a home on its first tick");
			helper.assertFalse(free.isConfined(), "an old Symbiote in the open stays free");
			helper.assertTrue(lab.isConfined(), "an old Symbiote in a lab cell re-adopts it as its confined home");
			free.discard();
			lab.discard();
		});
	}

	/** Build a SymbioteEntity from a pre-0.13.19 save: vanilla entity keys + only {@code HoverY}. */
	private static SymbioteEntity oldSave(GameTestHelper helper, Vec3 rel) {
		ServerLevel level = helper.getLevel();
		SymbioteEntity template = SymbioteEntityTypes.SYMBIOTE.create(level);
		Vec3 abs = helper.absoluteVec(rel);
		template.moveTo(abs.x, abs.y, abs.z, 0.0f, 0.0f);
		CompoundTag tag = template.saveWithoutId(new CompoundTag());
		for (String key : new String[] { "HomeX", "HomeY", "HomeZ", "Confined", "HomeRadius", "HuntDelay" }) {
			tag.remove(key);
		}
		tag.putDouble("HoverY", abs.y);
		tag.putBoolean("NoGravity", true); // the old constructor set this and it was saved
		SymbioteEntity loaded = SymbioteEntityTypes.SYMBIOTE.create(level);
		loaded.load(tag);
		loaded.setUUID(java.util.UUID.randomUUID());
		level.addFreshEntity(loaded);
		return loaded;
	}
}
