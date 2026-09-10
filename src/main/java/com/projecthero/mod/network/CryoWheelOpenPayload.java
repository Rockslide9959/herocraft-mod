package com.projecthero.mod.network;

import com.projecthero.mod.ProjectHeroMod;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Server &rarr; client: the cryokinetic held Sneak + R for 2 seconds -- open the ice-weapon wheel.
 * No data travels; the wheel's choices are fixed.
 */
public record CryoWheelOpenPayload() implements CustomPacketPayload {
	public static final CryoWheelOpenPayload INSTANCE = new CryoWheelOpenPayload();

	public static final CustomPacketPayload.Type<CryoWheelOpenPayload> TYPE =
			new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(ProjectHeroMod.MOD_ID, "cryo_wheel_open"));

	public static final StreamCodec<RegistryFriendlyByteBuf, CryoWheelOpenPayload> CODEC =
			StreamCodec.unit(INSTANCE);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
