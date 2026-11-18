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
	// moveset 2 (2026-11-18)
	/** Disassemble / a Golden Fury kill: the creature comes apart into golden motes (size = its height, tx = its width). */
	public static final int DISINTEGRATE = 12;
	/** Shadow Pool: a pool of shadow spreading on the ground (size = full radius, ticks = how long). */
	public static final int POOL = 13;
	/** Swallow: a black void sphere round the creature {@code owner} (size = radius). */
	public static final int SPHERE = 14;
	/** Nightmare: a wave of horror rolling out (size = radius). */
	public static final int NIGHTMARE = 15;
	/** Shadow Eclipse: a black sun with a gold corona over {@code owner} and the dark under it (size = radius). */
	public static final int ECLIPSE = 16;
	/** A shadow figure (Shadow Clones, Nightmare's manifestations) at x/y/z facing tx/ty/tz (size = height). */
	public static final int SHADE = 17;
	/** Golden Guardian: a pillar of light on someone it helps (size = height). */
	public static final int PILLAR = 18;
	/** Skyward Hurl / the Haymaker: a golden comet trail behind the creature {@code owner}. */
	public static final int COMET = 19;
	/** Round 3: Solar Flare -- a burst of sunlight (rays, rings, a flash nearby) round {@code owner}. */
	public static final int FLARE = 20;
	/** Round 3: Golden Sense -- a golden pulse running out to {@code size} round {@code owner}. */
	public static final int SENSE = 21;
	/** Round 3: the Flight Boost starting -- a sonic-boom ring across {@code owner}'s flight path ({@code to}: the direction point). */
	public static final int BOOST = 22;
	/** Round 3, the Void: Umbral Shroud (a dark swirl round {@code owner} for {@code ticks}) and Shadow Meld (an ink burst). */
	public static final int SHROUD = 23;
	public static final int MELD = 24;

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
