package com.projecthero.mod.miraculous.hawkmoth.network;

import java.util.List;

import com.projecthero.mod.ProjectHeroMod;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Server to client, to players near (v0.16.6): Swarm Blind's white butterflies swarm these creatures for {@code ticks}. */
public record HawkMothSwarmPayload(List<Integer> ids, int ticks) implements CustomPacketPayload {
	public static final CustomPacketPayload.Type<HawkMothSwarmPayload> TYPE =
			new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(ProjectHeroMod.MOD_ID, "hawk_moth_swarm"));

	public static final StreamCodec<RegistryFriendlyByteBuf, HawkMothSwarmPayload> CODEC = StreamCodec.composite(
			ByteBufCodecs.VAR_INT.apply(ByteBufCodecs.list(256)), HawkMothSwarmPayload::ids,
			ByteBufCodecs.VAR_INT, HawkMothSwarmPayload::ticks,
			HawkMothSwarmPayload::new);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
