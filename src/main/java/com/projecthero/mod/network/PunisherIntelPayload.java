package com.projecthero.mod.network;

import com.projecthero.mod.ProjectHeroMod;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * v0.15.18, server &rarr; client: the Punisher's private target picture. {@code markId} is the entity his Target
 * Designation marked (kept for {@code markTicks}); {@link #CLEAR_MARK} drops it, {@link #KEEP_MARK} leaves it alone.
 * {@code threats} are what a Threat Assessment picked up (kept for {@code threatTicks}). Sent only to the Punisher
 * himself and drawn as a glow in his own render -- nothing is set on any entity, so no one else ever sees it.
 */
public record PunisherIntelPayload(int markId, int markTicks, int[] threats, int threatTicks) implements CustomPacketPayload {
	public static final int CLEAR_MARK = -1;
	public static final int KEEP_MARK = -2;

	public static final CustomPacketPayload.Type<PunisherIntelPayload> TYPE =
			new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(ProjectHeroMod.MOD_ID, "punisher_intel"));

	public static final StreamCodec<RegistryFriendlyByteBuf, PunisherIntelPayload> CODEC =
			StreamCodec.ofMember(PunisherIntelPayload::write, PunisherIntelPayload::read);

	private static void write(PunisherIntelPayload p, RegistryFriendlyByteBuf buf) {
		buf.writeVarInt(p.markId + 2); // -2 / -1 / ids, kept non-negative for the var-int
		buf.writeVarInt(p.markTicks);
		buf.writeVarIntArray(p.threats);
		buf.writeVarInt(p.threatTicks);
	}

	private static PunisherIntelPayload read(RegistryFriendlyByteBuf buf) {
		return new PunisherIntelPayload(buf.readVarInt() - 2, buf.readVarInt(), buf.readVarIntArray(), buf.readVarInt());
	}

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
