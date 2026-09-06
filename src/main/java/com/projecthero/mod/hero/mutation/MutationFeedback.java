package com.projecthero.mod.hero.mutation;

import com.projecthero.mod.hero.Power;

import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;

/** All player-facing feedback for the mutation flow, in one place. */
public final class MutationFeedback {
	private MutationFeedback() {
	}

	public static void serumTookHold(ServerPlayer player, Power power) {
		player.displayClientMessage(Component.translatable("message.projecthero.mutation.serum_active",
				Component.translatable(power.nameKey())).withStyle(ChatFormatting.LIGHT_PURPLE), true);
		player.level().playSound(null, player.blockPosition(), SoundEvents.BREWING_STAND_BREW, SoundSource.PLAYERS, 0.7f, 0.7f);
	}

	public static void serumFaded(ServerPlayer player) {
		player.displayClientMessage(Component.translatable("message.projecthero.mutation.serum_faded")
				.withStyle(ChatFormatting.GRAY), true);
	}

	public static void exposureSurvived(ServerPlayer player, Power power) {
		player.displayClientMessage(Component.translatable("message.projecthero.mutation.exposure_survived")
				.withStyle(ChatFormatting.GOLD), true);
	}

	public static void mutationConfirmed(ServerPlayer player, Power power) {
		player.connection.send(new ClientboundSetTitlesAnimationPacket(10, 60, 20));
		player.connection.send(new ClientboundSetTitleTextPacket(
				Component.translatable("message.projecthero.mutation.detected").withStyle(ChatFormatting.DARK_PURPLE, ChatFormatting.BOLD)));
		player.connection.send(new ClientboundSetSubtitleTextPacket(
				Component.translatable(power.nameKey()).withStyle(ChatFormatting.LIGHT_PURPLE)));

		player.sendSystemMessage(Component.empty());
		player.sendSystemMessage(Component.translatable("message.projecthero.mutation.unlocked",
				Component.translatable(power.nameKey())).withStyle(ChatFormatting.LIGHT_PURPLE));
		player.sendSystemMessage(Component.translatable("message.projecthero.mutation.select_hint")
				.withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));

		if (player.level() instanceof ServerLevel level) {
			level.playSound(null, player.blockPosition(), SoundEvents.TOTEM_USE, SoundSource.PLAYERS, 0.8f, 0.6f);
			level.playSound(null, player.blockPosition(), SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.9f, 0.5f);
			level.sendParticles(ParticleTypes.WITCH, player.getX(), player.getY() + 1.0, player.getZ(), 60, 0.5, 1.0, 0.5, 0.2);
			level.sendParticles(ParticleTypes.END_ROD, player.getX(), player.getY() + 1.0, player.getZ(), 40, 0.4, 0.8, 0.4, 0.1);
		}
	}

	public static void capacityFull(ServerPlayer player) {
		player.displayClientMessage(Component.translatable("message.projecthero.mutation.capacity_full")
				.withStyle(ChatFormatting.RED), true);
	}

	public static void researchFound(ServerPlayer player, Power power) {
		player.sendSystemMessage(Component.translatable("message.projecthero.research.found",
				Component.translatable(power.nameKey())).withStyle(ChatFormatting.AQUA));
	}

	public static void researchAlreadyKnown(ServerPlayer player, Power power) {
		player.displayClientMessage(Component.translatable("message.projecthero.research.already_known",
				Component.translatable(power.nameKey())).withStyle(ChatFormatting.GRAY), true);
	}
}
