package com.projecthero.mod.falcon.network;

import com.projecthero.mod.ProjectHeroMod;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Client to server Falcon flight requests that are not ability slots (v0.17.1): the jump double-tap (take off / drop out).
 * The jet burn is X held (an ability slot), so it needs no payload of its own.
 */
public record FalconActionPayload(Action action) implements CustomPacketPayload {
	public enum Action {
		TOGGLE_FLIGHT
	}

	public static final CustomPacketPayload.Type<FalconActionPayload> TYPE =
			new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(ProjectHeroMod.MOD_ID, "falcon_action"));

	public static final StreamCodec<RegistryFriendlyByteBuf, FalconActionPayload> CODEC = StreamCodec.composite(
			ByteBufCodecs.VAR_INT.map(i -> Action.values()[Math.floorMod(i, Action.values().length)], Action::ordinal),
			FalconActionPayload::action,
			FalconActionPayload::new);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
