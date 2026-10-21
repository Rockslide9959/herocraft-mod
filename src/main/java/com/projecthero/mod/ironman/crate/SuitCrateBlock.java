package com.projecthero.mod.ironman.crate;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import com.mojang.serialization.MapCodec;
import com.projecthero.mod.ironman.IronManEnergy;
import com.projecthero.mod.ironman.item.SuitCrateItem;
import com.projecthero.mod.ironman.suit.IronManSuit;
import com.projecthero.mod.ironman.suit.IronManSuits;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.NonNullList;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * v0.16.7 (user) ERA I: the <b>Suit Crate</b> as a block -- what the Shift+C emergency eject folds an Era I suit into
 * ({@code EraOneEject} -> {@link SuitCrateFoldEntity}). A heavy metal Stark crate holding the four pieces with their
 * integrity and energy ({@link SuitCrateBlockEntity}).
 * <ul>
 *   <li>Right-click: the stored suit's name, integrity and energy on the action bar.</li>
 *   <li>Right-click holding iron ingots: one ingot repairs {@link #REPAIR_FRACTION} of the suit's max integrity (an
 *       intentional exception to "Era I repairs only in a Gantry").</li>
 *   <li>Shift+right-click (empty hand or any other item): the 30 s suit-up, anywhere ({@link CrateSuitUp}).</li>
 *   <li>Breaking it (any tool; a pickaxe is fastest) drops the Suit Crate item with the suit inside -- in creative too,
 *       unless the crate is empty. Placing the item places the crate again, contents and all.</li>
 * </ul>
 */
public class SuitCrateBlock extends BaseEntityBlock {
	public static final MapCodec<SuitCrateBlock> CODEC = simpleCodec(SuitCrateBlock::new);
	public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;
	/** Lid up: a suit-up from this crate is running. */
	public static final BooleanProperty OPEN = BlockStateProperties.OPEN;
	/** v0.16.7 (user): an iron ingot repairs this much of the crated suit's max integrity. */
	public static final float REPAIR_FRACTION = 0.04f;
	private static final VoxelShape SHAPE = Block.box(0, 0, 0, 16, 15, 16);

