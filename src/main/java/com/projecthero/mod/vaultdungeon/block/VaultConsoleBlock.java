package com.projecthero.mod.vaultdungeon.block;

import com.mojang.serialization.MapCodec;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.phys.BlockHitResult;

/**
 * v0.17.5: the Security Floor's <b>security console</b>. Right-click: every iron door within {@link #REACH} blocks
 * swings open (the lockdown gate to the Warden's Level), with a release chime. Using it again closes them.
 */
public class VaultConsoleBlock extends HorizontalDirectionalBlock {
	public static final MapCodec<VaultConsoleBlock> CODEC = simpleCodec(VaultConsoleBlock::new);
	public static final int REACH = 32;

	public VaultConsoleBlock(BlockBehaviour.Properties props) {
		super(props);
		registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
	}

	@Override
	protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
		return CODEC;
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(FACING);
	}

	@Override
	public BlockState getStateForPlacement(BlockPlaceContext ctx) {
		return defaultBlockState().setValue(FACING, ctx.getHorizontalDirection().getOpposite());
	}

	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
		if (level instanceof ServerLevel server) {
			int[] counts = toggleDoors(server, pos, player);
			if (counts[0] + counts[1] == 0) {
				player.displayClientMessage(Component.translatable("block.projecthero.vault_console.none").withStyle(ChatFormatting.GRAY), true);
			} else {
				boolean opened = counts[0] > 0;
				player.displayClientMessage(Component.translatable(opened ? "block.projecthero.vault_console.open" : "block.projecthero.vault_console.closed")
						.withStyle(opened ? ChatFormatting.GREEN : ChatFormatting.RED), true);
				server.playSound(null, pos, opened ? SoundEvents.BEACON_ACTIVATE : SoundEvents.BEACON_DEACTIVATE, SoundSource.BLOCKS, 1.0f, 1.6f);
			}
		}
		return InteractionResult.sidedSuccess(level.isClientSide());
	}

	/**
	 * Opens every closed iron door within reach -- or, when they are all open already, closes them. Returns
	 * {opened, closed}.
	 */
	public static int[] toggleDoors(ServerLevel level, BlockPos pos, Player player) {
		java.util.List<BlockPos> doors = new java.util.ArrayList<>();
		boolean anyClosed = false;
		for (BlockPos p : BlockPos.betweenClosed(pos.offset(-REACH, -4, -REACH), pos.offset(REACH, 4, REACH))) {
			BlockState s = level.getBlockState(p);
			if (s.getBlock() instanceof DoorBlock && s.is(net.minecraft.world.level.block.Blocks.IRON_DOOR)
					&& s.getValue(DoorBlock.HALF) == DoubleBlockHalf.LOWER) {
				doors.add(p.immutable());
				anyClosed |= !s.getValue(DoorBlock.OPEN);
			}
		}
		int opened = 0;
		int closed = 0;
		for (BlockPos p : doors) {
			BlockState s = level.getBlockState(p);
			if (s.getValue(DoorBlock.OPEN) != anyClosed) {
				((DoorBlock) s.getBlock()).setOpen(player, level, s, p, anyClosed);
				if (anyClosed) {
					opened++;
				} else {
					closed++;
				}
			}
		}
		return new int[] { opened, closed };
	}
}
