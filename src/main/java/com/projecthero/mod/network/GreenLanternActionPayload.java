package com.projecthero.mod.network;

import com.projecthero.mod.ProjectHeroMod;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Client -> server "I double-tapped the jump key" packet for Ring Flight (v0.11.5), mirroring
 * {@code ThorActionPayload}/{@code IronManActionPayload}'s own double-tap-jump gesture. The server
 * re-validates power/context/energy in {@code GreenLanternAbilityManager#toggleFlight}.
 */
public record GreenLanternActionPayload(Action action) implements CustomPacketPayload {
	public static final CustomPacketPayload.Type<GreenLanternActionPayload> TYPE =
			new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(ProjectHeroMod.MOD_ID, "green_lantern_action"));

	public static final StreamCodec<RegistryFriendlyByteBuf, GreenLanternActionPayload> CODEC = StreamCodec.composite(
			StreamCodec.of(
					(buf, action) -> buf.writeEnum(action),
					buf -> buf.readEnum(Action.class)),
			GreenLanternActionPayload::action,
			GreenLanternActionPayload::new);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}

	public enum Action {
		TOGGLE_FLIGHT
	}
}
