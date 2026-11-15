package com.projecthero.mod.sentry.block;

import com.mojang.serialization.MapCodec;
import com.projecthero.mod.sentry.item.SentryItems;
import com.projecthero.mod.vaultdungeon.VaultClearing;
import com.projecthero.mod.vaultdungeon.VaultDungeonState;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * v0.17.5: the sealed <b>O.X.E. serum vat</b> in The Vault's vault room, behind Warden Kross's arena -- the only place the Sentry
 * Serum comes from (it is not craftable and in no other loot). Unbreakable. It opens with the Dungeon Vaults when Kross falls
 * ({@code VaultClearing.unsealVaults}), or when it is used while no Kross is within 96 of it (the dungeon can never get stuck). Open,
 * it gives every player one Sentry Serum (kept in {@link SerumVatBlockEntity}).
 */
public class SerumVatBlock extends BaseEntityBlock {
	public static final MapCodec<SerumVatBlock> CODEC = simpleCodec(SerumVatBlock::new);
	public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
	public static final BooleanProperty OPEN = BlockStateProperties.OPEN;
	private static final VoxelShape SHAPE = Block.box(1, 0, 1, 15, 16, 15);

	public SerumVatBlock(BlockBehaviour.Properties props) {
		super(props);
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
	public BlockState getStateForPlacement(BlockPlaceContext ctx) {
		return defaultBlockState().setValue(FACING, ctx.getHorizontalDirection().getOpposite());
	}

	@Override
	protected RenderShape getRenderShape(BlockState state) {
		return RenderShape.MODEL;
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
		return SHAPE;
	}

	@Override
	public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return new SerumVatBlockEntity(pos, state);
	}

	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
		if (level instanceof ServerLevel server) {
			use(server, pos, state, player);
		}
		return InteractionResult.sidedSuccess(level.isClientSide());
	}

	/** What using the vat does (for the tests too): true when a serum was handed over. */
	public static boolean use(ServerLevel level, BlockPos pos, BlockState state, Player player) {
		if (!state.getValue(OPEN)) {
			if (VaultClearing.guardianNear(level, pos) != null) {
				player.displayClientMessage(Component.translatable("block.projecthero.oxe_serum_vat.sealed").withStyle(ChatFormatting.GOLD), true);
				return false;
			}
			// the Warden is gone (dead without a clearing, Peaceful, a command): the vault room is open anyway
			VaultDungeonState.get(level).markCleared(pos, level.getGameTime());
			open(level, pos);
			state = level.getBlockState(pos);
			if (!state.is(SentryBlocks.SERUM_VAT)) {
				return false;
			}
		}
		if (!(level.getBlockEntity(pos) instanceof SerumVatBlockEntity vat)) {
			return false;
		}
		if (!vat.claim(player.getUUID())) {
			player.displayClientMessage(Component.translatable("block.projecthero.oxe_serum_vat.taken").withStyle(ChatFormatting.GRAY), true);
			return false;
		}
		ItemStack serum = new ItemStack(SentryItems.SENTRY_SERUM);
		if (!player.getInventory().add(serum)) {
			player.drop(serum, false);
		}
		level.playSound(null, pos, SoundEvents.BOTTLE_FILL, SoundSource.BLOCKS, 1.0f, 0.8f);
		level.sendParticles(ParticleTypes.END_ROD, pos.getX() + 0.5, pos.getY() + 0.9, pos.getZ() + 0.5, 12, 0.3, 0.3, 0.3, 0.02);
		player.displayClientMessage(Component.translatable("block.projecthero.oxe_serum_vat.got").withStyle(ChatFormatting.GOLD), true);
		return true;
	}

	/** Opens the vat at {@code pos} (from {@code VaultClearing.unsealVaults} when Kross falls). True if it was sealed. */
	public static boolean open(ServerLevel level, BlockPos pos) {
		BlockState s = level.getBlockState(pos);
		if (!s.is(SentryBlocks.SERUM_VAT) || s.getValue(OPEN)) {
			return false;
		}
		level.setBlock(pos, s.setValue(OPEN, true), Block.UPDATE_ALL);
		level.playSound(null, pos, SoundEvents.IRON_DOOR_OPEN, SoundSource.BLOCKS, 1.4f, 0.6f);
		level.sendParticles(ParticleTypes.CLOUD, pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5, 20, 0.3, 0.2, 0.3, 0.02);
		return true;
	}
}
