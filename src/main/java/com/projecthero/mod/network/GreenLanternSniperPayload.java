package com.projecthero.mod.network;

import com.projecthero.mod.ProjectHeroMod;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * v0.16.1: client -> server input for the Green Lantern Sniper Rifle construct while it is out -- attack pressed /
 * released (charge / fire) and right-click held / let go (scope). The server re-checks that the rifle is out, the
 * cooldown and the Ring Charge in {@code GreenLanternSniper}.
 */
public record GreenLanternSniperPayload(Action action) implements CustomPacketPayload {
	public static final CustomPacketPayload.Type<GreenLanternSniperPayload> TYPE =
			new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(ProjectHeroMod.MOD_ID, "green_lantern_sniper"));

	public static final StreamCodec<RegistryFriendlyByteBuf, GreenLanternSniperPayload> CODEC = StreamCodec.composite(
			StreamCodec.of(
					(buf, action) -> buf.writeEnum(action),
					buf -> buf.readEnum(Action.class)),
			GreenLanternSniperPayload::action,
			GreenLanternSniperPayload::new);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}

	public enum Action {
		CHARGE_START,
		FIRE,
		SCOPE_ON,
		SCOPE_OFF
	}
}
