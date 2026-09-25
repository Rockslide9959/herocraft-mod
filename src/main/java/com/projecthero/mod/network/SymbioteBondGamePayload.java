package com.projecthero.mod.network;

import com.projecthero.mod.ProjectHeroMod;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Server &rarr; client (v0.12.25): open the Symbiote bonding minigame. The seed makes the whole game
 * deterministic (see {@code SymbioteBondGame}), so the server can replay the client's answer.
 */
public record SymbioteBondGamePayload(int seed) implements CustomPacketPayload {
	public static final CustomPacketPayload.Type<SymbioteBondGamePayload> TYPE = new CustomPacketPayload.Type<>(
			ResourceLocation.fromNamespaceAndPath(ProjectHeroMod.MOD_ID, "symbiote_bond_game"));

	public static final StreamCodec<RegistryFriendlyByteBuf, SymbioteBondGamePayload> CODEC = StreamCodec.composite(
			ByteBufCodecs.INT, SymbioteBondGamePayload::seed,
			SymbioteBondGamePayload::new);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
