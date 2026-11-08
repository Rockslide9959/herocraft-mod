package com.projecthero.mod.network;

import com.projecthero.mod.ProjectHeroMod;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * v0.17.3: client -> server, Andy's right click held / let go while he flies (the boost: 40 b/s, the body burns away). Sent on
 * the edges only; the server re-checks that he is flying ({@code AndyFlight.setBoost}).
 */
public record AndyBoostPayload(boolean held) implements CustomPacketPayload {
	public static final CustomPacketPayload.Type<AndyBoostPayload> TYPE =
			new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(ProjectHeroMod.MOD_ID, "andy_boost"));

	public static final StreamCodec<RegistryFriendlyByteBuf, AndyBoostPayload> CODEC = StreamCodec.composite(
			ByteBufCodecs.BOOL, AndyBoostPayload::held, AndyBoostPayload::new);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
