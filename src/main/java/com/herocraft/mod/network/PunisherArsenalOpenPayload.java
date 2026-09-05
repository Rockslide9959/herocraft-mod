package com.herocraft.mod.network;

import com.herocraft.mod.HeroCraftMod;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Server &rarr; client: open the Arsenal weapon wheel. The client reads the list of available
 * weapons from the synced {@link com.herocraft.mod.punisher.data.PunisherState}, so no data travels.
 */
public record PunisherArsenalOpenPayload() implements CustomPacketPayload {
	public static final PunisherArsenalOpenPayload INSTANCE = new PunisherArsenalOpenPayload();

	public static final CustomPacketPayload.Type<PunisherArsenalOpenPayload> TYPE =
			new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(HeroCraftMod.MOD_ID, "punisher_arsenal_open"));

	public static final StreamCodec<RegistryFriendlyByteBuf, PunisherArsenalOpenPayload> CODEC =
			StreamCodec.unit(INSTANCE);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
