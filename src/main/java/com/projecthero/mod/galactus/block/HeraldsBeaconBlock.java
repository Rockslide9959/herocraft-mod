package com.projecthero.mod.galactus.block;

import java.util.List;

import com.mojang.serialization.MapCodec;
import com.projecthero.mod.galactus.Galactus;
import com.projecthero.mod.galactus.GalactusConfig;

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
 * v0.16.1: the <b>Herald's Beacon</b>. Place it and right-click it <b>at night</b>, in Survival (never on Peaceful), to send
 * a signal no Herald can ignore: Galactus: The Devourer begins, centred here. While it runs the beacon is {@link #ACTIVE}
 * (light 15, can't be mined) and marks the arena's centre; it becomes the reward chest when Galactus retreats, and burns
 * out if the world is consumed.
 */
public class HeraldsBeaconBlock extends Block {
	public static final MapCodec<HeraldsBeaconBlock> CODEC = simpleCodec(HeraldsBeaconBlock::new);
	public static final BooleanProperty ACTIVE = BooleanProperty.create("active");

	public HeraldsBeaconBlock(BlockBehaviour.Properties properties) {
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
			player.displayClientMessage(Component.translatable("message.projecthero.galactus.already").withStyle(ChatFormatting.LIGHT_PURPLE), true);
			return InteractionResult.CONSUME;
		}
		if (!GalactusConfig.trigger().enabled) {
			player.displayClientMessage(Component.translatable("message.projecthero.galactus.disabled").withStyle(ChatFormatting.GRAY), true);
			return InteractionResult.CONSUME;
		}
		if (player.isCreative() || player.isSpectator()) {
			player.displayClientMessage(Component.translatable("message.projecthero.galactus.survival_only").withStyle(ChatFormatting.GRAY), true);
			return InteractionResult.CONSUME;
		}
		if (server.getDifficulty() == Difficulty.PEACEFUL) {
			player.displayClientMessage(Component.translatable("message.projecthero.galactus.peaceful").withStyle(ChatFormatting.GRAY), true);
			return InteractionResult.CONSUME;
		}
		if (GalactusConfig.trigger().beaconNightOnly && !server.isNight()) {
			player.displayClientMessage(Component.translatable("message.projecthero.galactus.night_only").withStyle(ChatFormatting.GRAY), true);
			return InteractionResult.CONSUME;
		}
		if (Galactus.start(server, pos, sp, Galactus.Source.BEACON) == null) {
			player.displayClientMessage(Component.translatable("message.projecthero.galactus.refused").withStyle(ChatFormatting.LIGHT_PURPLE), true);
		}
		return InteractionResult.CONSUME;
	}

	@Override
	protected float getDestroyProgress(BlockState state, Player player, BlockGetter level, BlockPos pos) {
		return state.getValue(ACTIVE) ? 0f : super.getDestroyProgress(state, player, level, pos);
	}

	@Override
	public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
		if (state.getValue(ACTIVE)) {
			level.addParticle(ParticleTypes.END_ROD, pos.getX() + 0.5, pos.getY() + 1.1, pos.getZ() + 0.5, 0, 0.35, 0);
			if (random.nextInt(2) == 0) {
				level.addParticle(ParticleTypes.REVERSE_PORTAL, pos.getX() + random.nextDouble(), pos.getY() + 1.0, pos.getZ() + random.nextDouble(),
						0, 0.1, 0);
			}
		} else if (random.nextInt(5) == 0) {
			level.addParticle(ParticleTypes.REVERSE_PORTAL, pos.getX() + random.nextDouble(), pos.getY() + 1.02, pos.getZ() + random.nextDouble(),
					0, 0.03, 0);
		}
	}

	public static void appendTooltip(ItemStack stack, Item.TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
		tooltip.add(Component.translatable("block.projecthero.heralds_beacon.hint").withStyle(ChatFormatting.LIGHT_PURPLE));
		tooltip.add(Component.translatable("block.projecthero.heralds_beacon.hint2").withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
	}
}
