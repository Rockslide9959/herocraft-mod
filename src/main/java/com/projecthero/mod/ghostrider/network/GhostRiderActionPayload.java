package com.projecthero.mod.ghostrider.network;

import com.projecthero.mod.ProjectHeroMod;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Client to server Ghost Rider requests that are not ability slots (v0.16.5): H (the change) and signing Mephisto's
 * Contract from its screen. Only requests -- the server checks everything.
 */
public record GhostRiderActionPayload(Action action) implements CustomPacketPayload {
	public enum Action {
		TOGGLE_FORM,
		SIGN_CONTRACT
	}

	public static final CustomPacketPayload.Type<GhostRiderActionPayload> TYPE =
			new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(ProjectHeroMod.MOD_ID, "ghost_rider_action"));

	public static final StreamCodec<RegistryFriendlyByteBuf, GhostRiderActionPayload> CODEC = StreamCodec.composite(
			ByteBufCodecs.VAR_INT.map(i -> Action.values()[Math.floorMod(i, Action.values().length)], Action::ordinal),
			GhostRiderActionPayload::action,
			GhostRiderActionPayload::new);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
