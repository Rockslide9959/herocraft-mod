package com.projecthero.mod.network;

import com.projecthero.mod.ProjectHeroMod;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Server &rarr; client: ease the camera FOV in slightly for a moment -- a "something big is about to
 * happen" cue, first used by the Oathbreaker's summon buildup. Purely cosmetic, same shape as
 * {@link TitanShakePayload}: a modified client ignoring it gains nothing.
 */
public record WorldEventZoomPayload(float amount, int ticks) implements CustomPacketPayload {
	public static final CustomPacketPayload.Type<WorldEventZoomPayload> TYPE =
			new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(ProjectHeroMod.MOD_ID, "world_event_zoom"));

	public static final StreamCodec<RegistryFriendlyByteBuf, WorldEventZoomPayload> CODEC = StreamCodec.composite(
			ByteBufCodecs.FLOAT, WorldEventZoomPayload::amount,
			ByteBufCodecs.VAR_INT, WorldEventZoomPayload::ticks,
			WorldEventZoomPayload::new);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
