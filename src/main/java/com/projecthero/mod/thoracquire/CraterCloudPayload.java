package com.projecthero.mod.thoracquire;

import java.util.List;

import com.projecthero.mod.ProjectHeroMod;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * v0.17.7: the Mjolnir's Craters near a player that still hold their hammer (each crater's hammer spot), resent every couple
 * of seconds, and the spots where a Trial of Asgard is being fought (the cloud darkens and churns there). The client draws
 * the storm cloud over each one and forgets a crater that stops being listed.
 */
public record CraterCloudPayload(List<BlockPos> craters, List<BlockPos> trials) implements CustomPacketPayload {
	public static final CustomPacketPayload.Type<CraterCloudPayload> TYPE = new CustomPacketPayload.Type<>(ProjectHeroMod.id("crater_cloud"));
	public static final StreamCodec<RegistryFriendlyByteBuf, CraterCloudPayload> CODEC = StreamCodec.composite(
			BlockPos.STREAM_CODEC.apply(ByteBufCodecs.list(64)), CraterCloudPayload::craters,
			BlockPos.STREAM_CODEC.apply(ByteBufCodecs.list(16)), CraterCloudPayload::trials, CraterCloudPayload::new);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
