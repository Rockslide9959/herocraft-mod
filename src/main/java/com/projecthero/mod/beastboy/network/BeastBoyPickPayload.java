package com.projecthero.mod.beastboy.network;

import com.projecthero.mod.ProjectHeroMod;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Client to server (v0.17.3 Beast Boy): the V Animal Wheel's pick -- a {@code BeastForm} ordinal (0 = back to human). */
public record BeastBoyPickPayload(int form) implements CustomPacketPayload {
	public static final CustomPacketPayload.Type<BeastBoyPickPayload> TYPE = new CustomPacketPayload.Type<>(ProjectHeroMod.id("beast_boy_pick"));

	public static final StreamCodec<RegistryFriendlyByteBuf, BeastBoyPickPayload> CODEC = StreamCodec.of(
			(buf, p) -> buf.writeVarInt(p.form),
			buf -> new BeastBoyPickPayload(buf.readVarInt()));

	@Override
	public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
