package com.projecthero.mod.network;

import com.projecthero.mod.ProjectHeroMod;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Server &rarr; the shooter: a shot the server accepted. Carries the vertical camera kick so the
 * client applies recoil to its own view smoothly (and only for shots that actually happened -- the
 * client never kicks on a shot the server refused for ammo / rate).
 */
public record FirearmShotPayload(float verticalKick) implements CustomPacketPayload {
	public static final CustomPacketPayload.Type<FirearmShotPayload> TYPE =
			new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(ProjectHeroMod.MOD_ID, "firearm_shot"));

	public static final StreamCodec<RegistryFriendlyByteBuf, FirearmShotPayload> CODEC = StreamCodec.composite(
			ByteBufCodecs.FLOAT, FirearmShotPayload::verticalKick,
			FirearmShotPayload::new);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
