package com.projecthero.mod.bluebeetle.network;

import com.projecthero.mod.ProjectHeroMod;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Client to server Blue Beetle requests that are not ability slots (v0.16.5): H (the armour on / off). */
public record BlueBeetleActionPayload(Action action) implements CustomPacketPayload {
	public enum Action {
		TOGGLE_SUIT
	}

	public static final CustomPacketPayload.Type<BlueBeetleActionPayload> TYPE =
			new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(ProjectHeroMod.MOD_ID, "blue_beetle_action"));

	public static final StreamCodec<RegistryFriendlyByteBuf, BlueBeetleActionPayload> CODEC = StreamCodec.composite(
			ByteBufCodecs.VAR_INT.map(i -> Action.values()[Math.floorMod(i, Action.values().length)], Action::ordinal),
			BlueBeetleActionPayload::action,
			BlueBeetleActionPayload::new);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
