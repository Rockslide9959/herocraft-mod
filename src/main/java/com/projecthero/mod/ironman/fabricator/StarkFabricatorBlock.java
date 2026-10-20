package com.projecthero.mod.ironman.fabricator;

import com.projecthero.mod.ironman.IronManBlocks;
import com.projecthero.mod.ironman.TonyStark;
import com.projecthero.mod.ironman.item.IronManItems;

import com.mojang.serialization.MapCodec;

import net.minecraft.ChatFormatting;
import java.util.Map;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * The Stark Workbench (v0.16.7 (user): the Stark Fabricator rebuilt as a two-block holographic desk; the block id stays
 * {@code stark_fabricator} so worlds convert by themselves). Right-click:
 * <ul>
 *   <li>with a {@code reactor_core} or {@code arc_reactor} in hand and the Tony Stark power: inject Stark energy into the
 *       buffer, consume the item, play a charge cue -- explicit, never a surprise consumption;</li>
 *   <li>empty-handed with the Tony Stark power: open the Workbench screen;</li>
 *   <li>without the Tony Stark power: "You don't understand Stark technology." -- the interface never opens, and
 *       {@link StarkFabricatorMenu#stillValid} would refuse it even if it did.</li>
 * </ul>
 *
 * <p>Two halves like a bed ({@link #PART}): placing claims the block to the placer's right (refused when it is not free);
 * breaking either half removes both and drops ONE item (the loot table only drops non-side halves; breaking the side half
 * drops the main half's loot by hand); the side half forwards every interaction to the main half, which owns the one block
 * entity. A one-block Fabricator from an older world loads as {@link WorkbenchPart#SINGLE} -- a complete working desk --
 * and claims its side half by itself once that block is air (see {@link StarkFabricatorBlockEntity#serverTick}). If the
 * side half disappears some other way (a command, another mod) the main half falls back to SINGLE instead of vanishing,
 * so nothing inside is ever lost.
 *
 * <p>The block faces the player who placed it ({@link #FACING}); {@link #WORKING} is true while a fabrication is in
 * progress (brighter light + sparks off the projector; the client hologram reads the job from the block entity).
 */
public class StarkFabricatorBlock extends BaseEntityBlock {
	public static final MapCodec<StarkFabricatorBlock> CODEC = simpleCodec(StarkFabricatorBlock::new);
	public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;
	public static final BooleanProperty WORKING = BooleanProperty.create("working");
	public static final EnumProperty<WorkbenchPart> PART = EnumProperty.create("part", WorkbenchPart.class);

	// Hit boxes per half, model pixels, front = north (see scratchpad/gen_stark_workbench_model.js for the matching model).
	private static final Map<Direction, VoxelShape> SHAPE_MAIN = IronManBlockShapes.byFacing(
			new double[] {0, 11, 0, 16, 14, 16},      // desk top + edge strip
			new double[] {6, 0, 1, 15, 11, 15},       // cabinet
			new double[] {0, 8, 1.5, 6, 11, 14.5},    // drawer
			new double[] {0, 14, 4.5, 3.5, 15.5, 11.5}); // projector (half)
	private static final Map<Direction, VoxelShape> SHAPE_SIDE = IronManBlockShapes.byFacing(
			new double[] {0, 11, 0, 16, 14, 16},
			new double[] {1, 0, 1, 10, 11, 15},
			new double[] {10, 8, 1.5, 16, 11, 14.5},
			new double[] {12.5, 14, 4.5, 16, 15.5, 11.5},
			new double[] {4, 14, 6, 7, 16.8, 9});        // arc reactor stand
	private static final Map<Direction, VoxelShape> SHAPE_SINGLE = IronManBlockShapes.byFacing(
			new double[] {0, 11, 0, 16, 14, 16},
			new double[] {11.5, 0, 1, 15.5, 11, 15},
			new double[] {0.5, 0, 1, 4.5, 11, 15},
			new double[] {4.5, 8, 1.5, 11.5, 11, 14.5},
			new double[] {4.5, 14, 4.5, 11.5, 15.5, 11.5});

	private static final int ARC_REACTOR_ENERGY = 25_000;
	private static final int REACTOR_CORE_ENERGY = 8_000;

	public StarkFabricatorBlock(Properties properties) {
		super(properties);
		registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(WORKING, false)
				.setValue(PART, WorkbenchPart.SINGLE));
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(FACING, WORKING, PART);
	}

	// ------------------------------------------------------------------ the two halves

	/** Which way the side half lies from the main half: the placer's right, i.e. counter-clockwise of the front. */
	public static Direction sideDirection(Direction facing) {
		return facing.getCounterClockWise();
	}

	/** Where the block entity lives for a workbench block at {@code pos}: the main half (or the block itself). */
	public static BlockPos mainPos(BlockState state, BlockPos pos) {
		if (state.hasProperty(PART) && state.getValue(PART) == WorkbenchPart.SIDE) {
			return pos.relative(sideDirection(state.getValue(FACING)).getOpposite());
		}
		return pos;
	}

	/** The workbench's block entity behind {@code pos} (either half), or null. */
	public static StarkFabricatorBlockEntity blockEntity(BlockGetter level, BlockPos pos) {
		BlockState state = level.getBlockState(pos);
		if (!(state.getBlock() instanceof StarkFabricatorBlock)) {
			return null;
		}
		return level.getBlockEntity(mainPos(state, pos)) instanceof StarkFabricatorBlockEntity be ? be : null;
	}

	/** The work surface faces the player who placed it; the side half goes to their right and must be free. */
	@Override
	public BlockState getStateForPlacement(BlockPlaceContext context) {
		Direction facing = context.getHorizontalDirection().getOpposite();
		BlockPos side = context.getClickedPos().relative(sideDirection(facing));
		Level level = context.getLevel();
		if (!level.getBlockState(side).canBeReplaced(context) || !level.getWorldBorder().isWithinBounds(side)) {
			if (!level.isClientSide() && context.getPlayer() != null) {
				context.getPlayer().displayClientMessage(Component.translatable("message.projecthero.stark_workbench.no_room")
						.withStyle(ChatFormatting.RED), true);
			}
			return null;
		}
		return defaultBlockState().setValue(FACING, facing).setValue(PART, WorkbenchPart.MAIN);
	}

	@Override
	public void setPlacedBy(Level level, BlockPos pos, BlockState state, LivingEntity placer, ItemStack stack) {
		super.setPlacedBy(level, pos, state, placer, stack);
		if (!level.isClientSide() && state.getValue(PART) == WorkbenchPart.MAIN) {
			BlockPos side = pos.relative(sideDirection(state.getValue(FACING)));
			level.setBlock(side, state.setValue(PART, WorkbenchPart.SIDE), Block.UPDATE_ALL);
			level.blockUpdated(pos, Blocks.AIR);
			state.updateNeighbourShapes(level, pos, Block.UPDATE_ALL);
		}
	}

	/**
	 * A main half whose side half went missing falls back to a working one-block desk (nothing inside is lost); a side
	 * half whose main half went missing disappears (it never drops anything itself).
	 */
	@Override
	protected BlockState updateShape(BlockState state, Direction direction, BlockState neighbor, LevelAccessor level,
			BlockPos pos, BlockPos neighborPos) {
		WorkbenchPart part = state.getValue(PART);
		Direction sideDir = sideDirection(state.getValue(FACING));
		if (part == WorkbenchPart.MAIN && direction == sideDir && !isPartner(state, neighbor, WorkbenchPart.SIDE)) {
			return state.setValue(PART, WorkbenchPart.SINGLE);
		}
		if (part == WorkbenchPart.SIDE && direction == sideDir.getOpposite() && !isPartner(state, neighbor, WorkbenchPart.MAIN)) {
			return Blocks.AIR.defaultBlockState();
		}
		if (part == WorkbenchPart.SIDE && direction == sideDir.getOpposite() && neighbor.getValue(WORKING) != state.getValue(WORKING)) {
			return state.setValue(WORKING, neighbor.getValue(WORKING)); // the side half glows with the main half
		}
		return super.updateShape(state, direction, neighbor, level, pos, neighborPos);
	}

	private boolean isPartner(BlockState self, BlockState other, WorkbenchPart wanted) {
		return other.is(this) && other.getValue(PART) == wanted && other.getValue(FACING) == self.getValue(FACING);
	}

	/**
	 * Breaking one half takes the other with it, and exactly one item drops: the main half drops through its loot table
	 * (the side half's partner is removed without drops); breaking the side half drops the main half's loot by hand
	 * (with the breaker's tool, so the pickaxe rule still holds) and removes the main half without drops. Creative breaks
	 * drop nothing, as usual.
	 */
	@Override
	public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
		if (!level.isClientSide()) {
			WorkbenchPart part = state.getValue(PART);
			Direction sideDir = sideDirection(state.getValue(FACING));
			if (part == WorkbenchPart.MAIN) {
				BlockPos side = pos.relative(sideDir);
				BlockState sideState = level.getBlockState(side);
				if (isPartner(state, sideState, WorkbenchPart.SIDE)) {
					level.setBlock(side, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL | Block.UPDATE_SUPPRESS_DROPS);
					level.levelEvent(player, 2001, side, Block.getId(sideState));
				}
			} else if (part == WorkbenchPart.SIDE) {
				BlockPos main = pos.relative(sideDir.getOpposite());
				BlockState mainState = level.getBlockState(main);
				if (isPartner(state, mainState, WorkbenchPart.MAIN)) {
					if (!player.getAbilities().instabuild && player.hasCorrectToolForDrops(mainState)) { // (abilities, not isCreative: mock players report creative)
						Block.dropResources(mainState, level, main, level.getBlockEntity(main), player, player.getMainHandItem());
					}
					level.setBlock(main, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL | Block.UPDATE_SUPPRESS_DROPS);
					level.levelEvent(player, 2001, main, Block.getId(mainState));
				}
			}
		}
		return super.playerWillDestroy(level, pos, state, player);
	}

	@Override
	protected BlockState rotate(BlockState state, Rotation rotation) {
		return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
	}

	@Override
	protected BlockState mirror(BlockState state, Mirror mirror) {
		return state.rotate(mirror.getRotation(state.getValue(FACING)));
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		Direction facing = state.getValue(FACING);
		return switch (state.getValue(PART)) {
			case MAIN -> SHAPE_MAIN.get(facing);
			case SIDE -> SHAPE_SIDE.get(facing);
			default -> SHAPE_SINGLE.get(facing);
		};
	}

	/** Sparks off the projector disc while a piece is being fabricated. */
	@Override
	public void animateTick(BlockState state, Level level, BlockPos pos, net.minecraft.util.RandomSource random) {
		if (!state.getValue(WORKING) || state.getValue(PART) == WorkbenchPart.SIDE || random.nextInt(3) != 0) {
			return;
		}
		Vec3 c = deskCentre(state, pos);
		for (int i = 0; i < 2; i++) {
			level.addParticle(ParticleTypes.ELECTRIC_SPARK, c.x + (random.nextDouble() - 0.5) * 0.3, c.y + 1.0 / 16.0 + random.nextDouble() * 0.6,
					c.z + (random.nextDouble() - 0.5) * 0.3, 0, 0.02, 0);
		}
	}

	/** World position of the projector lens top (the hologram's floor) for the workbench whose main half is at {@code pos}. */
	public static Vec3 deskCentre(BlockState state, BlockPos pos) {
		Vec3 c = Vec3.atBottomCenterOf(pos).add(0, 15.5 / 16.0, 0);
		if (state.hasProperty(PART) && state.getValue(PART) == WorkbenchPart.MAIN) {
			Direction side = sideDirection(state.getValue(FACING));
			c = c.add(side.getStepX() * 0.5, 0, side.getStepZ() * 0.5);
		}
		return c;
	}

	@Override
	protected MapCodec<? extends BaseEntityBlock> codec() {
		return CODEC;
	}

	/**
	 * Hand back whatever was in the machine when it is broken -- ingredients mid-craft and a finished suit piece still
	 * sitting in the output. The loot table only drops the block itself, so without this everything inside was silently
	 * deleted. (Only the main / single half has a block entity.)
	 */
	@Override
	protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
		if (!state.is(newState.getBlock()) && level.getBlockEntity(pos) instanceof net.minecraft.world.Container c) {
			net.minecraft.world.Containers.dropContents(level, pos, c);
		}
		super.onRemove(state, level, pos, newState, movedByPiston);
	}

	@Override
	protected RenderShape getRenderShape(BlockState state) {
		return RenderShape.MODEL;
	}

	@Override
	public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return state.getValue(PART) == WorkbenchPart.SIDE ? null : new StarkFabricatorBlockEntity(pos, state);
	}

	@Override
	public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
		return level.isClientSide() || state.getValue(PART) == WorkbenchPart.SIDE ? null
				: createTickerHelper(type, IronManBlocks.STARK_FABRICATOR_BE, StarkFabricatorBlockEntity::serverTick);
	}

	@Override
	protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
			Player player, InteractionHand hand, BlockHitResult hit) {
		int energy = stack.is(IronManItems.ARC_REACTOR) ? ARC_REACTOR_ENERGY
				: stack.is(IronManItems.REACTOR_CORE) ? REACTOR_CORE_ENERGY : -1;
		if (energy < 0) {
			return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
		}
		if (!TonyStark.hasPower(player)) {
			deny(player);
			return ItemInteractionResult.SUCCESS;
		}
		if (level.isClientSide()) {
			return ItemInteractionResult.SUCCESS;
		}
		StarkFabricatorBlockEntity be = blockEntity(level, pos); // either half: the side half forwards to the main half
		if (be == null || be.isFull()) {
			player.displayClientMessage(Component.translatable("message.projecthero.stark_fabricator.energy_full"), true);
			return ItemInteractionResult.SUCCESS;
		}
		be.addEnergy(energy);
		if (!player.getAbilities().instabuild) {
			stack.shrink(1);
		}
		((ServerLevel) level).playSound(null, be.getBlockPos(), SoundEvents.CONDUIT_ACTIVATE, SoundSource.BLOCKS, 0.8f, 1.4f);
		player.displayClientMessage(Component.translatable("message.projecthero.stark_fabricator.energy_added",
				be.energy(), FabricatorRecipes.MAX_ENERGY).withStyle(ChatFormatting.AQUA), true);
		return ItemInteractionResult.sidedSuccess(false);
	}

	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
			BlockHitResult hit) {
		if (!TonyStark.hasPower(player)) {
			if (!level.isClientSide()) {
				deny(player);
			}
			return InteractionResult.SUCCESS;
		}
		if (!level.isClientSide() && player instanceof ServerPlayer serverPlayer) {
			StarkFabricatorBlockEntity be = blockEntity(level, pos); // either half opens the main half's menu
			if (be != null) {
				serverPlayer.openMenu(be);
			}
		}
		return InteractionResult.sidedSuccess(level.isClientSide());
	}

	private static void deny(Player player) {
		player.displayClientMessage(
				Component.translatable("message.projecthero.stark_fabricator.no_understanding").withStyle(ChatFormatting.RED), true);
	}
}
