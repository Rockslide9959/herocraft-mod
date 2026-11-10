package com.projecthero.mod.network;

import com.projecthero.mod.ProjectHeroMod;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * v0.17.3: client -> server, Undead right-clicked while his thrown head is still in the air: the body starts growing back now
 * instead of on touching a block. The server re-checks ({@code UndeadHeadThrow.regrowNow}).
 */
public record UndeadHeadThrowPayload() implements CustomPacketPayload {
	public static final UndeadHeadThrowPayload INSTANCE = new UndeadHeadThrowPayload();

	public static final CustomPacketPayload.Type<UndeadHeadThrowPayload> TYPE =
			new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(ProjectHeroMod.MOD_ID, "undead_head_throw_regrow"));

	public static final StreamCodec<RegistryFriendlyByteBuf, UndeadHeadThrowPayload> CODEC = StreamCodec.unit(INSTANCE);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
