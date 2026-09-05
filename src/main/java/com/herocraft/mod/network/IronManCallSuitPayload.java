package com.herocraft.mod.network;

import com.herocraft.mod.HeroCraftMod;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Client &rarr; server: the player picked a suit from the call-armour screen. Re-validated
 * server-side by {@link com.herocraft.mod.ironman.suit.IronManSuitCall}.
 */
public record IronManCallSuitPayload(String suitId, int source) implements CustomPacketPayload {
	public static final CustomPacketPayload.Type<IronManCallSuitPayload> TYPE =
			new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(HeroCraftMod.MOD_ID, "iron_man_call_suit"));

	public static final StreamCodec<RegistryFriendlyByteBuf, IronManCallSuitPayload> CODEC = StreamCodec.composite(
			ByteBufCodecs.STRING_UTF8, IronManCallSuitPayload::suitId,
			ByteBufCodecs.VAR_INT, IronManCallSuitPayload::source,
			IronManCallSuitPayload::new);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
