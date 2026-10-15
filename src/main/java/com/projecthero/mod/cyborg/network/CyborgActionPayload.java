package com.projecthero.mod.cyborg.network;

import com.projecthero.mod.ProjectHeroMod;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Client to server Cyborg requests that are not ability slots (v0.16.5): H (combat / civilian form). */
public record CyborgActionPayload(Action action) implements CustomPacketPayload {
	public enum Action {
		TOGGLE_FORM
	}

	public static final CustomPacketPayload.Type<CyborgActionPayload> TYPE =
			new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(ProjectHeroMod.MOD_ID, "cyborg_action"));

	public static final StreamCodec<RegistryFriendlyByteBuf, CyborgActionPayload> CODEC = StreamCodec.composite(
			ByteBufCodecs.VAR_INT.map(i -> Action.values()[Math.floorMod(i, Action.values().length)], Action::ordinal),
			CyborgActionPayload::action,
			CyborgActionPayload::new);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
