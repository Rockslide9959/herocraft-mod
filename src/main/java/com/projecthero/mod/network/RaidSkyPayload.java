package com.projecthero.mod.network;

import com.projecthero.mod.ProjectHeroMod;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Server -> client: "you are standing in (or near) a raid area." The client uses it to wash the sky
 * a dark purple while a Zombie Raid is happening around the player -- a visible signal that the
 * timer ran out here and the raid is now live, the way a vanilla Pillager raid shades everything.
 *
 * <p>Sent only when a player crosses into or out of the zone, not on a timer, so a whole raid is a
 * couple of packets per player.
 *
 * @param active false clears the tint
 * @param x      raid centre
 * @param y      raid centre
 * @param z      raid centre
 * @param radius blocks from the centre the tint is at full strength (it fades out over the next few)
 */
public record RaidSkyPayload(boolean active, int x, int y, int z, float radius)
		implements CustomPacketPayload {

	public static final CustomPacketPayload.Type<RaidSkyPayload> TYPE =
			new CustomPacketPayload.Type<>(ProjectHeroMod.id("raid_sky"));

	public static final RaidSkyPayload CLEAR = new RaidSkyPayload(false, 0, 0, 0, 0.0f);

	public static final StreamCodec<RegistryFriendlyByteBuf, RaidSkyPayload> CODEC = StreamCodec.composite(
			ByteBufCodecs.BOOL, RaidSkyPayload::active,
			ByteBufCodecs.VAR_INT, RaidSkyPayload::x,
			ByteBufCodecs.VAR_INT, RaidSkyPayload::y,
			ByteBufCodecs.VAR_INT, RaidSkyPayload::z,
			ByteBufCodecs.FLOAT, RaidSkyPayload::radius,
			RaidSkyPayload::new);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
