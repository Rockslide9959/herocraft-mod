package com.projecthero.mod.alarm.network;

import java.util.List;

import com.projecthero.mod.ProjectHeroMod;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * v0.17.7, server to one player every 4 ticks while they are in an alarm zone (and once with {@code state = -1} when they
 * leave): the zone's state for the HUD eye, the guards near them that show a "?" ({@code id * 4 + 1}) or "!"
 * ({@code id * 4 + 2}), and the Snipers' laser lines as {@code [sniperId, targetId, bright]} triples.
 */
public record AlarmStatusPayload(int state, List<Integer> marks, List<Integer> lasers) implements CustomPacketPayload {
	public static final int NONE = -1;

	public static final CustomPacketPayload.Type<AlarmStatusPayload> TYPE = new CustomPacketPayload.Type<>(ProjectHeroMod.id("alarm_status"));

	public static final StreamCodec<RegistryFriendlyByteBuf, AlarmStatusPayload> CODEC = StreamCodec.composite(
			ByteBufCodecs.VAR_INT, AlarmStatusPayload::state,
			ByteBufCodecs.VAR_INT.apply(ByteBufCodecs.list(256)), AlarmStatusPayload::marks,
			ByteBufCodecs.VAR_INT.apply(ByteBufCodecs.list(96)), AlarmStatusPayload::lasers,
			AlarmStatusPayload::new);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
