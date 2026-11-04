package com.projecthero.mod.galactus.cinematic;

import com.projecthero.mod.ProjectHeroMod;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * v0.17.2 Galactus raid v2, server -> client: play cutscene {@code scene} (a {@link GalactusScenes.Scene} ordinal; -1
 * stops whatever is playing) anchored at ({@code x, y, z}) facing {@code yaw}, already {@code elapsed} ticks in (a late
 * join), with the raid's entity id {@code bossId} (the colossus the camera follows, or -1) and a free {@code extra}.
 */
public record CinematicPayload(int scene, double x, double y, double z, float yaw, int elapsed, int bossId, float extra) implements CustomPacketPayload {
	public static final Type<CinematicPayload> TYPE = new Type<>(ProjectHeroMod.id("galactus_cinematic"));

	public static final StreamCodec<RegistryFriendlyByteBuf, CinematicPayload> CODEC = StreamCodec.of(
			(buf, p) -> {
				buf.writeVarInt(p.scene + 1);
				buf.writeDouble(p.x);
				buf.writeDouble(p.y);
				buf.writeDouble(p.z);
				buf.writeFloat(p.yaw);
				buf.writeVarInt(p.elapsed);
				buf.writeVarInt(p.bossId + 1);
				buf.writeFloat(p.extra);
			},
			buf -> new CinematicPayload(buf.readVarInt() - 1, buf.readDouble(), buf.readDouble(), buf.readDouble(), buf.readFloat(), buf.readVarInt(),
					buf.readVarInt() - 1, buf.readFloat()));

	public static CinematicPayload stop() {
		return new CinematicPayload(-1, 0, 0, 0, 0f, 0, -1, 0f);
	}

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
