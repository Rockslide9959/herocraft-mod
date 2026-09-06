package com.projecthero.mod.network;

import com.projecthero.mod.ProjectHeroMod;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Server &rarr; client: Steel has spotted a hostile projectile closing on the player from outside
 * their view. Carries the yaw (degrees, world-relative) toward the threat so the HUD can draw a brief
 * directional marker. Purely informational -- the server never dodges or catches anything for the
 * player (that is the Spider-Man power's job, not this one).
 */
public record MaxSteelWarningPayload(float yawToThreat) implements CustomPacketPayload {
	public static final CustomPacketPayload.Type<MaxSteelWarningPayload> TYPE =
			new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(ProjectHeroMod.MOD_ID, "max_steel_warning"));

	public static final StreamCodec<RegistryFriendlyByteBuf, MaxSteelWarningPayload> CODEC = StreamCodec.composite(
			ByteBufCodecs.FLOAT, MaxSteelWarningPayload::yawToThreat, MaxSteelWarningPayload::new);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
