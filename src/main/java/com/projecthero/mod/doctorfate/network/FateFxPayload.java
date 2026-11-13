package com.projecthero.mod.doctorfate.network;

import com.projecthero.mod.ProjectHeroMod;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Server to client (v0.17.3 Doctor Fate): an effect only one client draws. {@link #SIGHT}: Sight of Nabu for {@code ticks};
 * {@code data} is pairs of (entity id, {@link #SIGHT_CHAOS} / {@link #SIGHT_ORDER} / {@link #SIGHT_NEUTRAL}).
 */
public record FateFxPayload(int kind, int entity, int ticks, int[] data) implements CustomPacketPayload {
	public static final int SIGHT = 0;
	public static final int SIGHT_NEUTRAL = 0;
	public static final int SIGHT_CHAOS = 1;
	public static final int SIGHT_ORDER = 2;
	/**
	 * v0.17.3 the Tower of Fate mindscape: Nabu's answer; {@code data} = {reply ordinal, a, b, tier}
	 * ({@code DoctorFateMindscape.Reply}). A GREETING opens the screen.
	 */
	public static final int MIND_REPLY = 1;

	public static final CustomPacketPayload.Type<FateFxPayload> TYPE = new CustomPacketPayload.Type<>(ProjectHeroMod.id("doctor_fate_fx"));

	public static final StreamCodec<RegistryFriendlyByteBuf, FateFxPayload> CODEC = StreamCodec.of((buf, p) -> {
		buf.writeVarInt(p.kind);
		buf.writeVarInt(p.entity);
		buf.writeVarInt(p.ticks);
		buf.writeVarIntArray(p.data);
	}, buf -> new FateFxPayload(buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readVarIntArray(8192)));

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
