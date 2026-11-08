package com.projecthero.mod.blackpanther.network;

import com.projecthero.mod.ProjectHeroMod;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Client to server (v0.17.3): a tap of H, the suit out of the necklace or back into it; a 1.5 s hold, the necklace off / on. */
public record BlackPantherActionPayload(Action action) implements CustomPacketPayload {
	public enum Action {
		TOGGLE_SUIT,
		TOGGLE_NECKLACE
	}

	public static final CustomPacketPayload.Type<BlackPantherActionPayload> TYPE =
			new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(ProjectHeroMod.MOD_ID, "black_panther_action"));

	public static final StreamCodec<RegistryFriendlyByteBuf, BlackPantherActionPayload> CODEC = StreamCodec.composite(
			ByteBufCodecs.VAR_INT.map(i -> Action.values()[Math.floorMod(i, Action.values().length)], Action::ordinal),
			BlackPantherActionPayload::action,
			BlackPantherActionPayload::new);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
