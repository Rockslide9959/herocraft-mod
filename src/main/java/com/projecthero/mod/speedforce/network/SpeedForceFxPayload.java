package com.projecthero.mod.speedforce.network;

import com.projecthero.mod.ProjectHeroMod;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Server to client (v0.17.1): one Speed Force visual -- a gold bolt from (fx, fy, fz) to (tx, ty, tz) (the Lightning
 * Throw and its chain), the sonic boom ring at (fx, fy, fz), a line of after-images along a zip (Step, Rewind, the
 * Remnant), or the awakening's column of lightning. Drawn by {@code SpeedForceClient}.
 */
public record SpeedForceFxPayload(int playerId, int kind, double fx, double fy, double fz, double tx, double ty, double tz)
		implements CustomPacketPayload {
	public static final int BOLT = 0;
	public static final int BOOM = 1;
	public static final int STREAK = 2;
	public static final int AWAKEN = 3;
	public static final int SURGE = 4;

	public static final CustomPacketPayload.Type<SpeedForceFxPayload> TYPE =
			new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(ProjectHeroMod.MOD_ID, "speed_force_fx"));

	public static final StreamCodec<RegistryFriendlyByteBuf, SpeedForceFxPayload> CODEC = StreamCodec.of(
			(buf, p) -> {
				buf.writeVarInt(p.playerId);
				buf.writeVarInt(p.kind);
				buf.writeDouble(p.fx);
				buf.writeDouble(p.fy);
				buf.writeDouble(p.fz);
				buf.writeDouble(p.tx);
				buf.writeDouble(p.ty);
				buf.writeDouble(p.tz);
			},
			buf -> new SpeedForceFxPayload(buf.readVarInt(), buf.readVarInt(), buf.readDouble(), buf.readDouble(), buf.readDouble(),
					buf.readDouble(), buf.readDouble(), buf.readDouble()));

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
