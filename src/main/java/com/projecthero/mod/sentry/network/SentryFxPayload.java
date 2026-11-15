package com.projecthero.mod.sentry.network;

import com.projecthero.mod.ProjectHeroMod;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Server to client (v0.17.5 Sentry): one effect to draw, sent to everyone near it. {@code x/y/z} is where; {@code size} a radius or
 * length; {@code ticks} how long it lasts; {@code tx/ty/tz} a second point (a tendril's end); {@code owner} the entity it belongs to
 * (-1 none).
 */
public record SentryFxPayload(int kind, double x, double y, double z, float size, int ticks, double tx, double ty, double tz, int owner)
		implements CustomPacketPayload {
	/** A golden shockwave ring on the ground. */
	public static final int RING = 0;
	/** A Million Suns going off: the white-gold flash sphere. */
	public static final int SUNS = 1;
	/** A Million Suns charging: the gathering light (ticks = charge time). */
	public static final int SUNS_CHARGE = 2;
	/** Void Rift: the black hole. */
	public static final int RIFT = 3;
	/** Nightfall: the dome of darkness. */
	public static final int NIGHTFALL = 4;
	/** A black tendril from x/y/z to tx/ty/tz (Shadow Lash, Grasp, the takeover's grabs). */
	public static final int TENDRIL = 5;
	/** Erase: the black streak and the shadow burning in. */
	public static final int ERASE = 6;
	/** The sonic boom of the dash. */
	public static final int BOOM = 7;
	/** The golden flash when the Void is thrown out. */
	public static final int THROWN_OUT = 8;
	/** Sentinel Shield: the golden dome (ticks = how long). */
	public static final int SHIELD = 9;
	/** Sun Dive's golden crater. */
	public static final int CRATER = 10;
	/** A helper reaching the Void: a gold thread from them to it. */
	public static final int REACH = 11;

	public static final CustomPacketPayload.Type<SentryFxPayload> TYPE = new CustomPacketPayload.Type<>(ProjectHeroMod.id("sentry_fx"));

	public static final StreamCodec<RegistryFriendlyByteBuf, SentryFxPayload> CODEC = StreamCodec.of((buf, p) -> {
		buf.writeVarInt(p.kind);
		buf.writeDouble(p.x);
		buf.writeDouble(p.y);
		buf.writeDouble(p.z);
		buf.writeFloat(p.size);
		buf.writeVarInt(p.ticks);
		buf.writeDouble(p.tx);
		buf.writeDouble(p.ty);
		buf.writeDouble(p.tz);
		buf.writeVarInt(p.owner + 1);
	}, buf -> new SentryFxPayload(buf.readVarInt(), buf.readDouble(), buf.readDouble(), buf.readDouble(), buf.readFloat(), buf.readVarInt(),
			buf.readDouble(), buf.readDouble(), buf.readDouble(), buf.readVarInt() - 1));

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
