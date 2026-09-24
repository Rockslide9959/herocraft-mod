package com.projecthero.mod.network;

import com.projecthero.mod.ProjectHeroMod;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Client &rarr; server Wolverine gestures that are not one of the six ability slots: the H-key claw
 * deploy / retract. Edge-triggered and re-validated on the server (power ownership + a short spam
 * guard), so a modified client can do nothing with it but toggle its own claws.
 */
public record WolverineActionPayload(Action action) implements CustomPacketPayload {
	public enum Action {
		TOGGLE_CLAWS,
		SNIFF,
		CLAW_STRIKE
	}

	public static final CustomPacketPayload.Type<WolverineActionPayload> TYPE =
			new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(ProjectHeroMod.MOD_ID, "wolverine_action"));

	public static final StreamCodec<RegistryFriendlyByteBuf, WolverineActionPayload> CODEC = StreamCodec.composite(
			ByteBufCodecs.VAR_INT.map(i -> Action.values()[Math.floorMod(i, Action.values().length)], Action::ordinal),
			WolverineActionPayload::action,
			WolverineActionPayload::new);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
