package com.herocraft.mod.network;

import com.herocraft.mod.HeroCraftMod;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Client -&gt; server "I pressed or released one of the six universal ability slots" packet. The
 * server ({@link com.herocraft.mod.hero.AbilityRouter}) re-validates everything: which power owns the
 * slot right now, ownership, cooldown, resources, world state.
 *
 * <p>{@code slot} is 1..6. {@code pressed} is the edge: {@code true} on key-down (activate / start a
 * hold or toggle), {@code false} on key-up (end a hold). Tap abilities ignore the release edge;
 * hold/channel abilities (Thor's Lightning Beam, a flamethrower, ...) use both.
 */
public record AbilityInputPayload(int slot, boolean pressed) implements CustomPacketPayload {
	public static final CustomPacketPayload.Type<AbilityInputPayload> TYPE =
			new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(HeroCraftMod.MOD_ID, "ability_input"));

	public static final StreamCodec<RegistryFriendlyByteBuf, AbilityInputPayload> CODEC = StreamCodec.composite(
			ByteBufCodecs.VAR_INT, AbilityInputPayload::slot,
			ByteBufCodecs.BOOL, AbilityInputPayload::pressed,
			AbilityInputPayload::new);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
