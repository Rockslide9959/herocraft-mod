package com.projecthero.mod.temple.block;

import java.util.Locale;

import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;

/**
 * v0.17.7: a <b>Temple Gate</b> -- a slab of carved stone filling a doorway. Unbreakable; it sinks away when what it waits
 * for happens ({@code gate}): {@link Group#TRIALS} when the Hall of Trials' three seals are broken, {@link Group#CRYPT}
 * when the Tomb Sentinel falls, {@link Group#VAULT} when Ankhatep falls ({@link #open}). Using it says what it waits for.
 */
public class TempleGateBlock extends Block {
	public static final MapCodec<TempleGateBlock> CODEC = simpleCodec(TempleGateBlock::new);
	public static final EnumProperty<Group> GATE = EnumProperty.create("gate", Group.class);

	public enum Group implements StringRepresentable {
		TRIALS, CRYPT, VAULT;

		@Override
		public String getSerializedName() {
			return name().toLowerCase(Locale.ROOT);
		}
	}

	public TempleGateBlock(BlockBehaviour.Properties props) {
		super(props);
		registerDefaultState(stateDefinition.any().setValue(GATE, Group.TRIALS));
	}

	@Override
	protected MapCodec<? extends Block> codec() {
		return CODEC;
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(GATE);
	}

	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
		if (level instanceof ServerLevel) {
			player.displayClientMessage(Component.translatable("block.projecthero.temple_gate." + state.getValue(GATE).getSerializedName())
					.withStyle(s -> s.withColor(0xE8C060)), true);
		}
		return InteractionResult.sidedSuccess(level.isClientSide());
	}

	/** Opens every {@code group} gate block within {@code r} (horizontal, ±6 vertical) of {@code centre}. Returns how many. */
	public static int open(ServerLevel level, BlockPos centre, Group group, int r) {
		int n = 0;
		for (BlockPos p : BlockPos.betweenClosed(centre.offset(-r, -6, -r), centre.offset(r, 6, r))) {
			BlockState s = level.getBlockState(p);
			if (s.is(TempleBlocks.GATE) && s.getValue(GATE) == group) {
				level.setBlock(p, Blocks.AIR.defaultBlockState(), 3);
				level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.SANDSTONE.defaultBlockState()), p.getX() + 0.5, p.getY() + 0.5,
						p.getZ() + 0.5, 12, 0.3, 0.4, 0.3, 0.05);
				if (n++ == 0) {
					level.playSound(null, p, SoundEvents.PISTON_CONTRACT, SoundSource.BLOCKS, 2.0f, 0.4f);
					level.playSound(null, p, SoundEvents.ANCIENT_DEBRIS_BREAK, SoundSource.BLOCKS, 2.0f, 0.5f);
				}
			}
		}
		return n;
	}
}
