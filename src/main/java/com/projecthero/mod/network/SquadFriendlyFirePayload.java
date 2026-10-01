package com.projecthero.mod.network;

import com.projecthero.mod.ProjectHeroMod;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * v0.14.16 Client &rarr; server: the squad screen's (P) friendly-fire button -- "set my squad's friendly fire to
 * {@code on}". Only a request: the server re-checks that the sender leads a squad
 * ({@link com.projecthero.mod.squad.Squads#setFriendlyFire}), exactly as {@code /squad friendlyfire} does.
 */
public record SquadFriendlyFirePayload(boolean on) implements CustomPacketPayload {
	public static final CustomPacketPayload.Type<SquadFriendlyFirePayload> TYPE =
			new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(ProjectHeroMod.MOD_ID, "squad_friendly_fire"));

	public static final StreamCodec<RegistryFriendlyByteBuf, SquadFriendlyFirePayload> CODEC =
			StreamCodec.composite(ByteBufCodecs.BOOL, SquadFriendlyFirePayload::on, SquadFriendlyFirePayload::new);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
