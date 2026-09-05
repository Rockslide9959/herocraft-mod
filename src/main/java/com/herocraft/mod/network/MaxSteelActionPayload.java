package com.herocraft.mod.network;

import com.herocraft.mod.HeroCraftMod;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Client &rarr; server Max Steel gestures that are not one of the six ability slots: the dedicated
 * transform toggle key, and the H-key helmet toggle. Both are edge-triggered and re-validated on the
 * server ({@code MaxSteel.isTransformed}, power ownership), so spamming them buys a modified client
 * nothing.
 */
public record MaxSteelActionPayload(Action action) implements CustomPacketPayload {
	public enum Action {
		/** Dedicated key (default N): Go Turbo. v0.6.17 -- no longer powers down (that is Shift+H). */
		TRANSFORM_TOGGLE,
		/** H key while transformed (no shift): retract / seal the helmet. */
		TOGGLE_HELMET,
		/** Shift + H while transformed: power down (v0.6.17). Refused in combat. */
		POWER_DOWN
	}

	public static final CustomPacketPayload.Type<MaxSteelActionPayload> TYPE =
			new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(HeroCraftMod.MOD_ID, "max_steel_action"));

	public static final StreamCodec<RegistryFriendlyByteBuf, MaxSteelActionPayload> CODEC = StreamCodec.composite(
			ByteBufCodecs.VAR_INT.map(i -> Action.values()[Math.floorMod(i, Action.values().length)], Action::ordinal),
			MaxSteelActionPayload::action,
			MaxSteelActionPayload::new);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
