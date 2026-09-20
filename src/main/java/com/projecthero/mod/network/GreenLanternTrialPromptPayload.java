package com.projecthero.mod.network;

import com.projecthero.mod.ProjectHeroMod;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Server &rarr; client (v0.11.11): the Will Trial's three waves are cleared -- open the "Are you
 * afraid?" confirmation on the winning player's screen. No data travels; the prompt text is fixed.
 */
public record GreenLanternTrialPromptPayload() implements CustomPacketPayload {
	public static final GreenLanternTrialPromptPayload INSTANCE = new GreenLanternTrialPromptPayload();

	public static final CustomPacketPayload.Type<GreenLanternTrialPromptPayload> TYPE = new CustomPacketPayload.Type<>(
			ResourceLocation.fromNamespaceAndPath(ProjectHeroMod.MOD_ID, "green_lantern_trial_prompt"));

	public static final StreamCodec<RegistryFriendlyByteBuf, GreenLanternTrialPromptPayload> CODEC =
			StreamCodec.unit(INSTANCE);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
