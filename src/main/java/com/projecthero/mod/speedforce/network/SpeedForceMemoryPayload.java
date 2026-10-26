package com.projecthero.mod.speedforce.network;

import com.projecthero.mod.ProjectHeroMod;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Client to server (v0.17.1, Speed Force Memories): the Shift+N menu's memory buttons -- {@link #REMEMBER} the spot the
 * runner stands on, or {@link #FORGET} marked place {@code index}.
 */
public record SpeedForceMemoryPayload(int action, int index) implements CustomPacketPayload {
	public static final int REMEMBER = 0;
	public static final int FORGET = 1;
	/** v0.17.1 (user, the Auto-Courier): send the main-hand stack to marked place {@code index}. */
	public static final int SEND = 2;

	public static final CustomPacketPayload.Type<SpeedForceMemoryPayload> TYPE =
			new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(ProjectHeroMod.MOD_ID, "speed_force_memory"));

	public static final StreamCodec<RegistryFriendlyByteBuf, SpeedForceMemoryPayload> CODEC = StreamCodec.of(
			(buf, p) -> {
				buf.writeVarInt(p.action);
				buf.writeVarInt(p.index + 1);
			},
			buf -> new SpeedForceMemoryPayload(buf.readVarInt(), buf.readVarInt() - 1));

	@Override
	public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
