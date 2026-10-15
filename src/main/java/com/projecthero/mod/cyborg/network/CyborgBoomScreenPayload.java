package com.projecthero.mod.cyborg.network;

import com.projecthero.mod.ProjectHeroMod;
import com.projecthero.mod.stormbreaker.BifrostWaypoints;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Server to client (v0.16.5): Cyborg's Boom Tube menu (Shift+V) -- his three waypoints, the ticks left on the Boom Tube's
 * cooldown and the dimension he is in. {@code open} = open the menu; otherwise refresh one already open. The same shape
 * as {@code BifrostScreenPayload}: the client shows both with one screen.
 */
public record CyborgBoomScreenPayload(boolean open, BifrostWaypoints waypoints, int cooldownTicks, String dimension)
		implements CustomPacketPayload {
	public static final CustomPacketPayload.Type<CyborgBoomScreenPayload> TYPE =
			new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(ProjectHeroMod.MOD_ID, "cyborg_boom_screen"));

	public static final StreamCodec<RegistryFriendlyByteBuf, CyborgBoomScreenPayload> CODEC = StreamCodec.of(
			(buf, p) -> {
				buf.writeBoolean(p.open);
				BifrostWaypoints.STREAM_CODEC.encode(buf, p.waypoints);
				ByteBufCodecs.VAR_INT.encode(buf, p.cooldownTicks);
				ByteBufCodecs.STRING_UTF8.encode(buf, p.dimension);
			},
			buf -> new CyborgBoomScreenPayload(buf.readBoolean(), BifrostWaypoints.STREAM_CODEC.decode(buf), ByteBufCodecs.VAR_INT.decode(buf),
					ByteBufCodecs.STRING_UTF8.decode(buf)));

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
