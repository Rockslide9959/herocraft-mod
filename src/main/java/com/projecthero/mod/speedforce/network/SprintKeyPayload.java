package com.projecthero.mod.speedforce.network;

import com.projecthero.mod.ProjectHeroMod;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Client to server (v0.17.1): whether the Speed Force Speedster's sprint key (Ctrl) is PHYSICALLY held right now. With
 * vanilla's Toggle Sprint option the player stays sprinting after the key is let go, so the server's
 * {@code isSprinting()} can't tell "holding Ctrl" apart from "toggled on" -- this can. Sent on every change and refreshed
 * once a second.
 */
public record SprintKeyPayload(boolean held) implements CustomPacketPayload {
	public static final CustomPacketPayload.Type<SprintKeyPayload> TYPE =
			new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(ProjectHeroMod.MOD_ID, "sprint_key"));

	public static final StreamCodec<RegistryFriendlyByteBuf, SprintKeyPayload> CODEC = StreamCodec.of(
			(buf, p) -> buf.writeBoolean(p.held),
			buf -> new SprintKeyPayload(buf.readBoolean()));

	@Override
	public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
