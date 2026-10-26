package com.projecthero.mod.speedforce.network;

import com.projecthero.mod.ProjectHeroMod;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Client to server (v0.17.1, Speed Build): whether the Speed Force Speedster is holding right-click with blocks in the off
 * hand -- {@link #NONE}, {@link #BRIDGE} (a bridge under the feet) or {@link #WALL} (sneaking: a 2-high wall beside them).
 * Sent on every change and refreshed once a second while held.
 */
public record SpeedBuildPayload(int mode) implements CustomPacketPayload {
	public static final int NONE = 0;
	public static final int BRIDGE = 1;
	public static final int WALL = 2;

	public static final CustomPacketPayload.Type<SpeedBuildPayload> TYPE =
			new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(ProjectHeroMod.MOD_ID, "speed_build"));

	public static final StreamCodec<RegistryFriendlyByteBuf, SpeedBuildPayload> CODEC = StreamCodec.of(
			(buf, p) -> buf.writeVarInt(p.mode),
			buf -> new SpeedBuildPayload(buf.readVarInt()));

	@Override
	public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
