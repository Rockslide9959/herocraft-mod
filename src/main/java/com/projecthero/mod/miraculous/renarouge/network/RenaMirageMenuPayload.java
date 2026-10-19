package com.projecthero.mod.miraculous.renarouge.network;

import com.projecthero.mod.ProjectHeroMod;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Server to client (v0.16.7): Shift+Z was accepted -- open the Mirage wheel ({@code seconds} = how long the offer stays good). */
public record RenaMirageMenuPayload(int seconds) implements CustomPacketPayload {
	public static final CustomPacketPayload.Type<RenaMirageMenuPayload> TYPE =
			new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(ProjectHeroMod.MOD_ID, "rena_mirage_menu"));

	public static final StreamCodec<RegistryFriendlyByteBuf, RenaMirageMenuPayload> CODEC = StreamCodec.composite(
			ByteBufCodecs.VAR_INT, RenaMirageMenuPayload::seconds,
			RenaMirageMenuPayload::new);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
