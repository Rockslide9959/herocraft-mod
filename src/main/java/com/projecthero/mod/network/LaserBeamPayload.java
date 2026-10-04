package com.projecthero.mod.network;

import com.projecthero.mod.ProjectHeroMod;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;

/**
 * Server -&gt; client "draw a Laser Vision beam from A to B for {@code ticks} ticks" -- purely visual, all damage is
 * resolved on the server. Sent by {@code LaserBeams} to the players near a beam who would otherwise never see it:
 * viewers that do not track the shooting player (beyond their entity-tracking range, which shrinks with the viewer's
 * render distance -- often below the beams' 100-block reach), and everyone around a Laser Vision boss (whose beam
 * used to be only a particle line, dropped by the "Particles: Minimal / Decreased" setting and beyond 32 blocks).
 * Drawn by {@code LaserBeamRenderer} with the same geometry as a tracked player's beams.
 *
 * @param kind  one of the {@code KIND_*} constants (the renderer's beam style)
 * @param ticks how long the beam stays on screen; one-shots longer than {@link #REFRESH_TICKS} fade out over it
 */
public record LaserBeamPayload(Vec3 start, Vec3 end, int kind, int ticks) implements CustomPacketPayload {
	public static final int KIND_BEAM = 0;
	public static final int KIND_PIERCE = 1;
	public static final int KIND_SWEEP = 2;
	public static final int KIND_RECOIL = 3;
	public static final int KIND_IGNITE = 4;
	public static final int KIND_MAX = 5;

	/** Lifetime of a held beam's per-tick refresh: long enough to bridge one late packet, short enough to stop crisply. */
	public static final int REFRESH_TICKS = 3;

	public static final CustomPacketPayload.Type<LaserBeamPayload> TYPE =
			new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(ProjectHeroMod.MOD_ID, "laser_beam"));

	private static final StreamCodec<RegistryFriendlyByteBuf, Vec3> VEC3 = StreamCodec.composite(
			ByteBufCodecs.DOUBLE, Vec3::x,
			ByteBufCodecs.DOUBLE, Vec3::y,
			ByteBufCodecs.DOUBLE, Vec3::z,
			Vec3::new);

	public static final StreamCodec<RegistryFriendlyByteBuf, LaserBeamPayload> CODEC = StreamCodec.composite(
			VEC3, LaserBeamPayload::start,
			VEC3, LaserBeamPayload::end,
			ByteBufCodecs.VAR_INT, LaserBeamPayload::kind,
			ByteBufCodecs.VAR_INT, LaserBeamPayload::ticks,
			LaserBeamPayload::new);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
