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
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
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
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * The Stark Fabricator block (spec sections 5-8). Right-click:
 * <ul>
 *   <li>with a {@code reactor_core} or {@code arc_reactor} in hand and the Tony Stark power: inject
 *       Stark energy into the buffer, consume the item, play a charge cue -- explicit, never a
 *       surprise consumption;</li>
 *   <li>empty-handed with the Tony Stark power: open the fabrication interface;</li>
 *   <li>without the Tony Stark power: "You don't understand Stark technology." -- the interface never
 *       opens, and {@link StarkFabricatorMenu#stillValid} would refuse it even if it did.</li>
 * </ul>
 *
 * <p>v0.14.21 model: a gunmetal workbench (static JSON model) with a GeckoLib rig on top -- robotic welding arm,
 * holographic helmet over the glass work plate, emissive light strips -- animated by the client renderer. The block
 * faces the player who placed it ({@link #FACING}); {@link #WORKING} is true while a fabrication is in progress and
 * switches the rig to its working animation, brightens the block and throws welding sparks off the arm.
 */
public class StarkFabricatorBlock extends BaseEntityBlock {
	public static final MapCodec<StarkFabricatorBlock> CODEC = simpleCodec(StarkFabricatorBlock::new);
	public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;
	public static final BooleanProperty WORKING = BooleanProperty.create("working");

	/** Hit box: the bench, the arm turret and the rear display gantry (model pixels, front = north). */
	private static final Map<Direction, VoxelShape> SHAPES = IronManBlockShapes.byFacing(
			new double[] {0, 0, 0, 16, 11, 16},
			new double[] {11, 11, 8.5, 14, 13, 11.5},
			new double[] {0.5, 11, 12, 15.5, 16, 15});
	/** Where the arm's emitter welds while working (model pixels; matches the generated working animation). */
	private static final double TIP_X = 8.2, TIP_Y = 12.4, TIP_Z = 6.6;

	private static final int ARC_REACTOR_ENERGY = 25_000;
	private static final int REACTOR_CORE_ENERGY = 8_000;

	public StarkFabricatorBlock(Properties properties) {
		super(properties);
		registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(WORKING, false));
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(FACING, WORKING);
	}

	/** The work surface and its display face the player who placed it. */
	@Override
	public BlockState getStateForPlacement(BlockPlaceContext context) {
		return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
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
		return SHAPES.get(state.getValue(FACING));
	}

	/** Welding sparks off the arm's emitter while a piece is being fabricated. */
	@Override
	public void animateTick(BlockState state, Level level, BlockPos pos, net.minecraft.util.RandomSource random) {
		if (!state.getValue(WORKING) || random.nextInt(3) != 0) {
			return;
		}
		Vec3 tip = IronManBlockShapes.point(TIP_X, TIP_Y, TIP_Z, state.getValue(FACING)).add(pos.getX(), pos.getY(), pos.getZ());
		for (int i = 0; i < 2; i++) {
			level.addParticle(ParticleTypes.ELECTRIC_SPARK, tip.x, tip.y, tip.z,
					(random.nextDouble() - 0.5) * 0.12, random.nextDouble() * 0.08, (random.nextDouble() - 0.5) * 0.12);
		}
	}

	@Override
	protected MapCodec<? extends BaseEntityBlock> codec() {
		return CODEC;
	}

	/**
	 * Hand back whatever was in the machine when it is broken -- ingredients mid-craft and a finished
	 * suit piece still sitting in the output. The loot table only drops the block itself, so without
	 * this everything inside was silently deleted.
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
		return new StarkFabricatorBlockEntity(pos, state);
	}

	@Override
	public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
		return level.isClientSide() ? null
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
		if (!(level.getBlockEntity(pos) instanceof StarkFabricatorBlockEntity be) || be.isFull()) {
			player.displayClientMessage(Component.translatable("message.projecthero.stark_fabricator.energy_full"), true);
			return ItemInteractionResult.SUCCESS;
		}
		be.addEnergy(energy);
		if (!player.getAbilities().instabuild) {
			stack.shrink(1);
		}
		((ServerLevel) level).playSound(null, pos, SoundEvents.CONDUIT_ACTIVATE, SoundSource.BLOCKS, 0.8f, 1.4f);
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
		if (!level.isClientSide() && player instanceof ServerPlayer serverPlayer
				&& level.getBlockEntity(pos) instanceof StarkFabricatorBlockEntity be) {
			serverPlayer.openMenu(be);
		}
		return InteractionResult.sidedSuccess(level.isClientSide());
	}

	private static void deny(Player player) {
		player.displayClientMessage(
				Component.translatable("message.projecthero.stark_fabricator.no_understanding").withStyle(ChatFormatting.RED), true);
	}
}
