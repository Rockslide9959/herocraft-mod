package com.projecthero.mod.frostgiant.block;

import java.util.List;

import com.mojang.serialization.MapCodec;
import com.projecthero.mod.frostgiant.FrostGiants;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Difficulty;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;

/**
 * v0.16.1: the <b>Jotunheim Rune Stone</b>. Place it and right-click it <b>at night</b>, in Survival (never on Peaceful),
 * to tear open a rift to Jotunheim above it: the Frost Giant Assault begins, centred here. The stone becomes the <b>Rift
 * Anchor</b> -- {@link #ACTIVE}: blue-white, light 10, impossible to mine while the assault runs -- and turns into the
 * reward chest when King Laufey falls (or shatters if the defenders do).
 */
public class JotunheimRuneStoneBlock extends Block {
	public static final MapCodec<JotunheimRuneStoneBlock> CODEC = simpleCodec(JotunheimRuneStoneBlock::new);
	public static final BooleanProperty ACTIVE = BooleanProperty.create("active");

	public JotunheimRuneStoneBlock(BlockBehaviour.Properties properties) {
		super(properties);
		registerDefaultState(stateDefinition.any().setValue(ACTIVE, false));
	}

	@Override
	protected MapCodec<? extends Block> codec() {
		return CODEC;
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(ACTIVE);
	}

	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
		if (!(level instanceof ServerLevel server) || !(player instanceof ServerPlayer sp)) {
			return InteractionResult.SUCCESS;
		}
		if (state.getValue(ACTIVE)) {
			player.displayClientMessage(Component.translatable("message.projecthero.frost_giants.already").withStyle(ChatFormatting.AQUA), true);
			return InteractionResult.CONSUME;
		}
		if (player.isCreative() || player.isSpectator()) {
			player.displayClientMessage(Component.translatable("message.projecthero.frost_giants.survival_only").withStyle(ChatFormatting.GRAY), true);
			return InteractionResult.CONSUME;
		}
		if (server.getDifficulty() == Difficulty.PEACEFUL) {
			player.displayClientMessage(Component.translatable("message.projecthero.frost_giants.peaceful").withStyle(ChatFormatting.GRAY), true);
			return InteractionResult.CONSUME;
		}
		if (!server.isNight()) {
			player.displayClientMessage(Component.translatable("message.projecthero.frost_giants.night_only").withStyle(ChatFormatting.GRAY), true);
			return InteractionResult.CONSUME;
		}
		if (FrostGiants.start(server, pos, sp) == null) {
			player.displayClientMessage(Component.translatable("message.projecthero.frost_giants.refused").withStyle(ChatFormatting.AQUA), true);
		}
		return InteractionResult.CONSUME;
	}

	/** While its assault runs nobody can mine it. */
	@Override
	protected float getDestroyProgress(BlockState state, Player player, BlockGetter level, BlockPos pos) {
		return state.getValue(ACTIVE) ? 0f : super.getDestroyProgress(state, player, level, pos);
	}

	@Override
	public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
		if (state.getValue(ACTIVE)) {
			for (int i = 0; i < 2; i++) {
				level.addParticle(ParticleTypes.SNOWFLAKE, pos.getX() + random.nextDouble(), pos.getY() + 1.05, pos.getZ() + random.nextDouble(),
						0, 0.08, 0);
			}
			if (random.nextInt(3) == 0) {
				level.addParticle(ParticleTypes.END_ROD, pos.getX() + 0.5, pos.getY() + 1.1, pos.getZ() + 0.5, 0, 0.12, 0);
			}
		} else if (random.nextInt(6) == 0) {
			level.addParticle(ParticleTypes.SNOWFLAKE, pos.getX() + random.nextDouble(), pos.getY() + 1.02, pos.getZ() + random.nextDouble(),
					0, 0.02, 0);
		}
	}

	public static void appendTooltip(ItemStack stack, Item.TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
		tooltip.add(Component.translatable("block.projecthero.jotunheim_rune_stone.hint").withStyle(ChatFormatting.AQUA));
		tooltip.add(Component.translatable("block.projecthero.jotunheim_rune_stone.hint2").withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
	}
}
