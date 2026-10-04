package com.projecthero.mod.gametest;

import com.projecthero.mod.ironman.IronManBlocks;
import com.projecthero.mod.ironman.TonyStark;
import com.projecthero.mod.ironman.fabricator.FabricatorRecipes;
import com.projecthero.mod.ironman.fabricator.IronManSuitPlatformBlock;
import com.projecthero.mod.ironman.fabricator.StarkFabricatorBlock;
import com.projecthero.mod.ironman.fabricator.StarkFabricatorBlockEntity;
import com.projecthero.mod.ironman.item.IronManItems;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * v0.14.21: the remodelled Iron Man blocks -- both face the player who placed them, their hit boxes follow the model
 * (and turn with it), and the Fabricator flags {@code working} while it fabricates so the client rig can animate.
 */
public class IronManBlocksV01421GameTests implements FabricGameTest {

	private static ServerPlayer player(GameTestHelper helper, float yaw) {
		ServerPlayer p = helper.makeMockServerPlayerInLevel();
		p.setGameMode(GameType.SURVIVAL);
		p.setYRot(yaw);
		p.yRotO = yaw;
		return p;
	}

	private static BlockState placed(GameTestHelper helper, Block block, float yaw) {
		BlockPos pos = helper.absolutePos(new BlockPos(1, 2, 1));
		BlockPlaceContext ctx = new BlockPlaceContext(helper.getLevel(), player(helper, yaw), InteractionHand.MAIN_HAND,
				new ItemStack(block.asItem()), new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false));
		return block.getStateForPlacement(ctx);
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void ironManBlocksFaceThePlacer(GameTestHelper helper) {
		// yaw 0 looks south, 90 west, 180 north, 270 east -- the front must face back at the player
		float[] yaws = {0f, 90f, 180f, 270f};
		Direction[] expected = {Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST};
		for (int i = 0; i < yaws.length; i++) {
			BlockState fab = placed(helper, IronManBlocks.STARK_FABRICATOR, yaws[i]);
			helper.assertTrue(fab.getValue(StarkFabricatorBlock.FACING) == expected[i],
					"fabricator placed at yaw " + yaws[i] + " should face " + expected[i] + ", got " + fab.getValue(StarkFabricatorBlock.FACING));
			helper.assertFalse(fab.getValue(StarkFabricatorBlock.WORKING), "a fresh fabricator is idle");
			BlockState plat = placed(helper, IronManBlocks.IRON_MAN_SUIT_PLATFORM, yaws[i]);
			helper.assertTrue(plat.getValue(IronManSuitPlatformBlock.FACING) == expected[i],
					"suit platform placed at yaw " + yaws[i] + " should face " + expected[i]);
		}
		helper.succeed();
	}

