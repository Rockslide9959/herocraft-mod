package com.projecthero.mod.network;

import com.projecthero.mod.ProjectHeroMod;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * v0.15.18: server &rarr; the shooter: a Punisher Weapon Ability (Shift+V with the rifle or sniper) has locked onto
 * {@code entityId} -- the client turns the camera onto it every frame and ignores mouse look until released
 * ({@code entityId} = -1). {@code scope}: raise the scope too (the sniper). The aim itself is decided server side.
 */
public record PunisherLockOnPayload(int entityId, boolean scope) implements CustomPacketPayload {
	public static final CustomPacketPayload.Type<PunisherLockOnPayload> TYPE =
			new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(ProjectHeroMod.MOD_ID, "punisher_lock_on"));

	public static final StreamCodec<RegistryFriendlyByteBuf, PunisherLockOnPayload> CODEC = StreamCodec.composite(
			ByteBufCodecs.VAR_INT, PunisherLockOnPayload::entityId,
			ByteBufCodecs.BOOL, PunisherLockOnPayload::scope,
			PunisherLockOnPayload::new);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
