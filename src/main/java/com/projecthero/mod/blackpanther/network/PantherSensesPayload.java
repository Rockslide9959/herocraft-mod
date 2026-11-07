package com.projecthero.mod.blackpanther.network;

import java.util.List;

import com.projecthero.mod.ProjectHeroMod;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Server to client, sent ONLY to the Black Panther using Panther Senses (v0.17.3): the creatures outlined purple for
 * {@code ticks}. Nothing is set on the entities, so nobody else sees the outlines (the v0.15.4 privacy rule).
 */
public record PantherSensesPayload(List<Integer> ids, int ticks) implements CustomPacketPayload {
	public static final CustomPacketPayload.Type<PantherSensesPayload> TYPE =
			new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(ProjectHeroMod.MOD_ID, "panther_senses"));

	public static final StreamCodec<RegistryFriendlyByteBuf, PantherSensesPayload> CODEC = StreamCodec.composite(
			ByteBufCodecs.VAR_INT.apply(ByteBufCodecs.list(512)), PantherSensesPayload::ids,
			ByteBufCodecs.VAR_INT, PantherSensesPayload::ticks,
			PantherSensesPayload::new);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
