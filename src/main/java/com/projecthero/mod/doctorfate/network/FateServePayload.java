package com.projecthero.mod.doctorfate.network;

import com.projecthero.mod.ProjectHeroMod;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Server to client (v0.17.3 Doctor Fate): Nabu asks "Will you serve Order?" -- the Accept / Refuse screen. {@code pos} is the Tomb
 * pedestal the helmet rests on (the helmet is no item: accepting grants the power, and H wears it from then on).
 */
public record FateServePayload(BlockPos pos) implements CustomPacketPayload {
	public static final CustomPacketPayload.Type<FateServePayload> TYPE = new CustomPacketPayload.Type<>(ProjectHeroMod.id("doctor_fate_serve"));

	public static final StreamCodec<RegistryFriendlyByteBuf, FateServePayload> CODEC = StreamCodec.of((buf, p) -> {
		buf.writeBlockPos(p.pos);
	}, buf -> new FateServePayload(buf.readBlockPos()));

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
