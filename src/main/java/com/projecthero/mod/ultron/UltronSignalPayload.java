package com.projecthero.mod.ultron;

import com.projecthero.mod.ProjectHeroMod;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * v0.16.1: server &rarr; client: one of Ultron's "in your systems" signals (client {@code UltronJamClient}).
 * <ul>
 *   <li>{@link #RETICLE} (to everyone near): Prime's Network Override telegraph -- a red targeting reticle above entity
 *       {@code entityId} for {@code ticks}.</li>
 *   <li>{@link #JAM} (to the victim only): your ability keys in {@code mask} (bit n-1 = slot n) are jammed for {@code ticks};
 *       {@code entityId} is 1 for a Network Override (the red static overlay) and 0 for a Hijacker's one-key jam.</li>
 * </ul>
 * Cosmetic: the server ({@link UltronJam}) is what actually refuses the key.
 */
public record UltronSignalPayload(byte kind, int entityId, int mask, int ticks) implements CustomPacketPayload {
	public static final byte RETICLE = 0;
	public static final byte JAM = 1;

	public static final CustomPacketPayload.Type<UltronSignalPayload> TYPE =
			new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(ProjectHeroMod.MOD_ID, "ultron_signal"));

	public static final StreamCodec<RegistryFriendlyByteBuf, UltronSignalPayload> CODEC = StreamCodec.of((buf, p) -> {
		buf.writeByte(p.kind);
		buf.writeVarInt(p.entityId);
		buf.writeVarInt(p.mask);
		buf.writeVarInt(p.ticks);
	}, buf -> new UltronSignalPayload(buf.readByte(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt()));

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
