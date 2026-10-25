package com.projecthero.mod.falcon.network;

import com.projecthero.mod.ProjectHeroMod;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Client to server Falcon flight requests that are not ability slots (v0.17.1): the jump double-tap (take off), Space while
 * gliding (a wing flap), and Sprint held / let go while gliding (the jet boost, which burns Thrust on the server).
 */
public record FalconActionPayload(Action action) implements CustomPacketPayload {
	public enum Action {
		TAKE_OFF,
		FLAP,
		BOOST_ON,
		BOOST_OFF
	}

	public static final CustomPacketPayload.Type<FalconActionPayload> TYPE =
			new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(ProjectHeroMod.MOD_ID, "falcon_action"));

	public static final StreamCodec<RegistryFriendlyByteBuf, FalconActionPayload> CODEC = StreamCodec.composite(
			ByteBufCodecs.VAR_INT.map(i -> Action.values()[Math.floorMod(i, Action.values().length)], Action::ordinal),
			FalconActionPayload::action,
			FalconActionPayload::new);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
