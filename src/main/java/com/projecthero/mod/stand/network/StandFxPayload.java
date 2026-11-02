package com.projecthero.mod.stand.network;

import com.projecthero.mod.ProjectHeroMod;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * v0.17.2 server -&gt; Stand users only (user: "i dont want so much purple particle effects for star platinums abilities, can you
 * make it vfx instead"): one rendered effect ({@code StandFx}) in place of the old violet dust. {@code (dx, dy, dz)} is a direction
 * (a ring's facing, an uppercut's rise) or, for {@link #BEAM}, the far end; {@code size} scales it.
 */
public record StandFxPayload(int kind, double x, double y, double z, double dx, double dy, double dz, float size) implements CustomPacketPayload {
	/** A fist landing: a bright flash and a thin shockwave ring. */
	public static final int IMPACT = 0;
	/** A heavy blow (finisher, Star Breaker, catch): a bigger flash, two rings and spokes. */
	public static final int BURST = 1;
	/** Star Finger: a glowing line from the point to {@code (dx, dy, dz)}. */
	public static final int BEAM = 2;
	/** Summon / dismiss / awakening: a soft glow and a ring rising round the body. */
	public static final int AURA = 3;
	/** The Uppercut: a rising streak. */
	public static final int RISE = 4;
	/** The World: a wide ground ring and a fading dome of light. */
	public static final int WORLD = 5;

	public static final CustomPacketPayload.Type<StandFxPayload> TYPE = new CustomPacketPayload.Type<>(ProjectHeroMod.id("stand_fx"));

	public static final StreamCodec<RegistryFriendlyByteBuf, StandFxPayload> CODEC = StreamCodec.of((buf, p) -> {
		buf.writeVarInt(p.kind);
		buf.writeDouble(p.x);
		buf.writeDouble(p.y);
		buf.writeDouble(p.z);
		buf.writeDouble(p.dx);
		buf.writeDouble(p.dy);
		buf.writeDouble(p.dz);
		buf.writeFloat(p.size);
	}, buf -> new StandFxPayload(buf.readVarInt(), buf.readDouble(), buf.readDouble(), buf.readDouble(), buf.readDouble(), buf.readDouble(),
			buf.readDouble(), buf.readFloat()));

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
