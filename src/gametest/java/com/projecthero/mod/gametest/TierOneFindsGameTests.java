package com.projecthero.mod.gametest;

import java.util.List;

import com.projecthero.mod.loot.HeroVault;
import com.projecthero.mod.loot.LootItems;
import com.projecthero.mod.loot.finds.FindPiece;
import com.projecthero.mod.loot.finds.FindStructure;
import com.projecthero.mod.loot.finds.HeroOutpostPiece;
import com.projecthero.mod.loot.finds.SupplyDropEntity;
import com.projecthero.mod.loot.finds.SupplyDrops;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.LecternBlockEntity;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;

/**
 * v0.17.5 Tier 1 Finds: each piece builds on a stone plinth (high above the test area, like the dig-site test) and holds
 * a Tier 1 Hero Vault where it says; the outpost has its field notes and its mobs; a Supply Drop crate falls, lands as
 * a vault on the ground and packs up again; the game rule switches the scheduled drops off.
 */
public class TierOneFindsGameTests implements FabricGameTest {
	/** A stone plinth with its top at {@code top}, then the piece built whole on it. */
	private static FindPiece buildOnPlinth(GameTestHelper helper, FindStructure.Kind kind, int lift) {
		ServerLevel level = helper.getLevel();
		BlockPos at = helper.absolutePos(new BlockPos(3, 1, 3)).offset(0, lift, 0);
		for (BlockPos q : BlockPos.betweenClosed(at.offset(-8, -9, -8), at.offset(8, 12, 8))) {
			level.setBlock(q, (q.getY() > at.getY() ? Blocks.AIR : q.getY() == at.getY() ? Blocks.GRASS_BLOCK : Blocks.STONE).defaultBlockState(), 2);
		}
		FindPiece piece = FindStructure.createPiece(kind, at.getX(), at.getY() + 1, at.getZ());
		piece.build(level, piece.getBoundingBox());
		return piece;
	}

	private static void assertVault(GameTestHelper helper, FindPiece piece, String name) {
		BlockPos v = piece.vaultPos();
		helper.assertTrue(helper.getLevel().getBlockState(v).is(LootItems.HERO_VAULT), name + ": a hero_vault at " + v
				+ " (found " + helper.getLevel().getBlockState(v) + ")");
		helper.assertTrue(HeroVault.tierAt(helper.getLevel(), v) == 1, name + ": the vault is tier 1");
	}

