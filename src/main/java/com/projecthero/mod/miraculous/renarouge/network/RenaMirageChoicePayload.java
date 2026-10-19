package com.projecthero.mod.miraculous.renarouge.network;

import com.projecthero.mod.ProjectHeroMod;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Client to server (v0.16.7): the illusion picked on the Mirage wheel (its {@code MirageIllusion} ordinal; -1 = closed without one). */
public record RenaMirageChoicePayload(int index) implements CustomPacketPayload {
	public static final CustomPacketPayload.Type<RenaMirageChoicePayload> TYPE =
			new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(ProjectHeroMod.MOD_ID, "rena_mirage_choice"));

	public static final StreamCodec<RegistryFriendlyByteBuf, RenaMirageChoicePayload> CODEC = StreamCodec.composite(
			ByteBufCodecs.VAR_INT, RenaMirageChoicePayload::index,
			RenaMirageChoicePayload::new);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
