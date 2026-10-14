package com.projecthero.mod.network;

import com.projecthero.mod.ProjectHeroMod;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * v0.16.2: client -&gt; server, the Spider-Man Suit v2's Web Wings glide starting ({@code true}) or ending. Sent on the
 * edge only; the server re-checks the glide rules ({@code SpiderWebWings.canGlide}) and owns the synced flag.
 */
public record SpiderGlidePayload(boolean gliding) implements CustomPacketPayload {
	public static final CustomPacketPayload.Type<SpiderGlidePayload> TYPE =
			new CustomPacketPayload.Type<>(ProjectHeroMod.id("spider_glide"));

	public static final StreamCodec<RegistryFriendlyByteBuf, SpiderGlidePayload> CODEC = StreamCodec.composite(
			ByteBufCodecs.BOOL, SpiderGlidePayload::gliding, SpiderGlidePayload::new);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
