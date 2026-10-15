package com.projecthero.mod.network;

import com.projecthero.mod.ProjectHeroMod;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Client -&gt; server "I pressed or released one of the six universal ability slots" packet. The
 * server ({@link com.projecthero.mod.hero.AbilityRouter}) re-validates everything: which power owns the
 * slot right now, ownership, cooldown, resources, world state.
 *
 * <p>{@code slot} is 1..6. {@code pressed} is the edge: {@code true} on key-down (activate / start a
 * hold or toggle), {@code false} on key-up (end a hold). Tap abilities ignore the release edge;
 * hold/channel abilities (Thor's Lightning Beam, a flamethrower, ...) use both.
 *
 * <p>v0.16.4: {@code shift} is the client's Sneak key at the press. Shift and the ability key pressed together could reach
 * the server key-first, before its sneak state caught up -- a Punisher's Shift+Z Warzone came out as a plain Z grenade.
 */
public record AbilityInputPayload(int slot, boolean pressed, boolean shift) implements CustomPacketPayload {
	public AbilityInputPayload(int slot, boolean pressed) {
		this(slot, pressed, false);
	}

	public static final CustomPacketPayload.Type<AbilityInputPayload> TYPE =
			new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(ProjectHeroMod.MOD_ID, "ability_input"));

	public static final StreamCodec<RegistryFriendlyByteBuf, AbilityInputPayload> CODEC = StreamCodec.composite(
			ByteBufCodecs.VAR_INT, AbilityInputPayload::slot,
			ByteBufCodecs.BOOL, AbilityInputPayload::pressed,
			ByteBufCodecs.BOOL, AbilityInputPayload::shift,
			AbilityInputPayload::new);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
