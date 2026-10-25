package com.projecthero.mod.silversurfer.network;

import com.projecthero.mod.ProjectHeroMod;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Client to server Silver Surfer requests that are not ability slots (v0.17.1): the jump double-tap (the board flies up / drops away). */
public record SilverSurferActionPayload(Action action) implements CustomPacketPayload {
	public enum Action {
		TOGGLE_SURF
	}

	public static final CustomPacketPayload.Type<SilverSurferActionPayload> TYPE =
			new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(ProjectHeroMod.MOD_ID, "silver_surfer_action"));

	public static final StreamCodec<RegistryFriendlyByteBuf, SilverSurferActionPayload> CODEC = StreamCodec.composite(
			ByteBufCodecs.VAR_INT.map(i -> Action.values()[Math.floorMod(i, Action.values().length)], Action::ordinal),
			SilverSurferActionPayload::action,
			SilverSurferActionPayload::new);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
