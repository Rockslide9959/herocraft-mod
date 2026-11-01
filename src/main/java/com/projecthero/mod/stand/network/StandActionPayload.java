package com.projecthero.mod.stand.network;

import com.projecthero.mod.ProjectHeroMod;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * v0.17.2 client -&gt; server: H (summon / dismiss the Stand) and the Stand Menu's choices -- the pose N strikes
 * ({@code value} 1..4) and the settings ({@code value} bit 1 = ORA text, bit 2 = rush auto-face).
 */
public record StandActionPayload(Action action, int value) implements CustomPacketPayload {
	public enum Action {
		TOGGLE_SUMMON, SET_POSE, SET_SETTINGS
	}

	public static final CustomPacketPayload.Type<StandActionPayload> TYPE = new CustomPacketPayload.Type<>(ProjectHeroMod.id("stand_action"));

	public static final StreamCodec<RegistryFriendlyByteBuf, StandActionPayload> CODEC = StreamCodec.composite(
			ByteBufCodecs.VAR_INT.map(i -> Action.values()[Math.max(0, Math.min(Action.values().length - 1, i))], Action::ordinal),
			StandActionPayload::action,
			ByteBufCodecs.VAR_INT, StandActionPayload::value,
			StandActionPayload::new);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
