package com.projecthero.mod.network;

import com.projecthero.mod.ProjectHeroMod;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Client &rarr; server: the player picked {@code suitId} in the Blank Blueprint picker ("changes 21").
 * The server re-checks that the player holds a Blank Blueprint and that the mark is actually unlocked
 * before stamping the blueprint.
 */
public record IronManBlueprintChoicePayload(String suitId) implements CustomPacketPayload {
	public static final CustomPacketPayload.Type<IronManBlueprintChoicePayload> TYPE =
			new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(ProjectHeroMod.MOD_ID, "iron_man_blueprint_choice"));

	public static final StreamCodec<RegistryFriendlyByteBuf, IronManBlueprintChoicePayload> CODEC = StreamCodec.composite(
			ByteBufCodecs.STRING_UTF8, IronManBlueprintChoicePayload::suitId,
			IronManBlueprintChoicePayload::new);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
