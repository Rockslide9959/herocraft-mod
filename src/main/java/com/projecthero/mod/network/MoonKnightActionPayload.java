package com.projecthero.mod.network;

import com.projecthero.mod.ProjectHeroMod;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * v0.13.19: client to server Moon Knight requests that are not one of the six ability slots -- H (suit on / off),
 * the alter radial picker's choice ({@code arg} = alter ordinal) and (v0.13.21) the Cape Block's right-click press /
 * release. Only requests: the server re-checks everything.
 */
public record MoonKnightActionPayload(Action action, int arg) implements CustomPacketPayload {
	public enum Action {
		TOGGLE_SUIT,
		SELECT_ALTER,
		CAPE_BLOCK_START,
		CAPE_BLOCK_STOP
	}

	public static final CustomPacketPayload.Type<MoonKnightActionPayload> TYPE =
			new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(ProjectHeroMod.MOD_ID, "moon_knight_action"));

	public static final StreamCodec<RegistryFriendlyByteBuf, MoonKnightActionPayload> CODEC = StreamCodec.composite(
			ByteBufCodecs.VAR_INT.map(i -> Action.values()[Math.floorMod(i, Action.values().length)], Action::ordinal),
			MoonKnightActionPayload::action,
			ByteBufCodecs.VAR_INT,
			MoonKnightActionPayload::arg,
			MoonKnightActionPayload::new);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
