package com.projecthero.mod.network;

import com.projecthero.mod.ProjectHeroMod;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Server &rarr; client: the elastic hero pressed Shift + C -- open the body-shape wheel
 * (Elastic / Inflated / Compression).
 */
public record ElasticFormWheelPayload() implements CustomPacketPayload {
	public static final ElasticFormWheelPayload INSTANCE = new ElasticFormWheelPayload();

	public static final CustomPacketPayload.Type<ElasticFormWheelPayload> TYPE =
			new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(ProjectHeroMod.MOD_ID, "elastic_form_wheel"));

	public static final StreamCodec<RegistryFriendlyByteBuf, ElasticFormWheelPayload> CODEC =
			StreamCodec.unit(INSTANCE);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
