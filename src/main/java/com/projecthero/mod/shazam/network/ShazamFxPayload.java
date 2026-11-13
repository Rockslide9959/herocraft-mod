package com.projecthero.mod.shazam.network;

import com.projecthero.mod.ProjectHeroMod;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Server to every client near (v0.17.3 Shazam effects pass): one piece of lightning drawn client side by {@code ShazamBoltFx} --
 * real branching bolt geometry instead of vanilla bolts. {@code a} and {@code b} are two points (from / to, or a centre and a
 * direction), {@code size} scales it, {@code life} is ticks, {@code entity} an entity to follow (-1 none), {@code seed} keeps
 * every viewer's bolt the same shape.
 */
public record ShazamFxPayload(int kind, float ax, float ay, float az, float bx, float by, float bz, float size, int life, int entity, int seed)
		implements CustomPacketPayload {
	/** A branching arc from a to b (Lightning Hand and its chain hops). */
	public static final int ARC = 0;
	/** A bolt from the sky onto b (Call the Lightning, Zeus Storm, the transform strike, Eternity Strike). */
	public static final int SKY = 1;
	/** Mercury Dash: the red blur from a to b with a crackle trail. */
	public static final int BLUR = 2;
	/** Thunderclap: shockwave rings from a along direction b. */
	public static final int CLAP = 3;
	/** Bolts bursting out along the ground from a (Thunder Tackle, the Eternity slam, the transform). */
	public static final int BURST = 4;
	/** Achilles Stand: the golden aura round {@code entity}. */
	public static final int AURA = 5;
	/** The landing: glowing lightning cracks across the ground from a, facing b. */
	public static final int CRACK = 6;

	public static final CustomPacketPayload.Type<ShazamFxPayload> TYPE = new CustomPacketPayload.Type<>(ProjectHeroMod.id("shazam_fx"));

	public static final StreamCodec<RegistryFriendlyByteBuf, ShazamFxPayload> CODEC = StreamCodec.of((buf, p) -> {
		buf.writeVarInt(p.kind);
		buf.writeFloat(p.ax);
		buf.writeFloat(p.ay);
		buf.writeFloat(p.az);
		buf.writeFloat(p.bx);
		buf.writeFloat(p.by);
		buf.writeFloat(p.bz);
		buf.writeFloat(p.size);
		buf.writeVarInt(p.life);
		buf.writeVarInt(p.entity);
		buf.writeInt(p.seed);
	}, buf -> new ShazamFxPayload(buf.readVarInt(), buf.readFloat(), buf.readFloat(), buf.readFloat(), buf.readFloat(), buf.readFloat(),
			buf.readFloat(), buf.readFloat(), buf.readVarInt(), buf.readVarInt(), buf.readInt()));

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
