package com.projecthero.mod.network;

import com.projecthero.mod.ProjectHeroMod;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * v0.16.4 client &rarr; server: a cocooned player pressed one of the strafe keys while struggling to break out of a web
 * cocoon ({@code key} 0 = left, 1 = right; anything else is ignored). Only a request: the server checks that the player
 * is really cocooned, rate-limits the presses and keeps the progress itself ({@code spider.SpiderStruggle}).
 */
public record SpiderStrugglePayload(int key) implements CustomPacketPayload {
	public static final CustomPacketPayload.Type<SpiderStrugglePayload> TYPE =
			new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(ProjectHeroMod.MOD_ID, "spider_struggle"));

	public static final StreamCodec<RegistryFriendlyByteBuf, SpiderStrugglePayload> CODEC = StreamCodec.composite(
			ByteBufCodecs.VAR_INT, SpiderStrugglePayload::key,
			SpiderStrugglePayload::new);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