	public SuitCrateBlock(Properties properties) {
		super(properties);
		registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(OPEN, false));
	}

	@Override
	protected MapCodec<? extends BaseEntityBlock> codec() {
		return CODEC;
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(FACING, OPEN);
	}

	@Override
	public BlockState getStateForPlacement(BlockPlaceContext context) {
		// the front (the arc-reactor light) faces whoever placed it
		return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
	}

	@Override
	protected RenderShape getRenderShape(BlockState state) {
		return RenderShape.MODEL;
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return SHAPE;
	}

	@Override
	public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return new SuitCrateBlockEntity(pos, state);
	}

	// ------------------------------------------------------------------ use

	@Override
	protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
			InteractionHand hand, BlockHitResult hit) {
		if (stack.isEmpty()) {
			return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
		}
		InteractionResult r = interact(level, pos, player, stack);
		return r.consumesAction() ? ItemInteractionResult.sidedSuccess(level.isClientSide()) : ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
	}

	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
		return interact(level, pos, player, ItemStack.EMPTY);
	}

	/** Every use of the crate (also from the sneak hook in {@link SuitCrateBlocks}): ingots repair, sneak suits up, else info. */
	public static InteractionResult interact(Level level, BlockPos pos, Player player, ItemStack held) {
		if (level.isClientSide()) {
			return InteractionResult.SUCCESS;
		}
		if (!(player instanceof ServerPlayer sp) || !(level.getBlockEntity(pos) instanceof SuitCrateBlockEntity be)) {
			return InteractionResult.PASS;
		}
		if (held.is(Items.IRON_INGOT)) {
			repair(sp, be, held);
		} else if (sp.isSecondaryUseActive()) {
			CrateSuitUp.begin(sp, be);
		} else {
			info(sp, be);
		}
		return InteractionResult.CONSUME;
	}

	/** One iron ingot: +{@link #REPAIR_FRACTION} of max integrity, capped at full. Returns whether it repaired. */
	public static boolean repair(ServerPlayer player, SuitCrateBlockEntity be, ItemStack ingots) {
		String suitId = be.suitId();
		if (suitId == null) {
			tell(player, "message.projecthero.ironman.era1.crate_empty", ChatFormatting.GOLD);
			return false;
		}
		if (CrateSuitUp.busy(be)) {
			tell(player, "message.projecthero.ironman.era1.crate_busy", ChatFormatting.GOLD);
			return false;
		}
		float max = IronManEnergy.maxIntegrity(suitId);
		float cur = be.integrity();
		if (cur >= max) {
			tell(player, "message.projecthero.ironman.era1.crate_repair_full", ChatFormatting.GRAY);
			return false;
		}
		float next = Math.min(max, cur + max * REPAIR_FRACTION);
		be.setIntegrity(next);
		if (!player.getAbilities().instabuild) {
			ingots.shrink(1);
		}
		if (be.getLevel() instanceof ServerLevel sl) {
			BlockPos p = be.getBlockPos();
			sl.playSound(null, p, SoundEvents.ANVIL_USE, SoundSource.BLOCKS, 0.45f, 1.25f + sl.random.nextFloat() * 0.2f);
			sl.playSound(null, p, SoundEvents.SMITHING_TABLE_USE, SoundSource.BLOCKS, 0.6f, 1.1f);
			sl.sendParticles(ParticleTypes.CRIT, p.getX() + 0.5, p.getY() + 1.0, p.getZ() + 0.5, 8, 0.3, 0.1, 0.3, 0.2);
			sl.sendParticles(ParticleTypes.ELECTRIC_SPARK, p.getX() + 0.5, p.getY() + 1.0, p.getZ() + 0.5, 5, 0.3, 0.1, 0.3, 0.1);
		}
		player.displayClientMessage(Component.translatable("message.projecthero.ironman.era1.crate_repaired",
				Math.round(next), Math.round(max)).withStyle(ChatFormatting.AQUA), true);
		return true;
	}

	/** The stored suit's name, integrity and energy, on the action bar. */
	public static void info(ServerPlayer player, SuitCrateBlockEntity be) {
		String suitId = be.suitId();
		IronManSuit suit = suitId == null ? null : IronManSuits.byId(suitId);
		if (suit == null) {
			tell(player, "message.projecthero.ironman.era1.crate_empty", ChatFormatting.GOLD);
			return;
		}
		player.displayClientMessage(Component.translatable("message.projecthero.ironman.era1.crate_info",
				Component.translatable(suit.nameKey()), Math.round(be.integrity()), Math.round(IronManEnergy.maxIntegrity(suitId)),
				Math.round(be.energy()), Math.round(IronManEnergy.capacity(suitId))).withStyle(ChatFormatting.AQUA), true);
	}

	static void tell(ServerPlayer player, String key, ChatFormatting color) {
		player.displayClientMessage(Component.translatable(key).withStyle(color), true);
	}

	// ------------------------------------------------------------------ breaking

	@Override
	public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
		// creative breaks drop no loot: hand the suit back anyway (only an empty crate simply vanishes)
		if (!level.isClientSide() && player.getAbilities().instabuild && level.getBlockEntity(pos) instanceof SuitCrateBlockEntity be
				&& !be.isEmptyCrate()) {
			ItemStack drop = SuitCrateItem.of(be.contents());
			ItemEntity item = new ItemEntity(level, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, drop);
			item.setDefaultPickUpDelay();
			level.addFreshEntity(item);
		}
		return super.playerWillDestroy(level, pos, state, player);
	}

	// ------------------------------------------------------------------ placing (the eject)

	/**
	 * The nearest spot to {@code origin} a crate can go: replaceable, inside the world, with no one standing in it --
	 * ground under it preferred. Null if there is none within 2 blocks.
	 */
	public static BlockPos findSpot(Level level, BlockPos origin) {
		List<BlockPos> candidates = new ArrayList<>();
		for (int dy = -1; dy <= 2; dy++) {
			for (int dx = -2; dx <= 2; dx++) {
				for (int dz = -2; dz <= 2; dz++) {
					candidates.add(origin.offset(dx, dy, dz));
				}
			}
		}
		candidates.sort(Comparator.comparingDouble(p -> {
			double dx = p.getX() - origin.getX();
			double dz = p.getZ() - origin.getZ();
			double dy = p.getY() - origin.getY();
			return dx * dx + dz * dz + dy * dy * 1.5 + (dy < 0 ? 0.6 : 0);
		}));
		BlockState crate = SuitCrateBlocks.SUIT_CRATE.defaultBlockState();
		for (int pass = 0; pass < 2; pass++) {
			for (BlockPos p : candidates) {
				if (!level.isInWorldBounds(p) || !level.getBlockState(p).canBeReplaced() || !level.isUnobstructed(crate, p, CollisionContext.empty())) {
					continue;
				}
				if (pass == 0 && !level.getBlockState(p.below()).isFaceSturdy(level, p.below(), Direction.UP)) {
					continue;
				}
				return p.immutable();
			}
		}
		return null;
	}

	/**
	 * Put a crate holding {@code slots} at (or next to) {@code origin}, front facing {@code facing}. If there is no room
	 * at all it falls back to dropping the Suit Crate item there -- the suit is never lost. Returns the crate's position,
	 * or null when it was dropped as an item.
	 */
	public static BlockPos place(ServerLevel level, BlockPos origin, Direction facing, NonNullList<ItemStack> slots) {
		BlockPos spot = findSpot(level, origin);
		if (spot == null) {
			ItemEntity item = new ItemEntity(level, origin.getX() + 0.5, origin.getY() + 0.5, origin.getZ() + 0.5, SuitCrateItem.of(slots));
			level.addFreshEntity(item);
			return null;
		}
		level.setBlock(spot, SuitCrateBlocks.SUIT_CRATE.defaultBlockState().setValue(FACING, facing), Block.UPDATE_ALL);
		if (level.getBlockEntity(spot) instanceof SuitCrateBlockEntity be) {
			be.setContents(slots);
		}
		return spot;
	}
}