	private static void wipe(GameTestHelper helper, FindPiece piece) {
		ServerLevel level = helper.getLevel();
		AABB box = AABB.of(piece.getBoundingBox()).inflate(2);
		for (Mob m : level.getEntitiesOfClass(Mob.class, box)) {
			m.discard();
		}
		int x = piece.getBoundingBox().getCenter().getX(), z = piece.getBoundingBox().getCenter().getZ(), g = piece.groundY();
		for (BlockPos q : BlockPos.betweenClosed(x - 9, g - 9, z - 9, x + 9, g + 12, z + 9)) {
			level.setBlock(q, Blocks.AIR.defaultBlockState(), 2);
		}
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void crashedDroneHoldsAVault(GameTestHelper helper) {
		FindPiece piece = buildOnPlinth(helper, FindStructure.Kind.CRASHED_DRONE, 30);
		assertVault(helper, piece, "crashed drone");
		// the cargo bay in front of the vault is open
		BlockPos v = piece.vaultPos();
		Direction door = helper.getLevel().getBlockState(v).getValue(net.minecraft.world.level.block.HorizontalDirectionalBlock.FACING);
		helper.assertTrue(helper.getLevel().getBlockState(v.relative(door)).isAir(), "the vault's door opens onto air");
		wipe(helper, piece);
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void hiddenCacheHoldsABuriedVault(GameTestHelper helper) {
		FindPiece piece = buildOnPlinth(helper, FindStructure.Kind.HIDDEN_CACHE, 54);
		assertVault(helper, piece, "hidden cache");
		int depth = piece.groundY() - piece.vaultPos().getY();
		helper.assertTrue(depth >= 3 && depth <= 5, "the vault is buried 3-5 below the grass (got " + depth + ")");
		BlockPos marker = ((com.projecthero.mod.loot.finds.HiddenCachePiece) piece).markerPos();
		helper.assertFalse(helper.getLevel().getBlockState(marker).isAir(), "a marker stands over it at " + marker);
		helper.assertTrue(helper.getLevel().getBlockState(marker.below()).is(Blocks.COARSE_DIRT), "on churned earth");
		wipe(helper, piece);
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void heroOutpostHasVaultNotesAndMobs(GameTestHelper helper) {
		HeroOutpostPiece piece = (HeroOutpostPiece) buildOnPlinth(helper, FindStructure.Kind.HERO_OUTPOST, 78);
		assertVault(helper, piece, "hero outpost");
		ServerLevel level = helper.getLevel();
		helper.assertTrue(level.getBlockEntity(piece.lecternPos()) instanceof LecternBlockEntity l && l.hasBook(),
				"the lectern holds the field notes");
		List<Mob> mobs = level.getEntitiesOfClass(Mob.class, AABB.of(piece.getBoundingBox()));
		helper.assertTrue(mobs.size() >= 2 && mobs.size() <= 8 && mobs.size() >= piece.mobCount(),
				"2-4 lingering mobs (got " + mobs.size() + ", expected " + piece.mobCount() + ")");
		helper.assertTrue(mobs.stream().allMatch(Mob::isPersistenceRequired), "the mobs don't despawn");
		wipe(helper, piece);
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 240)
	public void supplyDropLandsAsAVault(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos column = helper.absolutePos(new BlockPos(4, 1, 4));
		int ground = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, column.getX(), column.getZ());
		SupplyDropEntity crate = SupplyDropEntity.create(level, column.getX() + 0.5, ground + 12, column.getZ() + 0.5, 1, Direction.NORTH);
		level.addFreshEntity(crate);
		BlockPos landing = new BlockPos(column.getX(), ground, column.getZ());
		helper.assertFalse(level.getBlockState(landing).is(LootItems.HERO_VAULT), "nothing there before it lands");
		helper.succeedWhen(() -> {
			helper.assertTrue(crate.landed(), "the crate has landed");
			helper.assertTrue(level.getBlockState(landing).is(LootItems.HERO_VAULT), "a vault on the ground at " + landing);
			helper.assertTrue(HeroVault.tierAt(level, landing) == 1, "tier 1");
			helper.assertTrue(crate.isAlive() && crate.isInvisible(), "the crate stays as the vault's timer, invisible");
			crate.packUp(level);
			helper.assertTrue(level.getBlockState(landing).isAir(), "packing up removes the vault");
		});
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void supplyDropCrateFallsInSteps(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos column = helper.absolutePos(new BlockPos(4, 1, 4));
		int ground = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, column.getX(), column.getZ());
		SupplyDropEntity crate = SupplyDropEntity.create(level, column.getX() + 0.5, ground + 40, column.getZ() + 0.5, 1, Direction.NORTH);
		level.addFreshEntity(crate);
		double y0 = crate.getY();
		helper.runAfterDelay(1, () -> {
			double step = y0 - crate.getY();
			helper.assertTrue(step > 0 && step <= SupplyDropEntity.FALL_SPEED * 2 + 1e-6, "falls a small step per tick (got " + step + ")");
			helper.assertFalse(crate.landed(), "still in the air");
			crate.discard();
			helper.succeed();
		});
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void gameRuleTurnsSupplyDropsOff(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		ServerPlayer p = helper.makeMockServerPlayerInLevel();
		p.setGameMode(GameType.SURVIVAL);
		boolean was = level.getGameRules().getBoolean(SupplyDrops.RULE);
		try {
			level.getGameRules().getRule(SupplyDrops.RULE).set(false, level.getServer());
			helper.assertFalse(SupplyDrops.enabled(level), "the rule reads off");
			helper.assertTrue(SupplyDrops.scheduledDrop(p).result() == SupplyDrops.Result.DISABLED, "a due drop is skipped while off");
			level.getGameRules().getRule(SupplyDrops.RULE).set(true, level.getServer());
			helper.assertTrue(SupplyDrops.enabled(level), "and on again");
		} finally {
			level.getGameRules().getRule(SupplyDrops.RULE).set(was, level.getServer());
		}
		helper.assertTrue(SupplyDrops.interval(level, level.getRandom()) >= 24000L, "at least a day between drops");
		helper.assertTrue("n".equals(SupplyDrops.compass(0, -10)) && "e".equals(SupplyDrops.compass(10, 0))
				&& "sw".equals(SupplyDrops.compass(-10, 10)), "compass words");
		helper.succeed();
	}
}
