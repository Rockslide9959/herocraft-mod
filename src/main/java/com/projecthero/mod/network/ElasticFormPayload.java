package com.projecthero.mod.network;

import com.projecthero.mod.ProjectHeroMod;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Client &rarr; server: the body shape picked from the Elasticity form wheel.
 * {@link #form} indexes {@link com.projecthero.mod.hero.power.p17.ElasticityHandlers.Form}.
 * Re-validated server-side (owns Elasticity).
 */
public record ElasticFormPayload(int form) implements CustomPacketPayload {
	public static final CustomPacketPayload.Type<ElasticFormPayload> TYPE =
			new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(ProjectHeroMod.MOD_ID, "elastic_form"));

	public static final StreamCodec<RegistryFriendlyByteBuf, ElasticFormPayload> CODEC = StreamCodec.composite(
			ByteBufCodecs.VAR_INT, ElasticFormPayload::form,
			ElasticFormPayload::new);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
