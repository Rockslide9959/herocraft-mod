package com.projecthero.mod.miraculous.ladybug.network;

import com.projecthero.mod.ProjectHeroMod;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Server to client (v0.16.6): a squadmate Ladybug's Yo-yo Phone ping -- where she was, for the compass marker. */
public record LadybugPingPayload(double x, double y, double z, int ticks, String name) implements CustomPacketPayload {
	public static final CustomPacketPayload.Type<LadybugPingPayload> TYPE =
			new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(ProjectHeroMod.MOD_ID, "ladybug_ping"));

	public static final StreamCodec<RegistryFriendlyByteBuf, LadybugPingPayload> CODEC = StreamCodec.composite(
			ByteBufCodecs.DOUBLE, LadybugPingPayload::x,
			ByteBufCodecs.DOUBLE, LadybugPingPayload::y,
			ByteBufCodecs.DOUBLE, LadybugPingPayload::z,
			ByteBufCodecs.VAR_INT, LadybugPingPayload::ticks,
			ByteBufCodecs.STRING_UTF8, LadybugPingPayload::name,
			LadybugPingPayload::new);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
