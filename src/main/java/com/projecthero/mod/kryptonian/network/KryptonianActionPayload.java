package com.projecthero.mod.kryptonian.network;

import com.projecthero.mod.ProjectHeroMod;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Client to server Kryptonian requests that are not one of the ability slots (v0.14.8): the double-tap-jump flight
 * toggle. Only a request -- the server checks the power, kryptonite and the burn-out.
 */
public record KryptonianActionPayload(Action action) implements CustomPacketPayload {
	public enum Action {
		TOGGLE_FLIGHT
	}

	public static final CustomPacketPayload.Type<KryptonianActionPayload> TYPE =
			new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(ProjectHeroMod.MOD_ID, "kryptonian_action"));

	public static final StreamCodec<RegistryFriendlyByteBuf, KryptonianActionPayload> CODEC = StreamCodec.composite(
			ByteBufCodecs.VAR_INT.map(i -> Action.values()[Math.floorMod(i, Action.values().length)], Action::ordinal),
			KryptonianActionPayload::action,
			KryptonianActionPayload::new);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
