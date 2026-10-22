package com.projecthero.mod.network;

import com.projecthero.mod.ProjectHeroMod;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Server &rarr; client (v0.16.7, Titan revamp): a Titan Serum was drunk -- open the Titan picker. {@code current} is the
 * ordinal of the shifter's Titan for a re-forge, or -1 for a new shifter. The answer comes back as a
 * {@link TitanShiftPayload} PICK_* action; nothing is granted (and the serum is not used up) until then.
 */
public record TitanPickerPayload(int current) implements CustomPacketPayload {
	public static final CustomPacketPayload.Type<TitanPickerPayload> TYPE =
			new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(ProjectHeroMod.MOD_ID, "titan_picker"));

	public static final StreamCodec<RegistryFriendlyByteBuf, TitanPickerPayload> CODEC = StreamCodec.composite(
			ByteBufCodecs.VAR_INT.map(i -> i - 1, i -> i + 1), TitanPickerPayload::current,
			TitanPickerPayload::new);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
