package com.projecthero.mod.network;

import com.projecthero.mod.ProjectHeroMod;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Server &rarr; client (v0.14.7): a Super Speed after-image effect for player {@code playerId}.
 * <ul>
 *   <li>{@link #KIND_STREAK}: a line of after-images from {@code (fx, fy, fz)} to {@code (tx, ty, tz)} -- the zip of a
 *       Blitz / Speed Sweep hop or the ring of a Speed Vortex -- fading over {@code life} ticks, in {@code rgb}.</li>
 * </ul>
 * Purely cosmetic; everything that matters happens on the server.
 */
public record SpeedStreakPayload(int playerId, int kind, double fx, double fy, double fz, double tx, double ty, double tz,
		float yaw, int rgb, int life) implements CustomPacketPayload {
	public static final int KIND_STREAK = 0;

	public static final CustomPacketPayload.Type<SpeedStreakPayload> TYPE =
			new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(ProjectHeroMod.MOD_ID, "speed_streak"));

	public static final StreamCodec<RegistryFriendlyByteBuf, SpeedStreakPayload> CODEC = StreamCodec.of(
			(buf, p) -> {
				buf.writeVarInt(p.playerId);
				buf.writeVarInt(p.kind);
				buf.writeDouble(p.fx);
				buf.writeDouble(p.fy);
				buf.writeDouble(p.fz);
				buf.writeDouble(p.tx);
				buf.writeDouble(p.ty);
				buf.writeDouble(p.tz);
				buf.writeFloat(p.yaw);
				buf.writeInt(p.rgb);
				buf.writeVarInt(p.life);
			},
			buf -> new SpeedStreakPayload(buf.readVarInt(), buf.readVarInt(), buf.readDouble(), buf.readDouble(),
					buf.readDouble(), buf.readDouble(), buf.readDouble(), buf.readDouble(), buf.readFloat(), buf.readInt(),
					buf.readVarInt()));

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
