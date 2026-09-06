package com.projecthero.mod.network;

import com.projecthero.mod.ProjectHeroMod;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Server &rarr; client (v0.9.3): the set of entity ids the recipient's Spider-Sense currently marks as
 * a threat. Sent only to the Spider-Man player themselves, on every threat scan, and it fully replaces
 * the client's previous set. The client outlines exactly these entities red -- nobody else's client is
 * told anything, so no other player sees or benefits from another player's danger sense.
 *
 * <p>Replaces the old approach of setting the vanilla {@code glowingTag} on the mob server-side, which
 * synced the outline to every client.
 */
public record SpiderSenseGlowPayload(int[] ids) implements CustomPacketPayload {
	public static final CustomPacketPayload.Type<SpiderSenseGlowPayload> TYPE =
			new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(ProjectHeroMod.MOD_ID, "spider_sense_glow"));

	public static final StreamCodec<RegistryFriendlyByteBuf, SpiderSenseGlowPayload> CODEC =
			StreamCodec.ofMember(SpiderSenseGlowPayload::write, SpiderSenseGlowPayload::read);

	private static void write(SpiderSenseGlowPayload payload, RegistryFriendlyByteBuf buf) {
		buf.writeVarIntArray(payload.ids);
	}

	private static SpiderSenseGlowPayload read(RegistryFriendlyByteBuf buf) {
		return new SpiderSenseGlowPayload(buf.readVarIntArray());
	}

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
