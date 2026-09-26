package com.projecthero.mod.network;

import com.projecthero.mod.ProjectHeroMod;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Client to server All Might requests that are not one of the six ability slots: H (transform / change back) and N
 * (All Might Leap). Only requests -- the server checks the power, the cooldown, the OFA and the current state.
 */
public record AllMightActionPayload(Action action) implements CustomPacketPayload {
	public enum Action {
		TOGGLE_FORM
	}

	public static final CustomPacketPayload.Type<AllMightActionPayload> TYPE =
			new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(ProjectHeroMod.MOD_ID, "all_might_action"));

	public static final StreamCodec<RegistryFriendlyByteBuf, AllMightActionPayload> CODEC = StreamCodec.composite(
			ByteBufCodecs.VAR_INT.map(i -> Action.values()[Math.floorMod(i, Action.values().length)], Action::ordinal),
			AllMightActionPayload::action,
			AllMightActionPayload::new);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
