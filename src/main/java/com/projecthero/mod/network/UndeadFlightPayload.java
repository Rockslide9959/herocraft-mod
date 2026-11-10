package com.projecthero.mod.network;

import com.projecthero.mod.ProjectHeroMod;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * v0.17.3: client -> server, Undead double-tapped jump in the air: take off on his blood jets, or drop out of the flight. The
 * server re-checks everything ({@code UndeadAbilityManager.doubleJump}).
 */
public record UndeadFlightPayload() implements CustomPacketPayload {
	public static final CustomPacketPayload.Type<UndeadFlightPayload> TYPE =
			new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(ProjectHeroMod.MOD_ID, "undead_flight"));

	public static final StreamCodec<RegistryFriendlyByteBuf, UndeadFlightPayload> CODEC = StreamCodec.unit(new UndeadFlightPayload());

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
