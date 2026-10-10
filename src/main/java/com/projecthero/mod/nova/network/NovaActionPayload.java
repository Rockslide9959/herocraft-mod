package com.projecthero.mod.nova.network;

import com.projecthero.mod.ProjectHeroMod;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Client to server Nova requests that are not one of the ability slots (v0.15.13): H (the uniform on / off) and the
 * double-tap-jump flight toggle. Only a request -- the server checks the power and the suit.
 */
public record NovaActionPayload(Action action) implements CustomPacketPayload {
	public enum Action {
		TOGGLE_SUIT,
		TOGGLE_FLIGHT,
		/** v0.15.21: Shift + N pressed / released -- hold it 5 s to take the helmet off (the power goes, the item comes back). */
		HELMET_REMOVE_START,
		HELMET_REMOVE_STOP
	}

	public static final CustomPacketPayload.Type<NovaActionPayload> TYPE =
			new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(ProjectHeroMod.MOD_ID, "nova_action"));

	public static final StreamCodec<RegistryFriendlyByteBuf, NovaActionPayload> CODEC = StreamCodec.composite(
			ByteBufCodecs.VAR_INT.map(i -> Action.values()[Math.floorMod(i, Action.values().length)], Action::ordinal),
			NovaActionPayload::action,
			NovaActionPayload::new);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
