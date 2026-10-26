package com.projecthero.mod.speedforce.network;

import com.projecthero.mod.ProjectHeroMod;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Server to client (v0.17.1): one Speed Force visual -- a gold bolt from (fx, fy, fz) to (tx, ty, tz) (the Lightning
 * Throw and its chain), the sonic boom ring at (fx, fy, fz), a line of after-images along a zip (Step, Rewind, the
 * Remnant), or the awakening's column of lightning. Drawn by {@code SpeedForceClient}.
 */
public record SpeedForceFxPayload(int playerId, int kind, double fx, double fy, double fz, double tx, double ty, double tz)
		implements CustomPacketPayload {
	public static final int BOLT = 0;
	public static final int BOOM = 1;
	public static final int STREAK = 2;
	public static final int AWAKEN = 3;
	public static final int SURGE = 4;
	/**
	 * v0.17.1 (user) the Speed Force portal forming in front of the runner: centred at (fx, fy, fz), tx = how far it has
	 * formed (0..1), ty = the yaw it faces. Sent every tick of the charge; a client lets it collapse if they stop coming.
	 */
	public static final int PORTAL = 5;
	/** The charge broke off: the portal collapses at (fx, fy, fz). */
	public static final int PORTAL_COLLAPSE = 6;
	/** The portal took them: a burst of lightning at (fx, fy, fz). */
	public static final int PORTAL_ENTER = 7;
	/** Out of the Speed Force: a bolt from (tx, ty, tz) down to where they land (fx, fy, fz). */
	public static final int REALM_EXIT = 8;
	/**
	 * v0.17.1 the Black Flash (playerId is HIS entity id): a black-and-red bolt (fx..)->(tx..); a dark shockwave ring at fx..
	 * of radius tx; the punch out of the Speed Force at fx..; his dissolve (bolts from fx.. up to tx..); a zip, a line of his
	 * after-images from fx.. to tx...
	 */
	public static final int BF_BOLT = 9;
	public static final int BF_RING = 10;
	public static final int BF_PUNCH = 11;
	public static final int BF_DISSOLVE = 12;
	public static final int BF_STREAK = 13;

	public static final CustomPacketPayload.Type<SpeedForceFxPayload> TYPE =
			new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(ProjectHeroMod.MOD_ID, "speed_force_fx"));

	public static final StreamCodec<RegistryFriendlyByteBuf, SpeedForceFxPayload> CODEC = StreamCodec.of(
			(buf, p) -> {
				buf.writeVarInt(p.playerId);
				buf.writeVarInt(p.kind);
				buf.writeDouble(p.fx);
				buf.writeDouble(p.fy);
				buf.writeDouble(p.fz);
				buf.writeDouble(p.tx);
				buf.writeDouble(p.ty);
				buf.writeDouble(p.tz);
			},
			buf -> new SpeedForceFxPayload(buf.readVarInt(), buf.readVarInt(), buf.readDouble(), buf.readDouble(), buf.readDouble(),
					buf.readDouble(), buf.readDouble(), buf.readDouble()));

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
