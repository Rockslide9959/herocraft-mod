package com.projecthero.mod.gametest;

import com.projecthero.mod.greenlantern.GreenLantern;
import com.projecthero.mod.greenlantern.GreenLanternAbilityManager;
import com.projecthero.mod.greenlantern.GreenLanternConfig;
import com.projecthero.mod.greenlantern.GreenLanternEnergy;
import com.projecthero.mod.greenlantern.GreenLanternSurvival;
import com.projecthero.mod.greenlantern.bind.GreenLanternBinding;
import com.projecthero.mod.greenlantern.block.GreenLanternSurvivalBlocks;
import com.projecthero.mod.greenlantern.block.HardLightScaffoldBlock;
import com.projecthero.mod.greenlantern.construct.ConstructType;
import com.projecthero.mod.greenlantern.construct.GreenLanternConstructs;
import com.projecthero.mod.greenlantern.data.GreenLanternState;
import com.projecthero.mod.greenlantern.entity.HardLightConstructEntity;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;

/**
 * v0.17.1 Green Lantern survival constructs ({@link GreenLanternSurvival}): the Hard-Light Chest keeps items while it
 * stands and spills every one of them when it ends (N or an empty ring), only its owner opens it; the Item Net reels a
 * dropped item in; the Tunneller mines a 3x3 bore into items and stops at bedrock; the Scaffold Tower stands, climbs and
 * lets go. Mock players are ticked by hand ({@link GreenLanternSurvival#tick}); rings run at half charge (no Willpower
 * Surge).
 */
public class GreenLanternSurvivalGameTests implements FabricGameTest {
	@SuppressWarnings("removal")
	private static ServerPlayer lantern(GameTestHelper helper, float pitch) {
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		player.setGameMode(GameType.SURVIVAL);
		for (BlockPos pos : BlockPos.betweenClosed(helper.absolutePos(new BlockPos(0, 1, 0)), helper.absolutePos(new BlockPos(7, 1, 7)))) {
			helper.getLevel().setBlock(pos, Blocks.STONE.defaultBlockState(), 2);
		}
		Vec3 at = helper.absoluteVec(new Vec3(1.5, 2.0, 1.5));
		player.moveTo(at.x, at.y, at.z, 0f, pitch);
		player.setYHeadRot(0f);
		GreenLantern.bond(player);
		GreenLanternState s = GreenLantern.state(player).copy();
		s.ringCharge = GreenLanternConfig.MAX_RING_CHARGE * 0.5f;
		GreenLantern.save(player, s);
		return player;
	}

	private static int count(GameTestHelper helper, BlockPos around, double r, net.minecraft.world.item.Item item) {
		int n = 0;
		for (ItemEntity e : helper.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(around).inflate(r),
				x -> x.isAlive() && x.getItem().is(item))) {
			n += e.getItem().getCount();
		}
		return n;
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void survivalConstructsAreInTheMenu(GameTestHelper helper) {
		for (ConstructType t : new ConstructType[] { ConstructType.HARD_LIGHT_CHEST, ConstructType.SCAFFOLD_TOWER,
				ConstructType.ITEM_NET, ConstructType.TUNNELLER }) {
			GreenLanternBinding.Entry e = GreenLanternBinding.forConstruct(t);
			helper.assertTrue(e != null && e.section() == GreenLanternBinding.Section.BUILDING, t + " is under Building & Utility");
			helper.assertTrue(e.atlas() == GreenLanternBinding.ATLAS_SURVIVAL && e.cell() >= 0 && e.cell() < 4, t + " has its icon");
			helper.assertFalse(e.hold(), t + " is a tap");
		}
		helper.succeed();
	}

