package com.projecthero.mod.beastboy.network;

import com.projecthero.mod.ProjectHeroMod;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Server to client (v0.17.3 Beast Boy): a one-off effect for this client. {@link #SHAKE}: a roar shakes the screen
 * ({@code strength}, {@code ticks}).
 */
public record BeastBoyFxPayload(int kind, float strength, int ticks) implements CustomPacketPayload {
	public static final int SHAKE = 0;

	public static final CustomPacketPayload.Type<BeastBoyFxPayload> TYPE = new CustomPacketPayload.Type<>(ProjectHeroMod.id("beast_boy_fx"));

	public static final StreamCodec<RegistryFriendlyByteBuf, BeastBoyFxPayload> CODEC = StreamCodec.of(
			(buf, p) -> {
				buf.writeVarInt(p.kind);
				buf.writeFloat(p.strength);
				buf.writeVarInt(p.ticks);
			},
			buf -> new BeastBoyFxPayload(buf.readVarInt(), buf.readFloat(), buf.readVarInt()));

	@Override
	public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
