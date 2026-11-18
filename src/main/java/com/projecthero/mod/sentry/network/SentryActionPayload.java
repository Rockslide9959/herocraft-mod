package com.projecthero.mod.sentry.network;

import com.projecthero.mod.ProjectHeroMod;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Client to server (v0.17.5 Sentry rework): {@link #TOGGLE_FLIGHT} -- double-tap Space took off / landed; {@link #TRIAL_RESULT} -- the
 * Let It Out trial ("Hold the Light") is over, {@code won} whether the Light bar filled. The server re-checks everything
 * ({@code SentryFlight.toggle}, {@code LetItOut.report}).
 */
public record SentryActionPayload(int action, boolean won) implements CustomPacketPayload {
	public static final int TOGGLE_FLIGHT = 0;
	public static final int TRIAL_RESULT = 1;
	/** Round 3: H -- Let It Out as the Sentry, back to the Sentry merged ({@code SentryAbilityManager.pressH}). */
	public static final int LET_IT_OUT = 2;

	public static final CustomPacketPayload.Type<SentryActionPayload> TYPE = new CustomPacketPayload.Type<>(ProjectHeroMod.id("sentry_action"));

	public static final StreamCodec<RegistryFriendlyByteBuf, SentryActionPayload> CODEC = StreamCodec.of((buf, p) -> {
		buf.writeVarInt(p.action);
		buf.writeBoolean(p.won);
	}, buf -> new SentryActionPayload(buf.readVarInt(), buf.readBoolean()));

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
