package com.projecthero.mod.network;

import com.projecthero.mod.ProjectHeroMod;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Server to client (v0.17.5 tendril-vfx round): one Symbiote move effect -- drawn on every viewer's client as rendered tendrils in the
 * owner's suit colours ({@code client.symbiote.SymbioteVfxClient}) instead of the old particle bursts. {@code kind} is one of
 * {@code SymbioteVfx}'s constants; the owner id picks the colours (and, for the body-anchored kinds, where the tendrils grow from);
 * {@code target} is an entity the tendrils hold on to (-1 = none). Purely cosmetic.
 */
public record SymbioteVfxPayload(int kind, int owner, int target, float x, float y, float z, float dx, float dy, float dz, float size,
		int duration) implements CustomPacketPayload {
	public static final CustomPacketPayload.Type<SymbioteVfxPayload> TYPE = new CustomPacketPayload.Type<>(ProjectHeroMod.id("symbiote_vfx"));

	public static final StreamCodec<RegistryFriendlyByteBuf, SymbioteVfxPayload> CODEC = StreamCodec.of((buf, p) -> {
		buf.writeByte(p.kind);
		buf.writeVarInt(p.owner);
		buf.writeVarInt(p.target);
		buf.writeFloat(p.x);
		buf.writeFloat(p.y);
		buf.writeFloat(p.z);
		buf.writeFloat(p.dx);
		buf.writeFloat(p.dy);
		buf.writeFloat(p.dz);
		buf.writeFloat(p.size);
		buf.writeVarInt(p.duration);
	}, buf -> new SymbioteVfxPayload(buf.readByte(), buf.readVarInt(), buf.readVarInt(), buf.readFloat(), buf.readFloat(), buf.readFloat(),
			buf.readFloat(), buf.readFloat(), buf.readFloat(), buf.readFloat(), buf.readVarInt()));

	public SymbioteVfxPayload {
		size = Float.isFinite(size) ? Math.max(0.05f, Math.min(12.0f, size)) : 1.0f;
		duration = Math.max(1, Math.min(400, duration));
	}

	@Override
	public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
