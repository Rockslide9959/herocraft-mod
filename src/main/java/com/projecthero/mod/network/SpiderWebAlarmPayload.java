package com.projecthero.mod.network;

import com.projecthero.mod.ProjectHeroMod;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * v0.16.1 server &rarr; the web's owner only: someone who is not Spider-Man or his squad just touched one of his webs
 * at ({@code x}, {@code y}, {@code z}). The owner's client plays the Spider-Sense ping and points a HUD arrow at it for
 * a few seconds.
 */
public record SpiderWebAlarmPayload(double x, double y, double z) implements CustomPacketPayload {
	public static final CustomPacketPayload.Type<SpiderWebAlarmPayload> TYPE =
			new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(ProjectHeroMod.MOD_ID, "spider_web_alarm"));

	public static final StreamCodec<RegistryFriendlyByteBuf, SpiderWebAlarmPayload> CODEC = StreamCodec.composite(
			ByteBufCodecs.DOUBLE, SpiderWebAlarmPayload::x,
			ByteBufCodecs.DOUBLE, SpiderWebAlarmPayload::y,
			ByteBufCodecs.DOUBLE, SpiderWebAlarmPayload::z,
			SpiderWebAlarmPayload::new);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
