package com.projecthero.mod.miraculous.hawkmoth.network;

import java.util.List;

import com.projecthero.mod.ProjectHeroMod;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Server to client, ONLY to the Hawk Moth using Emotion Sense (v0.16.6): every creature in range and the colour of its
 * mood (RGB), outlined through walls for {@code ticks}. Nothing is set on the creatures (the v0.15.4 privacy rule).
 */
public record HawkMothSensePayload(List<Integer> ids, List<Integer> colors, int ticks) implements CustomPacketPayload {
	public static final CustomPacketPayload.Type<HawkMothSensePayload> TYPE =
			new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(ProjectHeroMod.MOD_ID, "hawk_moth_sense"));

	public static final StreamCodec<RegistryFriendlyByteBuf, HawkMothSensePayload> CODEC = StreamCodec.composite(
			ByteBufCodecs.VAR_INT.apply(ByteBufCodecs.list(1024)), HawkMothSensePayload::ids,
			ByteBufCodecs.INT.apply(ByteBufCodecs.list(1024)), HawkMothSensePayload::colors,
			ByteBufCodecs.VAR_INT, HawkMothSensePayload::ticks,
			HawkMothSensePayload::new);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
