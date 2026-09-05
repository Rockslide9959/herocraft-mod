package com.herocraft.mod.network;

import com.herocraft.mod.HeroCraftMod;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Client &rarr; server Punisher gestures issued from the "Your Power" info screen. Currently just
 * abandoning Vigilante Training; re-validated server-side.
 */
public record PunisherActionPayload(Action action) implements CustomPacketPayload {
	public enum Action {
		ABANDON_TRAINING
	}

	public static final CustomPacketPayload.Type<PunisherActionPayload> TYPE =
			new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(HeroCraftMod.MOD_ID, "punisher_action"));

	public static final StreamCodec<RegistryFriendlyByteBuf, PunisherActionPayload> CODEC = StreamCodec.composite(
			ByteBufCodecs.VAR_INT.map(i -> Action.values()[Math.floorMod(i, Action.values().length)], Action::ordinal),
			PunisherActionPayload::action,
			PunisherActionPayload::new);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
