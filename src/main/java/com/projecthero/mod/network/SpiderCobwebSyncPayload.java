package com.projecthero.mod.network;

import com.projecthero.mod.ProjectHeroMod;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * v0.16.1 server &rarr; Spider-Man and his online squadmates: these cobweb positions (packed {@code BlockPos}) in your
 * current level are his ({@code add}) or are gone ({@code !add}), so your client lets you through them like the server
 * does.
 */
public record SpiderCobwebSyncPayload(boolean add, long[] positions) implements CustomPacketPayload {
	public static final CustomPacketPayload.Type<SpiderCobwebSyncPayload> TYPE =
			new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(ProjectHeroMod.MOD_ID, "spider_cobweb_sync"));

	public static final StreamCodec<RegistryFriendlyByteBuf, SpiderCobwebSyncPayload> CODEC = StreamCodec.of(
			(buf, p) -> {
				buf.writeBoolean(p.add());
				buf.writeLongArray(p.positions());
			},
			buf -> new SpiderCobwebSyncPayload(buf.readBoolean(), buf.readLongArray()));

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
