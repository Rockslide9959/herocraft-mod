package com.projecthero.mod.blackpanther.network;

import com.projecthero.mod.ProjectHeroMod;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * v0.17.7: client -> server, H as the Black Panther wearing (or carrying) the necklace: the suit out of it, or back in. The same
 * shape as the Flash Ring's {@code FlashRing.TogglePayload}; re-validated on the server ({@code BlackPanther.toggleSuit}).
 */
public record BlackPantherSuitPayload() implements CustomPacketPayload {
	public static final CustomPacketPayload.Type<BlackPantherSuitPayload> TYPE = new CustomPacketPayload.Type<>(ProjectHeroMod.id("black_panther_suit_toggle"));
	public static final StreamCodec<RegistryFriendlyByteBuf, BlackPantherSuitPayload> CODEC = StreamCodec.unit(new BlackPantherSuitPayload());

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
