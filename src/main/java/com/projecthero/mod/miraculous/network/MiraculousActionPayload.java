package com.projecthero.mod.miraculous.network;

import com.projecthero.mod.ProjectHeroMod;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** v0.16.6: client to server Miraculous requests that are not ability slots: transform / detransform (commands) and N (hide / call out the Kwami). Only a request. */
public record MiraculousActionPayload(Action action) implements CustomPacketPayload {
	public enum Action {
		TOGGLE_FORM,
		/** v0.16.6 (user): N -- hide the Kwami / call it out. */
		TOGGLE_KWAMI_HIDDEN
	}

	public static final CustomPacketPayload.Type<MiraculousActionPayload> TYPE =
			new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(ProjectHeroMod.MOD_ID, "miraculous_action"));

	public static final StreamCodec<RegistryFriendlyByteBuf, MiraculousActionPayload> CODEC = StreamCodec.composite(
			ByteBufCodecs.VAR_INT.map(i -> Action.values()[Math.floorMod(i, Action.values().length)], Action::ordinal),
			MiraculousActionPayload::action,
			MiraculousActionPayload::new);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
