package com.projecthero.mod.network;

import com.projecthero.mod.ProjectHeroMod;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Client &rarr; server: the player chose a firearm from the Arsenal wheel. Re-validated server-side
 * ({@code Punisher.weaponUnlocked}) before anything is equipped.
 */
public record PunisherArsenalPayload(String weaponId) implements CustomPacketPayload {
	public static final CustomPacketPayload.Type<PunisherArsenalPayload> TYPE =
			new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(ProjectHeroMod.MOD_ID, "punisher_arsenal"));

	public static final StreamCodec<RegistryFriendlyByteBuf, PunisherArsenalPayload> CODEC = StreamCodec.composite(
			ByteBufCodecs.STRING_UTF8, PunisherArsenalPayload::weaponId,
			PunisherArsenalPayload::new);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
