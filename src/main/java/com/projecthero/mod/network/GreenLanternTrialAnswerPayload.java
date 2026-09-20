package com.projecthero.mod.network;

import com.projecthero.mod.ProjectHeroMod;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Client &rarr; server (v0.11.11): the winning player's answer to the Will Trial's "Are you afraid?"
 * prompt ({@link GreenLanternTrialPromptPayload}). {@code yes} bonds the ring and breaks the pedestal;
 * {@code no} declines (no ring, pedestal stays unclaimed for anyone else to attempt). The server
 * ({@code GreenLanternTrial#handleAnswer}) re-checks that the sender actually has a trial awaiting an
 * answer -- a modified client sending this unprompted gains nothing.
 */
public record GreenLanternTrialAnswerPayload(boolean yes) implements CustomPacketPayload {
	public static final CustomPacketPayload.Type<GreenLanternTrialAnswerPayload> TYPE = new CustomPacketPayload.Type<>(
			ResourceLocation.fromNamespaceAndPath(ProjectHeroMod.MOD_ID, "green_lantern_trial_answer"));

	public static final StreamCodec<RegistryFriendlyByteBuf, GreenLanternTrialAnswerPayload> CODEC = StreamCodec.composite(
			ByteBufCodecs.BOOL, GreenLanternTrialAnswerPayload::yes,
			GreenLanternTrialAnswerPayload::new);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
