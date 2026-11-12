package com.projecthero.mod.raven.network;

import com.projecthero.mod.ProjectHeroMod;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Client to server (v0.17.3 Raven): the double-tap-Space flight toggle, and a left click while Debris Storm blocks orbit her (fire
 * one). The server re-checks everything.
 */
public record RavenActionPayload(Action action) implements CustomPacketPayload {
	public enum Action {
		TOGGLE_FLIGHT,
		FIRE_DEBRIS
	}

	public static final CustomPacketPayload.Type<RavenActionPayload> TYPE = new CustomPacketPayload.Type<>(ProjectHeroMod.id("raven_action"));

	public static final StreamCodec<RegistryFriendlyByteBuf, RavenActionPayload> CODEC = StreamCodec.of(
			(buf, p) -> buf.writeVarInt(p.action.ordinal()),
			buf -> new RavenActionPayload(Action.values()[Math.floorMod(buf.readVarInt(), Action.values().length)]));

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
