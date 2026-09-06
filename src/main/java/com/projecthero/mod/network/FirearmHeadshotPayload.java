package com.projecthero.mod.network;

import com.projecthero.mod.ProjectHeroMod;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Server &rarr; the shooter: the last shot landed a headshot. The client flashes a small "HEADSHOT"
 * cue near the crosshair (spec section 8 -- subtle, no screen clutter).
 */
public record FirearmHeadshotPayload() implements CustomPacketPayload {
	public static final FirearmHeadshotPayload INSTANCE = new FirearmHeadshotPayload();

	public static final CustomPacketPayload.Type<FirearmHeadshotPayload> TYPE =
			new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(ProjectHeroMod.MOD_ID, "firearm_headshot"));

	public static final StreamCodec<RegistryFriendlyByteBuf, FirearmHeadshotPayload> CODEC =
			StreamCodec.unit(INSTANCE);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
