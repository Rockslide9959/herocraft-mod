package com.projecthero.mod.miraculous.pegasus.network;

import com.projecthero.mod.ProjectHeroMod;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Client to server (v0.16.6): the Voyage destination picked from the menu (its index; -1 = closed without picking). */
public record PegasusVoyageChoicePayload(int index) implements CustomPacketPayload {
	public static final CustomPacketPayload.Type<PegasusVoyageChoicePayload> TYPE =
			new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(ProjectHeroMod.MOD_ID, "pegasus_voyage_choice"));

	public static final StreamCodec<RegistryFriendlyByteBuf, PegasusVoyageChoicePayload> CODEC = StreamCodec.composite(
			ByteBufCodecs.VAR_INT, PegasusVoyageChoicePayload::index,
			PegasusVoyageChoicePayload::new);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
