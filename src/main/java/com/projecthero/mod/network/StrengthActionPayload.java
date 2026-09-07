package com.projecthero.mod.network;

import com.projecthero.mod.ProjectHeroMod;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Client -&gt; server gesture for Super Strength that is not one of the six ability slots: the
 * charged punch is wound up by holding the vanilla attack key for two seconds (which vanilla never
 * reports on its own) and thrown when the key is released. The server re-validates (owns the power,
 * off cooldown), so spamming the packet buys nothing.
 */
public record StrengthActionPayload(Action action) implements CustomPacketPayload {
	public enum Action {
		/** Attack key released after being held ~2 s: throw the charged punch now. */
		PERFORM_CHARGED_PUNCH
	}

	public static final CustomPacketPayload.Type<StrengthActionPayload> TYPE =
			new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(ProjectHeroMod.MOD_ID, "strength_action"));

	public static final StreamCodec<RegistryFriendlyByteBuf, StrengthActionPayload> CODEC = StreamCodec.composite(
			ByteBufCodecs.VAR_INT.map(i -> Action.values()[Math.floorMod(i, Action.values().length)], Action::ordinal),
			StrengthActionPayload::action,
			StrengthActionPayload::new);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
