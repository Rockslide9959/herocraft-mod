package com.projecthero.mod.miraculous.hawkmoth.network;

import com.projecthero.mod.ProjectHeroMod;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Server to client, to the Hawk Moth alone (v0.16.6): Akuma Link with a Champion player -- open the chat box ready with
 * {@code /hawkmoth link } so what he types goes straight to {@code champion} (and no one else).
 */
public record HawkMothLinkPayload(String champion) implements CustomPacketPayload {
	public static final CustomPacketPayload.Type<HawkMothLinkPayload> TYPE =
			new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(ProjectHeroMod.MOD_ID, "hawk_moth_link"));

	public static final StreamCodec<RegistryFriendlyByteBuf, HawkMothLinkPayload> CODEC = StreamCodec.composite(
			ByteBufCodecs.STRING_UTF8, HawkMothLinkPayload::champion,
			HawkMothLinkPayload::new);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
