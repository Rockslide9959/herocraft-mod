package com.projecthero.mod.vaultdungeon.block;

import java.util.Locale;

import org.joml.Vector3f;

import com.mojang.serialization.MapCodec;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.network.chat.Component;
import net.minecraft.util.RandomSource;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;

/**
 * v0.17.7: a <b>security field</b> -- a humming, see-through energy wall in The Vault's new floors. Unbreakable; nothing
 * passes. Two kinds:
 * <ul>
 * <li>{@link Kind#POWER} (blue) -- runs on the cell-block power: the Maximum Security pods are fronted with it and it
 * bars the way down from the Warden's Control Room. Shutting the power down at the Control Room breaker
 * ({@link VaultBreakerBlock}) drops every one of them at once ({@code VaultSecurity.shutDown}).</li>
 * <li>{@link Kind#CAPTAIN} (red) -- the Riot Captain's lockdown on the door out of the Maximum Security hall; it drops
 * when he dies ({@code VaultSecurity.captainDown}).</li>
 * </ul>
 */
public class VaultBarrierBlock extends Block {
	public static final MapCodec<VaultBarrierBlock> CODEC = simpleCodec(VaultBarrierBlock::new);

	public enum Kind implements StringRepresentable {
		POWER, CAPTAIN;

		@Override
		public String getSerializedName() {
			return name().toLowerCase(Locale.ROOT);
		}
	}

	public static final EnumProperty<Kind> KIND = EnumProperty.create("kind", Kind.class);
	private static final DustParticleOptions BLUE = new DustParticleOptions(new Vector3f(0.35f, 0.7f, 1f), 0.7f);
	private static final DustParticleOptions RED = new DustParticleOptions(new Vector3f(1f, 0.2f, 0.15f), 0.7f);

	public VaultBarrierBlock(BlockBehaviour.Properties props) {
		super(props);
		registerDefaultState(stateDefinition.any().setValue(KIND, Kind.POWER));
	}

	@Override
	protected MapCodec<? extends Block> codec() {
		return CODEC;
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(KIND);
	}

	@Override
	protected boolean skipRendering(BlockState state, BlockState adjacent, Direction side) {
		return adjacent.is(this) || super.skipRendering(state, adjacent, side);
	}

	@Override
	protected float getShadeBrightness(BlockState state, BlockGetter level, BlockPos pos) {
		return 1.0f;
	}

	@Override
	protected boolean propagatesSkylightDown(BlockState state, BlockGetter level, BlockPos pos) {
		return true;
	}

	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
		if (!level.isClientSide()) {
			String key = state.getValue(KIND) == Kind.POWER ? "block.projecthero.vault_barrier.power" : "block.projecthero.vault_barrier.captain";
			player.displayClientMessage(Component.translatable(key).withStyle(ChatFormatting.GRAY), true);
		}
		return InteractionResult.sidedSuccess(level.isClientSide());
	}

	@Override
	public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
		if (random.nextInt(3) != 0) {
			return;
		}
		DustParticleOptions dust = state.getValue(KIND) == Kind.POWER ? BLUE : RED;
		level.addParticle(dust, pos.getX() + random.nextDouble(), pos.getY() + random.nextDouble(), pos.getZ() + random.nextDouble(), 0, 0, 0);
	}
}