	private static boolean contains(VoxelShape shape, double x, double y, double z) {
		Vec3 p = new Vec3(x / 16.0, y / 16.0, z / 16.0);
		for (AABB box : shape.toAabbs()) {
			if (box.contains(p)) {
				return true;
			}
		}
		return false;
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void ironManBlockShapesFollowTheModel(GameTestHelper helper) {
		BlockPos rel = new BlockPos(1, 2, 1);
		BlockPos abs = helper.absolutePos(rel);
		// Suit Platform: floor plate everywhere, gantry only at the back (south when facing north), nothing in front
		for (Direction facing : Direction.Plane.HORIZONTAL) {
			helper.setBlock(rel, IronManBlocks.IRON_MAN_SUIT_PLATFORM.defaultBlockState().setValue(IronManSuitPlatformBlock.FACING, facing));
			VoxelShape s = helper.getLevel().getBlockState(abs).getShape(helper.getLevel(), abs, CollisionContext.empty());
			Vec3 back = new Vec3(8, 10, 8).add(Vec3.atLowerCornerOf(facing.getOpposite().getNormal()).scale(6.0));
			Vec3 front = new Vec3(8, 10, 8).add(Vec3.atLowerCornerOf(facing.getNormal()).scale(6.0));
			helper.assertTrue(contains(s, 8, 1, 8), "platform floor plate must be solid (" + facing + ")");
			helper.assertTrue(contains(s, back.x, back.y, back.z), "platform gantry must be at the back (" + facing + ")");
			helper.assertFalse(contains(s, front.x, front.y, front.z), "platform front must be open (" + facing + ")");
			helper.assertFalse(contains(s, 8, 10, 8), "the suit's spot must be open (" + facing + ")");
		}
		// Fabricator: bench up to the worktop; the gantry at the back; open air over the front of the work plate
		for (Direction facing : Direction.Plane.HORIZONTAL) {
			helper.setBlock(rel, IronManBlocks.STARK_FABRICATOR.defaultBlockState().setValue(StarkFabricatorBlock.FACING, facing));
			VoxelShape s = helper.getLevel().getBlockState(abs).getShape(helper.getLevel(), abs, CollisionContext.empty());
			Vec3 back = new Vec3(8, 14, 8).add(Vec3.atLowerCornerOf(facing.getOpposite().getNormal()).scale(5.5));
			Vec3 front = new Vec3(8, 14, 8).add(Vec3.atLowerCornerOf(facing.getNormal()).scale(6.0));
			helper.assertTrue(contains(s, 8, 5, 8), "fabricator bench must be solid (" + facing + ")");
			helper.assertTrue(contains(s, back.x, back.y, back.z), "fabricator gantry must be at the back (" + facing + ")");
			helper.assertFalse(contains(s, front.x, front.y, front.z), "space over the work plate must be open (" + facing + ")");
		}
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void fabricatorFlagsWorkingWhileFabricating(GameTestHelper helper) {
		BlockPos rel = new BlockPos(2, 2, 2);
		helper.setBlock(rel, IronManBlocks.STARK_FABRICATOR);
		BlockPos abs = helper.absolutePos(rel);
		ServerPlayer tony = player(helper, 0f);
		tony.moveTo(abs.getX() + 0.5, abs.getY(), abs.getZ() - 1.5);
		TonyStark.grant(tony);
		var be = (StarkFabricatorBlockEntity) helper.getBlockEntity(rel);
		be.addEnergy(FabricatorRecipes.MAX_ENERGY);
		be.setItem(0, new ItemStack(Items.GOLD_INGOT, 3));
		be.setItem(1, new ItemStack(Items.IRON_INGOT, 3));
		be.setItem(2, new ItemStack(IronManItems.METAL_PLATING, 1));
		StarkFabricatorBlockEntity.serverTick(helper.getLevel(), abs, be.getBlockState(), be);
		helper.assertTrue(helper.getLevel().getBlockState(abs).getValue(StarkFabricatorBlock.WORKING),
				"fabricating (alloy recipe in the tray, Tony Stark at the bench) must set working=true");
		helper.assertTrue(helper.getLevel().getBlockState(abs).getLightEmission() > 7, "a working fabricator glows brighter");
		for (int i = 0; i < 3; i++) {
			be.setItem(i, ItemStack.EMPTY);
		}
		for (int t = 0; t < StarkFabricatorBlockEntity.WORKING_LINGER_TICKS - 1; t++) {
			StarkFabricatorBlockEntity.serverTick(helper.getLevel(), abs, helper.getLevel().getBlockState(abs), be);
		}
		helper.assertTrue(helper.getLevel().getBlockState(abs).getValue(StarkFabricatorBlock.WORKING),
				"working must linger briefly so queued pieces do not flicker the block");
		StarkFabricatorBlockEntity.serverTick(helper.getLevel(), abs, helper.getLevel().getBlockState(abs), be);
		helper.assertFalse(helper.getLevel().getBlockState(abs).getValue(StarkFabricatorBlock.WORKING),
				"with nothing to fabricate the block goes back to idle");
		helper.succeed();
	}
}
