package com.projecthero.mod.raven.network;

import com.projecthero.mod.ProjectHeroMod;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Client to server (v0.17.3 Raven): the double-tap-Space flight toggle, a left click while Debris Storm blocks orbit her (fire one),
 * V tapped (cast the picked spell) and the Spell Wheel's pick ({@code arg} = the spell's ordinal). The server re-checks everything.
 */
public record RavenActionPayload(Action action, int arg) implements CustomPacketPayload {
	public enum Action {
		TOGGLE_FLIGHT,
		FIRE_DEBRIS,
		CAST_SPELL,
		PICK_SPELL
	}

	public RavenActionPayload(Action action) {
		this(action, 0);
	}

	public static final CustomPacketPayload.Type<RavenActionPayload> TYPE = new CustomPacketPayload.Type<>(ProjectHeroMod.id("raven_action"));

	public static final StreamCodec<RegistryFriendlyByteBuf, RavenActionPayload> CODEC = StreamCodec.of(
			(buf, p) -> {
				buf.writeVarInt(p.action.ordinal());
				buf.writeVarInt(p.arg);
			},
			buf -> new RavenActionPayload(Action.values()[Math.floorMod(buf.readVarInt(), Action.values().length)], buf.readVarInt()));

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
