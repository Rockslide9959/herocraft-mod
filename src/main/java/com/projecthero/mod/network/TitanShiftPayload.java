package com.projecthero.mod.network;

import com.projecthero.mod.ProjectHeroMod;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Client &rarr; server Titan Shifter request that is not one of the six ability slots: the Titan Shift
 * key (H since v0.12.32 -- transform / revert). Only a <em>request</em> -- the server checks the unlock,
 * phase, Titan Energy, cooldown and space before doing anything.
 */
public record TitanShiftPayload(Action action) implements CustomPacketPayload {
	public enum Action {
		TOGGLE_SHIFT
	}

	public static final CustomPacketPayload.Type<TitanShiftPayload> TYPE =
			new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(ProjectHeroMod.MOD_ID, "titan_shift"));

	public static final StreamCodec<RegistryFriendlyByteBuf, TitanShiftPayload> CODEC = StreamCodec.composite(
			ByteBufCodecs.VAR_INT.map(i -> Action.values()[Math.floorMod(i, Action.values().length)], Action::ordinal),
			TitanShiftPayload::action,
			TitanShiftPayload::new);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