	// ---------------------------------------------------------------- Hard-Light Chest

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 60)
	public void chestKeepsItemsAndSpillsThemOnDismiss(GameTestHelper helper) {
		ServerPlayer p = lantern(helper, 60f);
		float before = GreenLanternEnergy.get(p);
		GreenLanternConstructs.deploy(p, ConstructType.HARD_LIGHT_CHEST);
		GreenLanternSurvival.Chest chest = GreenLanternSurvival.chest(p.getUUID());
		helper.assertTrue(chest != null, "a chest stands");
		helper.assertTrue(helper.getLevel().getBlockState(chest.pos).is(GreenLanternSurvivalBlocks.HARD_LIGHT_CHEST), "of hard light");
		helper.assertTrue(Math.abs(before - GreenLanternEnergy.get(p) - GreenLanternConfig.CHEST_COST) < 0.01f, "it cost 40");
		helper.assertTrue(chest.items.getContainerSize() == 27, "27 slots");
		chest.items.setItem(0, new ItemStack(Items.DIAMOND, 5));
		chest.items.setItem(26, new ItemStack(Items.OAK_LOG, 12));
		// a second one is refused while it stands
		GreenLanternConstructs.deploy(p, ConstructType.HARD_LIGHT_CHEST);
		helper.assertTrue(GreenLanternSurvival.chest(p.getUUID()) == chest, "one chest at a time");
		helper.assertTrue(GreenLanternSurvival.open(p, chest), "its owner opens it");
		@SuppressWarnings("removal")
		ServerPlayer stranger = helper.makeMockServerPlayerInLevel();
		helper.assertFalse(GreenLanternSurvival.open(stranger, chest), "a stranger cannot");
		p.closeContainer();
		BlockPos at = chest.pos;
		helper.runAfterDelay(10, () -> {
			for (int i = 0; i < 10; i++) {
				GreenLanternSurvival.tick(p);
			}
			helper.assertTrue(chest.items.getItem(0).getCount() == 5, "it holds its items while it stands");
			helper.assertTrue(GreenLanternEnergy.get(p) < before - GreenLanternConfig.CHEST_COST, "upkeep drains");
			GreenLanternAbilityManager.clearConstructs(p); // N
			helper.assertTrue(GreenLanternSurvival.chest(p.getUUID()) == null, "N dismisses it");
			helper.assertTrue(helper.getLevel().getBlockState(at).isAir(), "the block goes");
			helper.assertTrue(count(helper, at, 2, Items.DIAMOND) == 5, "the diamonds are on the ground");
			helper.assertTrue(count(helper, at, 2, Items.OAK_LOG) == 12, "and the logs");
			helper.succeed();
		});
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 40)
	public void chestSpillsWhenTheRingRunsDry(GameTestHelper helper) {
		ServerPlayer p = lantern(helper, 60f);
		GreenLanternConstructs.deploy(p, ConstructType.HARD_LIGHT_CHEST);
		GreenLanternSurvival.Chest chest = GreenLanternSurvival.chest(p.getUUID());
		helper.assertTrue(chest != null, "a chest stands");
		chest.items.setItem(3, new ItemStack(Items.IRON_INGOT, 9));
		GreenLanternState s = GreenLantern.state(p).copy();
		s.ringCharge = GreenLanternConfig.EMERGENCY_RESERVE; // nothing spendable left
		GreenLantern.save(p, s);
		GreenLanternSurvival.tick(p);
		helper.assertTrue(GreenLanternSurvival.chest(p.getUUID()) == null, "an empty ring ends it");
		helper.assertTrue(count(helper, chest.pos, 2, Items.IRON_INGOT) == 9, "and nothing is lost");
		helper.succeed();
	}

	// ---------------------------------------------------------------- Item Net

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 100)
	public void itemNetReelsInADroppedItem(GameTestHelper helper) {
		ServerPlayer p = lantern(helper, 0f);
		p.getInventory().clearContent();
		Vec3 spot = helper.absoluteVec(new Vec3(6.5, 2.2, 6.5));
		ItemEntity item = new ItemEntity(helper.getLevel(), spot.x, spot.y, spot.z, new ItemStack(Items.EMERALD, 3));
		item.setNoPickUpDelay();
		helper.getLevel().addFreshEntity(item);
		GreenLanternConstructs.deploy(p, ConstructType.ITEM_NET);
		helper.assertTrue(GreenLanternSurvival.active(p.getUUID()).stream().anyMatch(e -> e.shape() == HardLightConstructEntity.Shape.ITEM_NET),
				"the net is cast");
		helper.assertTrue(GreenLantern.cooldownRemaining(p, GreenLanternSurvival.NET_COOLDOWN_ID) > 0, "and goes on cooldown");
		GreenLanternConstructs.deploy(p, ConstructType.ITEM_NET);
		helper.assertTrue(GreenLanternSurvival.active(p.getUUID()).size() == 1, "a second cast waits for the cooldown");
		helper.runAfterDelay(GreenLanternConfig.NET_TICKS - 5, () -> {
			helper.assertTrue(p.getInventory().countItem(Items.EMERALD) == 3,
					"the emeralds were reeled in, inventory has " + p.getInventory().countItem(Items.EMERALD));
			helper.succeed();
		});
	}

	// ---------------------------------------------------------------- Tunneller

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 120)
	public void tunnellerMinesAThreeByThreeBoreIntoItems(GameTestHelper helper) {
		ServerPlayer p = lantern(helper, 0f); // facing +Z (south)
		// a block of stone 4 deep in front, bedrock behind it to stop the bore inside the test area
		for (BlockPos pos : BlockPos.betweenClosed(helper.absolutePos(new BlockPos(0, 2, 3)), helper.absolutePos(new BlockPos(2, 4, 6)))) {
			helper.getLevel().setBlock(pos, Blocks.STONE.defaultBlockState(), 2);
		}
		helper.setBlock(new BlockPos(1, 3, 7), Blocks.BEDROCK);
		float before = GreenLanternEnergy.get(p);
		GreenLanternConstructs.deploy(p, ConstructType.TUNNELLER);
		helper.assertTrue(GreenLanternSurvival.active(p.getUUID()).stream().anyMatch(e -> e.shape() == HardLightConstructEntity.Shape.TUNNELLER),
				"the tunneller is shaped");
		helper.runAfterDelay(8 * GreenLanternConfig.TUNNEL_TICKS_PER_SLICE + 4, () -> {
			for (BlockPos pos : BlockPos.betweenClosed(new BlockPos(0, 2, 3), new BlockPos(2, 4, 6))) {
				helper.assertBlockPresent(Blocks.AIR, pos);
			}
			helper.assertBlockPresent(Blocks.BEDROCK, new BlockPos(1, 3, 7));
			helper.assertBlockPresent(Blocks.STONE, new BlockPos(1, 1, 4)); // the floor is left alone
			int cobble = count(helper, helper.absolutePos(new BlockPos(1, 3, 5)), 4, Items.COBBLESTONE);
			helper.assertTrue(cobble == 36, "36 blocks of stone dropped as cobblestone, got " + cobble);
			float spent = before - GreenLanternEnergy.get(p);
			float expected = GreenLanternConfig.TUNNEL_COST + 36 * GreenLanternConfig.TUNNEL_COST_PER_BLOCK;
			helper.assertTrue(Math.abs(spent - expected) < 0.05f, "10 + 1.5 a block, spent " + spent);
			helper.succeed();
		});
	}

	// ---------------------------------------------------------------- Scaffold Tower

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 60)
	public void scaffoldTowerStandsClimbsAndLetsGo(GameTestHelper helper) {
		ServerPlayer p = lantern(helper, 60f);
		// the gametest framework roofs every test area with barriers (a few blocks over this floor) -- a tower stops
		// under anything unbreakable, so open a shaft through the roof for the full 24 blocks
		for (BlockPos pos : BlockPos.betweenClosed(helper.absolutePos(new BlockPos(-1, 3, 0)), helper.absolutePos(new BlockPos(3, 28, 4)))) {
			if (!helper.getLevel().getBlockState(pos).isAir()) {
				helper.getLevel().setBlock(pos, Blocks.AIR.defaultBlockState(), 2);
			}
		}
		GreenLanternConstructs.deploy(p, ConstructType.SCAFFOLD_TOWER);
		GreenLanternSurvival.Tower tower = GreenLanternSurvival.tower(p.getUUID());
		helper.assertTrue(tower != null, "a tower is going up");
		for (int i = 0; i < 12; i++) {
			GreenLanternSurvival.tick(p);
		}
		helper.assertTrue(tower.columnHeight == GreenLanternConfig.SCAFFOLD_HEIGHT, "24 blocks tall, got " + tower.columnHeight);
		BlockPos base = tower.cells.get(0).pos();
		helper.assertTrue(base.getY() == helper.absolutePos(new BlockPos(0, 2, 0)).getY(), "it stands on the floor");
		for (int i = 0; i < tower.columnHeight; i++) {
			BlockState s = helper.getLevel().getBlockState(base.above(i));
			helper.assertTrue(s.is(GreenLanternSurvivalBlocks.HARD_LIGHT_SCAFFOLD) && !s.getValue(HardLightScaffoldBlock.PLATFORM),
					"column cell " + i);
			helper.assertTrue(s.is(BlockTags.CLIMBABLE), "climbable");
		}
		BlockPos top = base.above(tower.columnHeight - 1);
		int deck = 0;
		for (BlockPos pos : BlockPos.betweenClosed(top.offset(-1, 0, -1), top.offset(1, 0, 1))) {
			BlockState s = helper.getLevel().getBlockState(pos);
			if (s.is(GreenLanternSurvivalBlocks.HARD_LIGHT_SCAFFOLD) && s.getValue(HardLightScaffoldBlock.PLATFORM)) {
				deck++;
			}
		}
		helper.assertTrue(deck == 8, "a 3x3 deck round the top, got " + deck);
		// inside the column: climbable and open; standing above a cell: solid underfoot
		BlockPos mid = base.above(5);
		p.moveTo(mid.getX() + 0.5, mid.getY(), mid.getZ() + 0.5);
		helper.assertTrue(p.onClimbable(), "a Lantern inside it is on a climbable");
		BlockState cell = helper.getLevel().getBlockState(mid);
		helper.assertTrue(cell.getCollisionShape(helper.getLevel(), mid, CollisionContext.of(p)).isEmpty(), "open from inside");
		p.moveTo(mid.getX() + 0.5, mid.getY() + 1.0, mid.getZ() + 0.5);
		helper.assertFalse(cell.getCollisionShape(helper.getLevel(), mid, CollisionContext.of(p)).isEmpty(), "solid underfoot");
		// pressing it again lets it go
		GreenLanternConstructs.deploy(p, ConstructType.SCAFFOLD_TOWER);
		helper.assertTrue(GreenLanternSurvival.tower(p.getUUID()) == null, "a second press lets it go");
		helper.assertTrue(helper.getLevel().getBlockState(mid).isAir() && helper.getLevel().getBlockState(top).isAir(), "and the light is gone");
		helper.succeed();
	}
}
