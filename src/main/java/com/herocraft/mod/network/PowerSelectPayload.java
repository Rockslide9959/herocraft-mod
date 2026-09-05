package com.herocraft.mod.network;

import com.herocraft.mod.HeroCraftMod;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Client -&gt; server request to change which owned experimental power occupies the six ability slots.
 * The server validates that the player actually owns {@code power} (or that it is empty, meaning
 * "no experimental power active"). Never uses any of R/G/X/Z/V/C -- driven by the dedicated
 * power-selection key (H) / wheel.
 *
 * <p>An empty string means "deactivate experimental powers" (slots then fall through to Thor context
 * or do nothing).
 */
public record PowerSelectPayload(String power) implements CustomPacketPayload {
	public static final CustomPacketPayload.Type<PowerSelectPayload> TYPE =
			new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(HeroCraftMod.MOD_ID, "power_select"));

	public static final StreamCodec<RegistryFriendlyByteBuf, PowerSelectPayload> CODEC = StreamCodec.composite(
			ByteBufCodecs.STRING_UTF8, PowerSelectPayload::power,
			PowerSelectPayload::new);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
