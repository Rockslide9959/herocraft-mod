package com.projecthero.mod.network;

import com.projecthero.mod.ProjectHeroMod;

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
		/** v0.14.2: the H key -- Go Turbo when unsuited, power down when suited (refused in combat). */
		TRANSFORM_TOGGLE,
		/** Shift + H while transformed (v0.14.2; was plain H): retract / seal the helmet. */
		TOGGLE_HELMET,
		/** Kept for the wire format (ordinal 2); power down now goes through {@link #TRANSFORM_TOGGLE}. */
		POWER_DOWN
	}

	public static final CustomPacketPayload.Type<MaxSteelActionPayload> TYPE =
			new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(ProjectHeroMod.MOD_ID, "max_steel_action"));

	public static final StreamCodec<RegistryFriendlyByteBuf, MaxSteelActionPayload> CODEC = StreamCodec.composite(
			ByteBufCodecs.VAR_INT.map(i -> Action.values()[Math.floorMod(i, Action.values().length)], Action::ordinal),
			MaxSteelActionPayload::action,
			MaxSteelActionPayload::new);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
