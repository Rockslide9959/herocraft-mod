package com.projecthero.mod.doctorfate.network;

import com.projecthero.mod.ProjectHeroMod;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Server to client (v0.17.3 Doctor Fate): Nabu asks "Will you serve Order?" -- the Accept / Refuse screen. {@code pos} is the Tomb
 * pedestal the helmet rests on, or {@link BlockPos#ZERO} with {@code fromHand} for a Helmet of Fate held in the hand.
 */
public record FateServePayload(BlockPos pos, boolean fromHand) implements CustomPacketPayload {
	public static final CustomPacketPayload.Type<FateServePayload> TYPE = new CustomPacketPayload.Type<>(ProjectHeroMod.id("doctor_fate_serve"));

	public static final StreamCodec<RegistryFriendlyByteBuf, FateServePayload> CODEC = StreamCodec.of((buf, p) -> {
		buf.writeBlockPos(p.pos);
		buf.writeBoolean(p.fromHand);
	}, buf -> new FateServePayload(buf.readBlockPos(), buf.readBoolean()));

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
