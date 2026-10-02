package com.projecthero.mod.network;

import com.projecthero.mod.ProjectHeroMod;
import com.projecthero.mod.stormbreaker.BifrostWaypoints;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Server &rarr; client (v0.14.20): Stormbreaker's Bifrost screen data -- the player's three waypoints, the ticks left
 * on the Bifrost's own cooldown and the dimension the player is in. {@code open} = open the screen (Sneak +
 * right-click); otherwise it only refreshes a screen that is already open (after a save, clear or failed travel).
 */
public record BifrostScreenPayload(boolean open, BifrostWaypoints waypoints, int cooldownTicks, String dimension)
		implements CustomPacketPayload {
	public static final CustomPacketPayload.Type<BifrostScreenPayload> TYPE =
			new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(ProjectHeroMod.MOD_ID, "bifrost_screen"));

	public static final net.minecraft.network.codec.StreamCodec<RegistryFriendlyByteBuf, BifrostScreenPayload> CODEC =
			net.minecraft.network.codec.StreamCodec.of(
					(buf, p) -> {
						buf.writeBoolean(p.open);
						BifrostWaypoints.STREAM_CODEC.encode(buf, p.waypoints);
						ByteBufCodecs.VAR_INT.encode(buf, p.cooldownTicks);
						ByteBufCodecs.STRING_UTF8.encode(buf, p.dimension);
					},
					buf -> new BifrostScreenPayload(buf.readBoolean(), BifrostWaypoints.STREAM_CODEC.decode(buf),
							ByteBufCodecs.VAR_INT.decode(buf), ByteBufCodecs.STRING_UTF8.decode(buf)));

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
