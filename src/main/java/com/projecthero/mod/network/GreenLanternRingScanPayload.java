package com.projecthero.mod.network;

import com.projecthero.mod.ProjectHeroMod;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Server &rarr; client (v0.11.10): the entity ids Ring Scan (Shift+V) just picked up around the
 * recipient, split by hostile/passive so the client can outline each set a different colour. Sent only
 * to the scanning player, replacing their whole previous set -- exactly {@code SpiderSenseGlowPayload}'s
 * pattern, so nobody else's client is told anything and nobody else sees the glow.
 */
public record GreenLanternRingScanPayload(int[] hostileIds, int[] passiveIds) implements CustomPacketPayload {
	public static final CustomPacketPayload.Type<GreenLanternRingScanPayload> TYPE = new CustomPacketPayload.Type<>(
			ResourceLocation.fromNamespaceAndPath(ProjectHeroMod.MOD_ID, "green_lantern_ring_scan"));

	public static final StreamCodec<RegistryFriendlyByteBuf, GreenLanternRingScanPayload> CODEC =
			StreamCodec.ofMember(GreenLanternRingScanPayload::write, GreenLanternRingScanPayload::read);

	private static void write(GreenLanternRingScanPayload payload, RegistryFriendlyByteBuf buf) {
		buf.writeVarIntArray(payload.hostileIds);
		buf.writeVarIntArray(payload.passiveIds);
	}

	private static GreenLanternRingScanPayload read(RegistryFriendlyByteBuf buf) {
		return new GreenLanternRingScanPayload(buf.readVarIntArray(), buf.readVarIntArray());
	}

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
