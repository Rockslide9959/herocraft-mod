package com.projecthero.mod.network;

import com.projecthero.mod.ProjectHeroMod;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * v0.14.29 Remote Pilot, client &rarr; server, every client tick while piloting: the pilot's stick input. The server
 * clamps everything and moves the drone itself ({@link com.projecthero.mod.ironman.drone.IronManDroneEntity}).
 *
 * @param forward -1..1 (W / S)
 * @param strafe  -1..1 (A / D, positive = left like vanilla's leftImpulse)
 * @param yaw     aim yaw (the pilot's mouse look)
 * @param pitch   aim pitch
 * @param flags   {@link #UP} | {@link #DOWN} | {@link #BOOST} | {@link #FIRE} | {@link #END}
 */
public record IronManDroneInputPayload(float forward, float strafe, float yaw, float pitch, int flags)
		implements CustomPacketPayload {
	public static final int UP = 1;
	public static final int DOWN = 2;
	public static final int BOOST = 4;
	public static final int FIRE = 8;
	/** C pressed: close the link. */
	public static final int END = 16;

	public static final CustomPacketPayload.Type<IronManDroneInputPayload> TYPE =
			new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(ProjectHeroMod.MOD_ID, "iron_man_drone_input"));

	public static final StreamCodec<RegistryFriendlyByteBuf, IronManDroneInputPayload> CODEC = StreamCodec.composite(
			ByteBufCodecs.FLOAT, IronManDroneInputPayload::forward,
			ByteBufCodecs.FLOAT, IronManDroneInputPayload::strafe,
			ByteBufCodecs.FLOAT, IronManDroneInputPayload::yaw,
			ByteBufCodecs.FLOAT, IronManDroneInputPayload::pitch,
			ByteBufCodecs.VAR_INT, IronManDroneInputPayload::flags,
			IronManDroneInputPayload::new);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
