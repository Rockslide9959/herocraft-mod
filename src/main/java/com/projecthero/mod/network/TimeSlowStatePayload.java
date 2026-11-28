package com.projecthero.mod.network;

import java.util.UUID;

import com.projecthero.mod.ProjectHeroMod;

import net.minecraft.core.UUIDUtil;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Server &rarr; client (v0.14.7): the Super Speed Time Slow started ({@code active}, cast by {@code caster}) or ended.
 * v0.17.7: per dimension -- a player is told about the slow in the dimension they stand in ({@code dimension}), on a
 * start / end there and whenever they join, change dimension or respawn. The caster's own client keeps running at 20 ticks
 * a second while the dimension's slowed tick rate slows everyone else's; every client in it draws the Time Slow screen
 * effect from it.
 */
public record TimeSlowStatePayload(boolean active, UUID caster, String dimension) implements CustomPacketPayload {
	private static final UUID NONE = new UUID(0L, 0L);

	public static final CustomPacketPayload.Type<TimeSlowStatePayload> TYPE =
			new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(ProjectHeroMod.MOD_ID, "time_slow_state"));

	public static final StreamCodec<RegistryFriendlyByteBuf, TimeSlowStatePayload> CODEC = StreamCodec.of(
			(buf, p) -> {
				buf.writeBoolean(p.active);
				UUIDUtil.STREAM_CODEC.encode(buf, p.caster == null ? NONE : p.caster);
				buf.writeUtf(p.dimension == null ? "" : p.dimension);
			},
			buf -> new TimeSlowStatePayload(buf.readBoolean(), UUIDUtil.STREAM_CODEC.decode(buf), buf.readUtf()));

	public static TimeSlowStatePayload off(String dimension) {
		return new TimeSlowStatePayload(false, NONE, dimension);
	}

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
