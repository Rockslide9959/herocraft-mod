package com.projecthero.mod.network;

import com.projecthero.mod.ProjectHeroMod;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Server &rarr; client: arm the owning client's adhesion grab intent (v0.6.21).
 *
 * <p>{@code SpiderClimbLocal} -- which the owning client's movement simulation reads to decide whether
 * to stick to a surface -- is a per-side unsynced attachment, so a server-side
 * {@code SpiderClimb.requestGrab} never reaches the client that actually integrates the motion. A
 * shift + Web Zip is fired through the server ability router, so the server sends this one marker
 * packet to tell the client "you asked to grab": the client sets its own grab intent and the moment
 * the zip plants the player on the wall the climb engine takes over, straight into a wall crawl.
 */
public record SpiderClimbGrabPayload() implements CustomPacketPayload {
	public static final CustomPacketPayload.Type<SpiderClimbGrabPayload> TYPE =
			new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(ProjectHeroMod.MOD_ID, "spider_climb_grab"));

	public static final StreamCodec<RegistryFriendlyByteBuf, SpiderClimbGrabPayload> CODEC =
			StreamCodec.unit(new SpiderClimbGrabPayload());

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
