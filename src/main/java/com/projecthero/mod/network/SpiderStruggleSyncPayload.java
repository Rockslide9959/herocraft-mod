package com.projecthero.mod.network;

import com.projecthero.mod.ProjectHeroMod;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * v0.16.4 server &rarr; the cocooned player only: how far their struggle to break out has got (0..1), so the struggle bar
 * shows the server's number. {@code good} is 1 when this update follows an accepted alternate press, -1 after a slip
 * (the same key twice), 0 for plain decay (or the bar going away), 2 when they just broke out.
 */
public record SpiderStruggleSyncPayload(float progress, int good) implements CustomPacketPayload {
	public static final CustomPacketPayload.Type<SpiderStruggleSyncPayload> TYPE =
			new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(ProjectHeroMod.MOD_ID, "spider_struggle_sync"));

	public static final StreamCodec<RegistryFriendlyByteBuf, SpiderStruggleSyncPayload> CODEC = StreamCodec.composite(
			ByteBufCodecs.FLOAT, SpiderStruggleSyncPayload::progress,
			ByteBufCodecs.VAR_INT, SpiderStruggleSyncPayload::good,
			SpiderStruggleSyncPayload::new);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
