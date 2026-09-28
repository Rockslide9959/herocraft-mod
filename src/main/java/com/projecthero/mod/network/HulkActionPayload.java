package com.projecthero.mod.network;

import com.projecthero.mod.ProjectHeroMod;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Client to server Hulk requests that are not one of the six ability slots: H (let the Hulk out at 75+ rage).
 * Only a request -- the server checks the Gamma power, the rage and the current state.
 */
public record HulkActionPayload(Action action) implements CustomPacketPayload {
	public enum Action {
		TRANSFORM
	}

	public static final CustomPacketPayload.Type<HulkActionPayload> TYPE =
			new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(ProjectHeroMod.MOD_ID, "hulk_action"));

	public static final StreamCodec<RegistryFriendlyByteBuf, HulkActionPayload> CODEC = StreamCodec.composite(
			ByteBufCodecs.VAR_INT.map(i -> Action.values()[Math.floorMod(i, Action.values().length)], Action::ordinal),
			HulkActionPayload::action,
			HulkActionPayload::new);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
