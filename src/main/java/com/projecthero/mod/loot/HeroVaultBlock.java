package com.projecthero.mod.loot;

import java.util.UUID;
import java.util.function.Supplier;

import org.joml.Vector3f;

import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;

/**
 * v0.17.5: the Hero Vault -- a per-player loot block (like the vanilla trial vault). {@link #TIER} 1-4 picks the loot
 * table ({@code projecthero:chests/vault_tier<N>}) and the trim colour; each player opens it once and gets their own
 * roll, popped out of the front with a sound and particles. Who has looted it lives in the {@link HeroVaultBlockEntity}.
 *
 * <p>Structure code places one with {@link HeroVault#place}.
 */
public class HeroVaultBlock extends BaseEntityBlock {
	public static final MapCodec<HeroVaultBlock> CODEC = simpleCodec(HeroVaultBlock::new);
	public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
	/** 1 Cache, 2 Site Vault, 3 Hideout Vault, 4 Dungeon Vault. */
	public static final IntegerProperty TIER = IntegerProperty.create("tier", 1, 4);

	/**
	 * Client hook (set by {@code LootClient}): the local player's UUID, so {@link #animateTick} can tell whether this
	 * reader has looted the vault yet. Common code can't touch the client classes, so it is a supplier.
	 */
	public static Supplier<UUID> localViewer = () -> null;

	public HeroVaultBlock(Properties properties) {
		super(properties);
		registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(TIER, 1));
	}

	@Override
	protected MapCodec<? extends BaseEntityBlock> codec() {
		return CODEC;
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(FACING, TIER);
	}

	@Override
	public BlockState getStateForPlacement(BlockPlaceContext ctx) {
		// the door faces the player who placed it; the tier comes from the item's block_state component (creative stacks)
		return defaultBlockState().setValue(FACING, ctx.getHorizontalDirection().getOpposite());
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
	protected RenderShape getRenderShape(BlockState state) {
		return RenderShape.MODEL;
	}

	@Override
	public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return new HeroVaultBlockEntity(pos, state);
	}

	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
		if (level instanceof ServerLevel server && player instanceof ServerPlayer sp) {
			HeroVault.open(server, pos, sp);
		}
		return InteractionResult.sidedSuccess(level.isClientSide);
	}

	/** A faint rising shimmer in the tier colour while the local player still has loot waiting here. */
	@Override
	public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
		UUID me = localViewer.get();
		if (me == null || !(level.getBlockEntity(pos) instanceof HeroVaultBlockEntity vault) || vault.hasLooted(me)) {
			return;
		}
		if (random.nextInt(3) != 0) {
			return;
		}
		int rgb = HeroVault.trimColour(state.getValue(TIER));
		Vector3f c = new Vector3f(((rgb >> 16) & 0xFF) / 255f, ((rgb >> 8) & 0xFF) / 255f, (rgb & 0xFF) / 255f);
		Direction f = state.getValue(FACING);
		double x = pos.getX() + 0.5 + f.getStepX() * 0.52 + (f.getStepZ() != 0 ? random.nextDouble() - 0.5 : 0) * 0.8;
		double z = pos.getZ() + 0.5 + f.getStepZ() * 0.52 + (f.getStepX() != 0 ? random.nextDouble() - 0.5 : 0) * 0.8;
		double y = pos.getY() + 0.15 + random.nextDouble() * 0.7;
		level.addParticle(new DustParticleOptions(c, 0.8f), x, y, z, 0, 0.02, 0);
	}
}
