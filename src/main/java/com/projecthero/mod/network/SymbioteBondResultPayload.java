package com.projecthero.mod.network;

import java.util.List;

import com.projecthero.mod.ProjectHeroMod;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Client &rarr; server (v0.12.25): the ticks (since the minigame opened) at which the player pressed, one per
 * round played; empty = backed out. The server replays them against its own seed and re-checks the timing --
 * a claimed win is never trusted (and an unprompted packet is ignored).
 */
public record SymbioteBondResultPayload(List<Integer> presses) implements CustomPacketPayload {
	public static final CustomPacketPayload.Type<SymbioteBondResultPayload> TYPE = new CustomPacketPayload.Type<>(
			ResourceLocation.fromNamespaceAndPath(ProjectHeroMod.MOD_ID, "symbiote_bond_result"));

	public static final StreamCodec<RegistryFriendlyByteBuf, SymbioteBondResultPayload> CODEC = StreamCodec.composite(
			ByteBufCodecs.VAR_INT.apply(ByteBufCodecs.list(8)), SymbioteBondResultPayload::presses,
			SymbioteBondResultPayload::new);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
