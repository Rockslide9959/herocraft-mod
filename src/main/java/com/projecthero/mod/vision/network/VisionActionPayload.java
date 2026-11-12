package com.projecthero.mod.vision.network;

import com.projecthero.mod.ProjectHeroMod;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Client to server (v0.17.3 Vision): the double-tap-Space flight toggle (the server re-checks everything). */
public record VisionActionPayload(int action) implements CustomPacketPayload {
	public static final int TOGGLE_FLIGHT = 0;

	public static final CustomPacketPayload.Type<VisionActionPayload> TYPE = new CustomPacketPayload.Type<>(ProjectHeroMod.id("vision_action"));
	public static final StreamCodec<RegistryFriendlyByteBuf, VisionActionPayload> CODEC = StreamCodec.composite(
			ByteBufCodecs.VAR_INT, VisionActionPayload::action, VisionActionPayload::new);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
