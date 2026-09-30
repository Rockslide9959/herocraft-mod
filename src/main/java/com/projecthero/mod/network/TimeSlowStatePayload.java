package com.projecthero.mod.network;

import java.util.UUID;

import com.projecthero.mod.ProjectHeroMod;

import net.minecraft.core.UUIDUtil;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Server &rarr; every client (v0.14.7): the game-wide Super Speed Time Slow started ({@code active}, cast by
 * {@code caster}) or ended. The caster's own client keeps running at 20 ticks a second while the synced tick rate
 * slows everyone else's; every client draws the Time Slow screen effect from it.
 */
public record TimeSlowStatePayload(boolean active, UUID caster) implements CustomPacketPayload {
	private static final UUID NONE = new UUID(0L, 0L);

	public static final CustomPacketPayload.Type<TimeSlowStatePayload> TYPE =
			new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(ProjectHeroMod.MOD_ID, "time_slow_state"));

	public static final StreamCodec<RegistryFriendlyByteBuf, TimeSlowStatePayload> CODEC = StreamCodec.of(
			(buf, p) -> {
				buf.writeBoolean(p.active);
				UUIDUtil.STREAM_CODEC.encode(buf, p.caster == null ? NONE : p.caster);
			},
			buf -> new TimeSlowStatePayload(buf.readBoolean(), UUIDUtil.STREAM_CODEC.decode(buf)));

	public static TimeSlowStatePayload off() {
		return new TimeSlowStatePayload(false, NONE);
	}

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
