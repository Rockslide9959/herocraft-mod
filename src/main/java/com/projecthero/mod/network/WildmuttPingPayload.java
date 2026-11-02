package com.projecthero.mod.network;

import com.projecthero.mod.ProjectHeroMod;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Server &rarr; client (v0.17.2 Wildmutt revamp): what a Savage Roar's sound ping reached -- these entities are outlined amber for
 * {@code ticks}. Sent only to the roaring Wildmutt and his squad mates; nothing is set on any entity, so nobody else sees it.
 */
public record WildmuttPingPayload(int[] ids, int ticks) implements CustomPacketPayload {
	public static final CustomPacketPayload.Type<WildmuttPingPayload> TYPE =
			new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(ProjectHeroMod.MOD_ID, "wildmutt_ping"));

	public static final StreamCodec<RegistryFriendlyByteBuf, WildmuttPingPayload> CODEC =
			StreamCodec.ofMember(WildmuttPingPayload::write, WildmuttPingPayload::read);

	private static void write(WildmuttPingPayload p, RegistryFriendlyByteBuf buf) {
		buf.writeVarIntArray(p.ids);
		buf.writeVarInt(p.ticks);
	}

	private static WildmuttPingPayload read(RegistryFriendlyByteBuf buf) {
		return new WildmuttPingPayload(buf.readVarIntArray(), buf.readVarInt());
	}

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
