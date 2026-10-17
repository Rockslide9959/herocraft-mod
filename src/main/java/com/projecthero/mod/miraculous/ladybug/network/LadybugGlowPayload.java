package com.projecthero.mod.miraculous.ladybug.network;

import java.util.List;

import com.projecthero.mod.ProjectHeroMod;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Server to client, sent ONLY to the Ladybug concerned (v0.16.6): the entities her Yo-yo Scan, Yo-yo Phone or Ladybug
 * Vision picked out, outlined red for {@code ticks}. Nothing is set on the entities themselves, so nobody else sees the
 * outlines (the v0.15.4 highlight privacy rule).
 */
public record LadybugGlowPayload(List<Integer> ids, int ticks) implements CustomPacketPayload {
	public static final CustomPacketPayload.Type<LadybugGlowPayload> TYPE =
			new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(ProjectHeroMod.MOD_ID, "ladybug_glow"));

	public static final StreamCodec<RegistryFriendlyByteBuf, LadybugGlowPayload> CODEC = StreamCodec.composite(
			ByteBufCodecs.VAR_INT.apply(ByteBufCodecs.list(1024)), LadybugGlowPayload::ids,
			ByteBufCodecs.VAR_INT, LadybugGlowPayload::ticks,
			LadybugGlowPayload::new);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
