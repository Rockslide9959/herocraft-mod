package com.projecthero.mod.quirk.darkshadow;

import com.projecthero.mod.ProjectHeroMod;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * v0.16.7 (user) "make pressing N hide dark shadow": client to server, N pressed by a Dark Shadow holder (no mutation
 * selected). Only a request -- the server re-checks everything in {@link DarkShadowMoves#toggleHidden}.
 */
public record DarkShadowHidePayload() implements CustomPacketPayload {
	public static final DarkShadowHidePayload INSTANCE = new DarkShadowHidePayload();
	public static final CustomPacketPayload.Type<DarkShadowHidePayload> TYPE =
			new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(ProjectHeroMod.MOD_ID, "dark_shadow_hide"));
	public static final StreamCodec<ByteBuf, DarkShadowHidePayload> CODEC = StreamCodec.unit(INSTANCE);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
