package com.projecthero.mod.network;

import com.projecthero.mod.ProjectHeroMod;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Client &rarr; server firearm gestures that are not "pull the trigger": reload, raise / lower the
 * sights, and cycle the sniper scope's zoom. All edge-triggered and re-validated server-side.
 */
public record FirearmActionPayload(Action action) implements CustomPacketPayload {
	public enum Action {
		RELOAD,
		AIM_START,
		AIM_STOP,
		CYCLE_ZOOM
	}

	public static final CustomPacketPayload.Type<FirearmActionPayload> TYPE =
			new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(ProjectHeroMod.MOD_ID, "firearm_action"));

	public static final StreamCodec<RegistryFriendlyByteBuf, FirearmActionPayload> CODEC = StreamCodec.composite(
			ByteBufCodecs.VAR_INT.map(i -> Action.values()[Math.floorMod(i, Action.values().length)], Action::ordinal),
			FirearmActionPayload::action,
			FirearmActionPayload::new);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
