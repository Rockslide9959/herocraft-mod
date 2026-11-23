package com.projecthero.mod.alarm.block;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.mojang.serialization.MapCodec;
import com.projecthero.mod.alarm.AlarmKeycards;
import com.projecthero.mod.alarm.AlarmZones;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * v0.17.7: a <b>Blast Door</b> panel. Builders fill a doorway with them (each block is one panel; connected panels act
 * as one door). {@link #OPEN} panels have no shape and draw nothing. {@link #LOCKED} doors stay shut until the place's
 * Keycard is used on them (or an alarm wave is cleared); unlocked doors shut whenever their zone goes Alert and open
 * again when a reinforcement wave dies or the zone calms down. Unbreakable.
 */
public class BlastDoorBlock extends Block {
	public static final MapCodec<BlastDoorBlock> CODEC = simpleCodec(BlastDoorBlock::new);
	public static final EnumProperty<AlarmStyle> STYLE = AlarmBlocks.STYLE;
	public static final BooleanProperty OPEN = BooleanProperty.create("open");
	public static final BooleanProperty LOCKED = BooleanProperty.create("locked");
	public static final int MAX_DOOR = 64;

	public BlastDoorBlock(BlockBehaviour.Properties props) {
		super(props);
		registerDefaultState(stateDefinition.any().setValue(STYLE, AlarmStyle.TECH).setValue(OPEN, false).setValue(LOCKED, false));
	}

	@Override
	protected MapCodec<? extends Block> codec() {
		return CODEC;
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(STYLE, OPEN, LOCKED);
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
		return state.getValue(OPEN) ? Shapes.empty() : Shapes.block();
	}

	@Override
	protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
		return state.getValue(OPEN) ? Shapes.empty() : Shapes.block();
	}

	@Override
	protected RenderShape getRenderShape(BlockState state) {
		return state.getValue(OPEN) ? RenderShape.INVISIBLE : RenderShape.MODEL;
	}

	@Override
	protected boolean propagatesSkylightDown(BlockState state, BlockGetter level, BlockPos pos) {
		return state.getValue(OPEN);
	}

	@Override
	protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState old, boolean moving) {
		super.onPlace(state, level, pos, old, moving);
		if (level instanceof ServerLevel server && !old.is(this)) {
			AlarmZones.noteBlock(server, pos, state);
		}
	}

	@Override
	protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand,
			BlockHitResult hit) {
		if (!AlarmKeycards.isKeycard(stack)) {
			return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
		}
		if (level instanceof ServerLevel server && player instanceof ServerPlayer sp) {
			AlarmKeycards.useOnDoor(server, pos, stack, sp);
		}
		return ItemInteractionResult.sidedSuccess(level.isClientSide);
	}

	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
		if (level instanceof ServerLevel server && player instanceof ServerPlayer sp) {
			AlarmKeycards.explainDoor(server, pos, state, sp);
		}
		return InteractionResult.sidedSuccess(level.isClientSide);
	}

	/** Sets one panel open / shut (no sound; callers play one per door). */
	public static void setOpen(ServerLevel level, BlockPos pos, BlockState state, boolean open) {
		level.setBlock(pos, state.setValue(OPEN, open), 3);
		level.sendParticles(open ? ParticleTypes.CLOUD : ParticleTypes.CRIT, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 3, 0.3, 0.3, 0.3, 0.01);
	}

	public static void sound(ServerLevel level, BlockPos pos, boolean open) {
		level.playSound(null, pos, open ? SoundEvents.PISTON_CONTRACT : SoundEvents.PISTON_EXTEND, SoundSource.BLOCKS, 1.2f, 0.5f);
		level.playSound(null, pos, open ? SoundEvents.IRON_DOOR_OPEN : SoundEvents.IRON_DOOR_CLOSE, SoundSource.BLOCKS, 1.0f, 0.6f);
	}

	/** Every Blast Door panel connected to {@code start} (6 neighbours, at most {@link #MAX_DOOR}). */
	public static List<BlockPos> connected(Level level, BlockPos start) {
		List<BlockPos> out = new ArrayList<>();
		Set<BlockPos> seen = new HashSet<>();
		ArrayDeque<BlockPos> todo = new ArrayDeque<>();
		todo.add(start.immutable());
		seen.add(start.immutable());
		while (!todo.isEmpty() && out.size() < MAX_DOOR) {
			BlockPos p = todo.poll();
			if (!level.getBlockState(p).is(AlarmBlocks.BLAST_DOOR)) {
				continue;
			}
			out.add(p);
			for (Direction d : Direction.values()) {
				BlockPos n = p.relative(d);
				if (seen.add(n)) {
					todo.add(n);
				}
			}
		}
		return out;
	}
}
