package com.projecthero.mod.network;

import com.projecthero.mod.ProjectHeroMod;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * v0.15.18: server &rarr; the shooter: the Assault Rifle's Shift+V Weapon Ability has locked onto {@code entityId} --
 * the client turns the camera onto it every frame and ignores mouse look until released ({@code entityId} = -1).
 * {@code entityId} = {@link #SCOPE_ONLY}: no lock at all, just raise the scope ({@code scope} true) -- the sniper's
 * Shift+V Steady Shot, where the player keeps the camera. The aim itself is always decided server side.
 */
public record PunisherLockOnPayload(int entityId, boolean scope) implements CustomPacketPayload {
	/** No camera lock: only raise (or, with {@code scope} false, lower) the scope. */
	public static final int SCOPE_ONLY = -2;

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
