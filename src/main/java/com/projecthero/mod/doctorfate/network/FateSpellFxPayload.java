package com.projecthero.mod.doctorfate.network;

import com.projecthero.mod.ProjectHeroMod;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Server to every client near (v0.17.3 Doctor Fate effects pass): one spell's glowing golden geometry, drawn client side by
 * {@code FateSpellFxClient} (ankhs, ankh gates, rune circles, chains, the Order Blast cone, the falling hieroglyph blades, the
 * landing's ankh shockwave). Purely cosmetic: {@code x/y/z} is where, {@code dx/dy/dz} a direction (or a second point for
 * {@link #BOLT}), {@code size} blocks, {@code life} ticks, {@code entity} an entity to follow (-1 = none), {@code colour} RGB.
 */
public record FateSpellFxPayload(int kind, float x, float y, float z, float dx, float dy, float dz, float size, int life, int entity, int colour)
		implements CustomPacketPayload {
	/** Ankh Bolt: a beam from (x,y,z) to (dx,dy,dz) with a spinning ankh racing down it. */
	public static final int BOLT = 0;
	/** A standing ankh facing (dx,dy,dz): impacts, hit feedback, the flight's lift-off. */
	public static final int ANKH = 1;
	/** A flat ankh gate on the ground opening and closing (Banishment, Gate Drop), with a pillar of light. */
	public static final int GATE = 2;
	/** An upright ankh-ring portal facing (dx,dy,dz) (Ankh Step). */
	public static final int PORTAL = 3;
	/** Golden Chains from the ground onto {@code entity}, with a binding rune circle under it. */
	public static final int CHAINS = 4;
	/** The Ward of Order's rune circle on the ground. */
	public static final int WARD = 5;
	/** Glyph Storm: a rune circle in the sky raining hieroglyph blades onto a circle on the ground. */
	public static final int STORM = 6;
	/** Order Blast: a cone of golden force waves along (dx,dy,dz). */
	public static final int CONE = 7;
	/** The Avatar of Order's hand slam: a huge rune circle and shock ring. */
	public static final int SLAM = 8;
	/** The hero landing: a golden ankh shockwave on the ground facing (dx,dy,dz). */
	public static final int LANDING = 9;
	/** The Avatar rising: a column of glyphs behind him. */
	public static final int RISE = 10;
	/**
	 * v0.17.4 the Helmet of Fate summoned: it flies from (x,y,z) to the head of {@code entity} over {@code life} ticks, trailing gold
	 * ({@code size} 1 = it flew out of where it lay, 0 = out of an ankh portal beside him).
	 */
	public static final int SUMMON = 11;

	public static final CustomPacketPayload.Type<FateSpellFxPayload> TYPE = new CustomPacketPayload.Type<>(ProjectHeroMod.id("doctor_fate_spell_fx"));

	public static final StreamCodec<RegistryFriendlyByteBuf, FateSpellFxPayload> CODEC = StreamCodec.of((buf, p) -> {
		buf.writeVarInt(p.kind);
		buf.writeFloat(p.x);
		buf.writeFloat(p.y);
		buf.writeFloat(p.z);
		buf.writeFloat(p.dx);
		buf.writeFloat(p.dy);
		buf.writeFloat(p.dz);
		buf.writeFloat(p.size);
		buf.writeVarInt(p.life);
		buf.writeVarInt(p.entity);
		buf.writeInt(p.colour);
	}, buf -> new FateSpellFxPayload(buf.readVarInt(), buf.readFloat(), buf.readFloat(), buf.readFloat(), buf.readFloat(), buf.readFloat(),
			buf.readFloat(), buf.readFloat(), buf.readVarInt(), buf.readVarInt(), buf.readInt()));

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
