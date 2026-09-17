package com.projecthero.mod.network;

import com.projecthero.mod.ProjectHeroMod;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Client &rarr; server: the player picked a construct from the Green Lantern construct wheel
 * (hold C &ge; {@code GreenLanternConfig.CONSTRUCT_WHEEL_HOLD_TICKS}). {@code ordinal} is a
 * {@code ConstructType} ordinal, re-validated server-side ({@code ConstructType#unlockedFor}) before
 * the selection is applied -- exactly like {@code PunisherArsenalPayload} re-validates its weapon id.
 */
public record GreenLanternConstructSelectPayload(int ordinal) implements CustomPacketPayload {
	public static final CustomPacketPayload.Type<GreenLanternConstructSelectPayload> TYPE =
			new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(ProjectHeroMod.MOD_ID, "green_lantern_construct_select"));

	public static final StreamCodec<RegistryFriendlyByteBuf, GreenLanternConstructSelectPayload> CODEC = StreamCodec.composite(
			ByteBufCodecs.VAR_INT, GreenLanternConstructSelectPayload::ordinal,
			GreenLanternConstructSelectPayload::new);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
