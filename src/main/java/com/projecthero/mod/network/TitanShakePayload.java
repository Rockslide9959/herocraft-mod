package com.projecthero.mod.network;

import com.projecthero.mod.ProjectHeroMod;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Server &rarr; client: a Titan footfall / impact tremor. Purely cosmetic (a camera shake of the given
 * strength for the given ticks) -- it never affects gameplay, so a modified client ignoring it gains nothing.
 */
public record TitanShakePayload(float intensity, int ticks) implements CustomPacketPayload {
	public static final CustomPacketPayload.Type<TitanShakePayload> TYPE =
			new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(ProjectHeroMod.MOD_ID, "titan_shake"));

	public static final StreamCodec<RegistryFriendlyByteBuf, TitanShakePayload> CODEC = StreamCodec.composite(
			ByteBufCodecs.FLOAT, TitanShakePayload::intensity,
			ByteBufCodecs.VAR_INT, TitanShakePayload::ticks,
			TitanShakePayload::new);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
