package com.projecthero.mod.doctorfate.network;

import com.projecthero.mod.ProjectHeroMod;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Server to client (v0.17.3 Doctor Fate): open one of Nabu's bargains on the small two-button screen. {@code kind} is a
 * {@code FateState.Bargain} kind; {@code a}/{@code b}/{@code c} are its terms (Death's door: Dominion, shield health; Order's
 * command: creatures, Dominion off on success, Dominion on failure; Forbidden spell: the Dominion floor); {@code refusals} today's
 * refusals (the screen says when he's being meaner).
 */
public record FateBargainPayload(int kind, int seconds, float a, float b, float c, int refusals) implements CustomPacketPayload {
	public static final CustomPacketPayload.Type<FateBargainPayload> TYPE = new CustomPacketPayload.Type<>(ProjectHeroMod.id("doctor_fate_bargain"));

	public static final StreamCodec<RegistryFriendlyByteBuf, FateBargainPayload> CODEC = StreamCodec.of((buf, p) -> {
		buf.writeVarInt(p.kind);
		buf.writeVarInt(p.seconds);
		buf.writeFloat(p.a);
		buf.writeFloat(p.b);
		buf.writeFloat(p.c);
		buf.writeVarInt(p.refusals);
	}, buf -> new FateBargainPayload(buf.readVarInt(), buf.readVarInt(), buf.readFloat(), buf.readFloat(), buf.readFloat(), buf.readVarInt()));

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
