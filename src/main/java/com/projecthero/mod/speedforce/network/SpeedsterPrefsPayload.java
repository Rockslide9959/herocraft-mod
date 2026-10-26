package com.projecthero.mod.speedforce.network;

import com.projecthero.mod.ProjectHeroMod;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Client to server (v0.17.1): a speedster's own settings -- Ground-Stick on or off (Shift+N for the plain Speedster, the
 * Shift+N menu for the Speed Force Speedster) and the Speed Force Speedster's lightning colour ({@code -1} = unchanged).
 */
public record SpeedsterPrefsPayload(boolean groundStick, int colour) implements CustomPacketPayload {
	public static final CustomPacketPayload.Type<SpeedsterPrefsPayload> TYPE =
			new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(ProjectHeroMod.MOD_ID, "speedster_prefs"));

	public static final StreamCodec<RegistryFriendlyByteBuf, SpeedsterPrefsPayload> CODEC = StreamCodec.of(
			(buf, p) -> {
				buf.writeBoolean(p.groundStick);
				buf.writeVarInt(p.colour + 1);
			},
			buf -> new SpeedsterPrefsPayload(buf.readBoolean(), buf.readVarInt() - 1));

	@Override
	public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
